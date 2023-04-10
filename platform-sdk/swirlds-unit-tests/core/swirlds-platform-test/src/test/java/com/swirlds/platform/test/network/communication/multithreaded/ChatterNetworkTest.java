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

package com.swirlds.platform.test.network.communication.multithreaded;

import static com.swirlds.common.threading.manager.internal.AdHocThreadManager.getStaticThreadManager;
import static org.awaitility.Awaitility.await;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.threading.utility.ThrowingRunnable;
import com.swirlds.platform.Connection;
import com.swirlds.platform.chatter.communication.ChatterProtocol;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.test.sync.ConnectionFactory;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the chatter network communication
 */
class ChatterNetworkTest {
    private static final int WAIT_TIME = 5;
    private static final TimeUnit WAIT_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * Creates 2 peers that exchange messages and checks if those messages were exchanged
     */
    @Test
    void transmitMessages()
            throws IOException, ConstructableRegistryException, ExecutionException, InterruptedException,
                    TimeoutException {
        final int numberOfMessages = 10;

        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(SerializableLong.class, SerializableLong::new));
        final ParallelExecutor parallelExecutor =
                new CachedPoolParallelExecutor(getStaticThreadManager(), "ChatterNetworkTest");
        parallelExecutor.start();
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        // Peer 1 sends positive numbers
        final ChatterNetworkTester peer1 = new ChatterNetworkTester(numberOfMessages, parallelExecutor, false);
        // Peer 2 sends negative numbers
        final ChatterNetworkTester peer2 = new ChatterNetworkTester(numberOfMessages, parallelExecutor, true);

        final Pair<Connection, Connection> connections =
                ConnectionFactory.createLocalConnections(NodeId.createMain(0), NodeId.createMain(1));

        final Future<Void> future1 = executor.submit(peer1.getProtocolRunnable(connections.getLeft()));
        final Future<Void> future2 = executor.submit(peer2.getProtocolRunnable(connections.getRight()));

        // wait until all messages are sent and received
        await().atMost(WAIT_TIME, WAIT_TIME_UNIT).until(() -> peer1.isDone() && peer2.isDone());

        // stop the threads gracefully
        peer1.stopChatter();
        peer2.stopChatter();

        // wait until the threads have died
        await().atMost(WAIT_TIME, WAIT_TIME_UNIT).until(future1::isDone);
        await().atMost(WAIT_TIME, WAIT_TIME_UNIT).until(future2::isDone);

        // in case any exception gets thrown by the chatter threads
        future1.get(WAIT_TIME, WAIT_TIME_UNIT);
        future2.get(WAIT_TIME, WAIT_TIME_UNIT);

        peer1.assertMessagesReceived();
        peer2.assertMessagesReceived();
    }

    private static class ChatterNetworkTester {
        final int numberOfMessages;
        final boolean sendNegativeNumbers;
        final ChatterProtocol protocol;
        final Queue<SelfSerializable> peerSend;
        final Queue<SelfSerializable> peerRec;
        final CommunicationState communicationState;

        public ChatterNetworkTester(
                final int numberOfMessages,
                final ParallelExecutor parallelExecutor,
                final boolean sendNegativeNumbers) {
            this.numberOfMessages = numberOfMessages;
            this.sendNegativeNumbers = sendNegativeNumbers;
            peerSend = new ConcurrentLinkedDeque<>();
            IntStream.range(1, numberOfMessages + 1)
                    .map(i -> sendNegativeNumbers ? -i : i)
                    .mapToObj(SerializableLong::new)
                    .forEach(peerSend::add);
            peerRec = new ConcurrentLinkedDeque<>();
            communicationState = new CommunicationState();
            communicationState.chatterSyncStartingPhase3();
            final PeerInstance peerInstance = new PeerInstance(
                    communicationState,
                    new PeerGossipState(100_000),
                    new MessageProvider() {
                        @Override
                        public SelfSerializable getMessage() {
                            return peerSend.poll();
                        }

                        @Override
                        public void clear() {
                            peerSend.clear();
                        }
                    },
                    peerRec::add);
            protocol = new ChatterProtocol(peerInstance, parallelExecutor);
        }

        public ThrowingRunnable getProtocolRunnable(final Connection connection) {
            return () -> protocol.runProtocol(connection);
        }

        public boolean isDone() {
            return peerSend.isEmpty() && peerRec.size() == numberOfMessages;
        }

        public void stopChatter() {
            communicationState.suspend();
        }

        public void assertMessagesReceived() {
            // we expect to receive the opposite of what we sent
            final boolean recNegative = !sendNegativeNumbers;
            long sequence = 0;
            for (final SelfSerializable ss : peerRec) {
                if (ss instanceof SerializableLong sl) {
                    sequence += recNegative ? -1 : 1;
                    Assertions.assertEquals(
                            sequence, sl.getValue(), "the received value does not equal the one expected");
                } else {
                    Assertions.fail("received message should be a SerializableLong");
                }
            }
            Assertions.assertEquals(
                    recNegative ? -numberOfMessages : numberOfMessages,
                    sequence,
                    "the last sequence should equal the number of messages");
        }
    }
}
