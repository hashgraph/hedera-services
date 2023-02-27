/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.chatter.communication;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.Connection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.PeerMessageHandler;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import java.io.IOException;

/**
 * An instance responsible for serializing and deserializing chatter messages for a single peer
 */
public class ChatterProtocol implements Protocol {
    private final CommunicationState communicationState;
    private final PeerMessageHandler messageHandler;
    private final MessageProvider messageProvider;
    private final ParallelExecutor parallelExecutor;

    public ChatterProtocol(final PeerInstance peerInstance, final ParallelExecutor parallelExecutor) {
        this.communicationState = peerInstance.communicationState();
        this.messageHandler = peerInstance.inputHandler();
        this.messageProvider = peerInstance.outputAggregator();
        this.parallelExecutor = parallelExecutor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        try {
            communicationState.chatterStarted();
            parallelExecutor.doParallel(
                    () -> read(connection), () -> write(connection), () -> readException(connection));
        } catch (final ParallelExecutionException e) {
            if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
                throw new IOException(e);
            }
            throw new NetworkProtocolException(e);
        } finally {
            communicationState.chatterEnded();
            messageProvider.clear();
        }
    }

    private void readException(final Connection connection) {
        // disconnect() can block for a long time in certain situations explained in #5511
        // because of this, we end chatter as soon as reading is done, and we clear the queue
        // this ensures we won't be holding on to old events while disconnect() is blocking
        communicationState.chatterEnded();
        messageProvider.clear();
        connection.disconnect();
    }

    /**
     * Reads {@link SelfSerializable} messages from a stream and passes them on to chatter for handling
     */
    private void read(final Connection connection) throws NetworkProtocolException, IOException {
        while (connection.connected()) {
            final byte b = connection.getDis().readByte();
            switch (b) {
                case Constants.KEEPALIVE -> {
                    // nothing to do
                }
                case Constants.PAYLOAD -> {
                    final SelfSerializable message = connection.getDis().readSerializable();
                    messageHandler.handleMessage(message);
                }
                case Constants.END -> {
                    communicationState.receivedEnd();
                    return;
                }
                default -> throw new NetworkProtocolException(String.format("Unexpected byte received: %02X", b));
            }
        }
    }

    /**
     * Polls chatter for messages and serializes them to the stream
     */
    public void write(final Connection connection) throws InterruptedException, IOException {
        while (connection.connected() && communicationState.shouldChatter()) {
            final SelfSerializable message = messageProvider.getMessage();
            if (message == null) {
                connection.getDos().flush(); // only flush before a sleep
                Thread.sleep(Constants.NO_PAYLOAD_SLEEP_MS);
                connection.getDos().writeByte(Constants.KEEPALIVE);
                continue;
            }
            connection.getDos().writeByte(Constants.PAYLOAD);
            connection.getDos().writeSerializable(message, true);
        }

        connection.getDos().writeByte(Constants.END);
        connection.getDos().flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        return communicationState.shouldChatter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        return communicationState.shouldChatter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return true;
    }
}
