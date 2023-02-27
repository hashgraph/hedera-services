/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import static com.swirlds.common.formatting.StringFormattingUtils.formattedList;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.LearningSynchronizer;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.logging.payloads.ReconnectDataUsagePayload;
import com.swirlds.platform.Connection;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SigSet;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.state.signed.SignedStateValidationData;
import com.swirlds.platform.state.signed.SignedStateValidator;
import java.io.IOException;
import java.net.SocketException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates reconnect logic for the out of date node which is
 * requesting a recent state from another node.
 */
public class ReconnectLearner {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(ReconnectLearner.class);

    private final Connection connection;
    private final AddressBook addressBook;
    private final State currentState;
    private final int reconnectSocketTimeout;
    private final ReconnectMetrics statistics;
    private SignedState newSignedState;
    private final SignedStateValidationData stateValidationData;
    private SigSet sigSet;
    /**
     * After reconnect is finished, restore the socket timeout to the original value.
     */
    private int originalSocketTimeout;

    private final ThreadManager threadManager;

    /**
     * @param threadManager
     * 		responsible for managing thread lifecycles
     * @param connection
     * 		the connection to use for the reconnect
     * @param addressBook
     * 		the current address book
     * @param currentState
     * 		the most recent state from the learner
     * @param reconnectSocketTimeout
     * 		the amount of time that should be used for the socket timeout
     * @param statistics
     * 		reconnect metrics
     */
    public ReconnectLearner(
            final ThreadManager threadManager,
            final Connection connection,
            final AddressBook addressBook,
            final State currentState,
            final int reconnectSocketTimeout,
            final ReconnectMetrics statistics) {

        currentState.throwIfImmutable("Can not perform reconnect with immutable state");
        currentState.throwIfDestroyed("Can not perform reconnect with destroyed state");

        this.threadManager = threadManager;
        this.connection = connection;
        this.addressBook = addressBook;
        this.currentState = currentState;
        this.reconnectSocketTimeout = reconnectSocketTimeout;
        this.statistics = statistics;

        // Save some of the current state data for validation
        this.stateValidationData =
                new SignedStateValidationData(currentState.getPlatformState().getPlatformData(), addressBook);
    }

    /**
     * @throws ReconnectException
     * 		thrown when there is an error in the underlying protocol
     */
    private void increaseSocketTimeout() throws ReconnectException {
        try {
            originalSocketTimeout = connection.getTimeout();
            connection.setTimeout(reconnectSocketTimeout);
        } catch (final SocketException e) {
            throw new ReconnectException(e);
        }
    }

    /**
     * @throws ReconnectException
     * 		thrown when there is an error in the underlying protocol
     */
    private void resetSocketTimeout() throws ReconnectException {
        if (!connection.connected()) {
            logger.debug(
                    RECONNECT.getMarker(),
                    "{} connection to {} is no longer connected. Returning.",
                    connection.getSelfId(),
                    connection.getOtherId());
            return;
        }

        try {
            connection.setTimeout(originalSocketTimeout);
        } catch (final SocketException e) {
            throw new ReconnectException(e);
        }
    }

    /**
     * Perform the reconnect operation.
     *
     * @throws ReconnectException
     * 		thrown if I/O related errors occur, when there is an error in the underlying protocol, or the received
     * 		state is invalid
     */
    public void execute(final SignedStateValidator validator) throws ReconnectException {
        increaseSocketTimeout();
        try {
            receiveSignatures();
            reconnect();
            validator.validate(newSignedState, addressBook, stateValidationData);
        } catch (final IOException | SignedStateInvalidException e) {
            throw new ReconnectException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            resetSocketTimeout();
        }
    }

    /**
     * Get a copy of the state from the other node.
     *
     * @throws InterruptedException
     * 		if the current thread is interrupted
     */
    private void reconnect() throws InterruptedException {
        statistics.incrementReceiverStartTimes();

        final MerkleDataInputStream in = new MerkleDataInputStream(connection.getDis());
        final MerkleDataOutputStream out = new MerkleDataOutputStream(connection.getDos());

        connection.getDis().getSyncByteCounter().resetCount();
        connection.getDos().getSyncByteCounter().resetCount();

        final LearningSynchronizer synchronizer =
                new LearningSynchronizer(threadManager, in, out, currentState, connection::disconnect);
        synchronizer.synchronize();

        final State state = (State) synchronizer.getRoot();
        newSignedState = new SignedState(state);
        newSignedState.setSigSet(sigSet);

        final double mbReceived = connection.getDis().getSyncByteCounter().getMebiBytes();
        logger.info(
                RECONNECT.getMarker(),
                () -> new ReconnectDataUsagePayload("Reconnect data usage report", mbReceived).toString());

        statistics.incrementReceiverEndTimes();
    }

    /**
     * Copy the signatures for the state from the other node.
     *
     * @throws IOException
     * 		if any I/O related errors occur
     */
    private void receiveSignatures() throws IOException {
        logger.info(RECONNECT.getMarker(), "Receiving signed state signatures");
        sigSet = connection.getDis().readSerializable();

        final StringBuilder sb = new StringBuilder();
        sb.append("Received signatures from nodes ");
        formattedList(sb, sigSet.iterator());
        logger.info(RECONNECT.getMarker(), sb);
    }

    /**
     * Get the signed state that was copied from the other node.
     */
    public SignedState getSignedState() {
        return newSignedState;
    }
}
