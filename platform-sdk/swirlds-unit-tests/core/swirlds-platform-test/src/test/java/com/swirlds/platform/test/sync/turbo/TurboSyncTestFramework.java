/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync.turbo;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.platform.test.sync.ConnectionFactory.createSocketConnections;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.base.utility.Pair;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.sync.turbo.TurboSyncRunner;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility methods for testing turbo sync.
 */
public final class TurboSyncTestFramework {

    private TurboSyncTestFramework() {}

    /**
     * The result of allowing two nodes to run turbo sync for a short period of time.
     *
     * @param eventsReceivedA the events that peer A received in the order that it received them, if duplicate events
     *                        were received then there will be duplicates in this list
     * @param eventsReceivedB the events that peer B received in the order that it received them, if duplicate events
     *                        were received then there will be duplicates in this list
     */
    public record TestSynchronizationResult(
            @NonNull List<GossipEvent> eventsReceivedA, @NonNull List<GossipEvent> eventsReceivedB) {}

    /**
     * Simulates a turbo sync between two nodes for a period of time.
     *
     * @param eventsA the events peer A knows about at the start of the test
     * @param eventsB the events peer B knows about at the start of the test
     * @return the result of the test
     */
    @NonNull
    public static TestSynchronizationResult simulateSynchronization(
            @NonNull final Random random,
            @NonNull final AddressBook addressBook,
            @NonNull final List<EventImpl> eventsA,
            @NonNull final List<EventImpl> eventsB)
            throws IOException {

        final NodeId nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(0);

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());

        final Pair<Connection, Connection> connections = createSocketConnections(nodeA, nodeB);

        final List<GossipEvent> eventsReceivedA = new ArrayList<>();
        final TurboSyncRunner runnerA = new TurboSyncTestNodeBuilder(
                        startingTime, addressBook, nodeA, nodeB, connections.left())
                .withEventConsumer(eventsReceivedA::add)
                .withKnownEvents(eventsA)
                .build();

        final List<GossipEvent> eventsReceivedB = new ArrayList<>();
        final TurboSyncRunner runnerB = new TurboSyncTestNodeBuilder(
                        startingTime, addressBook, nodeB, nodeA, connections.right())
                .withEventConsumer(eventsReceivedB::add)
                .withKnownEvents(eventsB)
                .build();

        final AtomicBoolean completedA = new AtomicBoolean(false);
        final AtomicBoolean errorA = new AtomicBoolean(false);
        new Thread(() -> {
                    try {
                        runnerA.run();
                    } catch (final IOException | ParallelExecutionException e) {
                        errorA.set(true);
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    completedA.set(true);
                })
                .start();

        final AtomicBoolean completedB = new AtomicBoolean(false);
        final AtomicBoolean errorB = new AtomicBoolean(false);
        new Thread(() -> {
                    try {
                        runnerB.run();
                    } catch (final IOException | ParallelExecutionException e) {
                        e.printStackTrace();
                        errorB.set(true);
                        throw new RuntimeException(e);
                    }
                    completedB.set(true);
                })
                .start();

        assertEventuallyTrue(completedA::get, Duration.ofSeconds(1), "Node A did not finish");
        assertEventuallyTrue(completedB::get, Duration.ofSeconds(1), "Node A did not finish");
        assertFalse(errorA.get(), "Node A had an error");
        assertFalse(errorB.get(), "Node B had an error");

        return new TestSynchronizationResult(eventsReceivedA, eventsReceivedB);
    }
}
