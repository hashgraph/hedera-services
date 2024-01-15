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
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.sync.turbo.TurboSyncRunner;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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

    // TODO if the arg count gets too high, consider using a builder
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
            @NonNull final List<EventImpl> eventsB,
            @NonNull final Instant startingTime)
            throws IOException {

        final NodeId nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(1);

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

        assertEventuallyTrue(completedA::get, Duration.ofSeconds(10000), "Node A did not finish"); // TODO
        assertEventuallyTrue(completedB::get, Duration.ofSeconds(10000), "Node A did not finish"); // TODO
        assertFalse(errorA.get(), "Node A had an error");
        assertFalse(errorB.get(), "Node B had an error");

        return new TestSynchronizationResult(eventsReceivedA, eventsReceivedB);
    }

    /**
     * Generate a random list of events.
     *
     * @param random       the random number generator
     * @param addressBook  the address book
     * @param count        the number of events to generate
     * @param startingTime the starting time, all events are given a "time received" timestamp equal to this
     * @return the generated events
     */
    public static List<EventImpl> generateEvents(
            @NonNull final Random random,
            @NonNull final AddressBook addressBook,
            @NonNull final Instant startingTime,
            final int count) {

        final List<EventSource<?>> sources = new ArrayList<>();
        for (final Address address : addressBook) {
            sources.add(new StandardEventSource(false)); // .setNodeId(address.getNodeId()));
        }
        final StandardGraphGenerator generator = new StandardGraphGenerator(random.nextLong(), sources, addressBook);

        final List<EventImpl> events = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final EventImpl event = generator.generateEvent();
            event.getBaseEvent().setTimeReceived(startingTime);
            events.add(event);
        }

        return events;
    }

    /**
     * Check if duplicate events were sent.
     *
     * @param originalEvents the events that were sent
     * @param eventsReceived the events that were received
     * @return true if duplicate events were sent
     */
    public static boolean wereDuplicateEventsSent(
            @NonNull final List<EventImpl> originalEvents, @NonNull final List<GossipEvent> eventsReceived) {

        final Set<Hash> hashes = new HashSet<>();
        for (final EventImpl originalEvent : originalEvents) {
            hashes.add(originalEvent.getBaseHash());
        }

        for (final GossipEvent eventReceived : eventsReceived) {
            if (!hashes.add(eventReceived.getHashedData().getHash())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the node has all the events.
     *
     * @param originalEvents the node's original events
     * @param eventsReceived the events that were received
     * @param allEvents      the list of all events
     * @return true if the node has all the events
     */
    public static boolean nodeHasAllEvents(
            @NonNull final List<EventImpl> originalEvents,
            @NonNull final List<GossipEvent> eventsReceived,
            @NonNull final List<EventImpl> allEvents) {

        final Set<Hash> hashes = new HashSet<>();
        for (final EventImpl event : allEvents) {
            hashes.add(event.getBaseHash());
        }

        for (final EventImpl originalEvent : originalEvents) {
            hashes.remove(originalEvent.getBaseHash());
        }

        for (final GossipEvent eventReceived : eventsReceived) {
            hashes.remove(eventReceived.getHashedData().getHash());
        }

        return hashes.isEmpty();
    }
}
