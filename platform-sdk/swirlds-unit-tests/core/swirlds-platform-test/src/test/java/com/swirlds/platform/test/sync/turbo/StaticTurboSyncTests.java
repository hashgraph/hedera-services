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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.generateEvents;
import static com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.nodeHasAllEvents;
import static com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.simulateSynchronization;
import static com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.wereDuplicateEventsSent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.gossip.shadowgraph.SyncUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.TestSynchronizationResult;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * This class contains tests with static hashgraphs. At the start of each test, each node is given a set of events, and
 * new events are not introduced into the system during the test. In this scenario, we expect to see zero duplicate
 * events, and both nodes should end up with a complete set of events.
 */
class StaticTurboSyncTests {

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");
    }

    /**
     * Neither node has any events.
     */
    @Test
    void noEventsTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(8).build();

        final List<EventImpl> initialEventsA = List.of();
        final List<EventImpl> initialEventsB = List.of();

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());

        final TestSynchronizationResult result =
                simulateSynchronization(random, addressBook, initialEventsA, initialEventsB, startingTime);

        assertTrue(result.eventsReceivedA().isEmpty());
        assertTrue(result.eventsReceivedB().isEmpty());
    }

    /**
     * One node has many events, the other has zero events.
     */
    @Test
    void oneNodeHasAllTheEvents() throws IOException {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(8).build();

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());

        final List<EventImpl> initialEventsA = generateEvents(random, addressBook, startingTime, 100);
        final List<EventImpl> initialEventsB = List.of();

        final TestSynchronizationResult result =
                simulateSynchronization(random, addressBook, initialEventsA, initialEventsB, startingTime);

        assertTrue(result.eventsReceivedA().isEmpty());

        assertFalse(wereDuplicateEventsSent(initialEventsB, result.eventsReceivedB()));
        assertTrue(nodeHasAllEvents(initialEventsB, result.eventsReceivedB(), initialEventsA));
    }

    /**
     * One node has many events, the other has some events but not all of them.
     */
    @Test
    void oneNodeHasManyMoreEventsThanPeer() throws IOException {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(8).build();

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());

        final List<EventImpl> initialEventsA = generateEvents(random, addressBook, startingTime, 100);

        final List<EventImpl> initialEventsB = new ArrayList<>(50);
        for (int i = 0; i < 50; i++) {
            initialEventsB.add(initialEventsA.get(i));
        }

        final TestSynchronizationResult result =
                simulateSynchronization(random, addressBook, initialEventsA, initialEventsB, startingTime);

        assertTrue(result.eventsReceivedA().isEmpty());

        assertFalse(wereDuplicateEventsSent(initialEventsB, result.eventsReceivedB()));
        assertTrue(nodeHasAllEvents(initialEventsB, result.eventsReceivedB(), initialEventsA));
    }

    /**
     * Both nodes have a lot of events, and they both have the same events.
     */
    @Test
    void bothNodesHaveTheSameEvents() throws IOException {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(8).build();

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());

        final List<EventImpl> initialEventsA = generateEvents(random, addressBook, startingTime, 100);
        final List<EventImpl> initialEventsB = new ArrayList<>(initialEventsA);

        final TestSynchronizationResult result =
                simulateSynchronization(random, addressBook, initialEventsA, initialEventsB, startingTime);

        assertTrue(result.eventsReceivedA().isEmpty());
        assertTrue(result.eventsReceivedB().isEmpty());
    }

    /**
     * Create a situation where each node has events needed by their peer.
     */
    @Test
    void nodesEachHaveEventsTheOtherNeeds() throws IOException {
        final Random random = getRandomPrintSeed(5650957212556381039L); // TODO

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(8).build();

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());
        final List<EventImpl> allEvents = generateEvents(random, addressBook, startingTime, 100);

        // "borrow" this handy method to find the tips of the hashgraph.
        // We want to set up each peer so that they don't have the same events,
        // but we don't want to leave one peer without a topologically sound
        // hashgraph. Use the tips to decide which events are safe to remove
        // from the original knowledge of each peer.
        final List<EventImpl> tips = SyncUtils.computeSentTips(List.of(), allEvents, 0);

        final Set<Hash> tipHashes = new HashSet<>();
        for (final EventImpl tip : tips) {
            tipHashes.add(tip.getBaseHash());
        }

        final List<EventImpl> initialEventsA = new ArrayList<>(allEvents.size());
        final List<EventImpl> initialEventsB = new ArrayList<>(allEvents.size());
        for (final EventImpl event : allEvents) {
            if (!tipHashes.contains(event.getBaseHash())) {
                initialEventsA.add(event);
                initialEventsB.add(event);
            }
        }

        for (int i = 0; i < tips.size(); i++) {
            if (i % 2 == 0) {
                initialEventsA.add(tips.get(i));
            } else {
                initialEventsB.add(tips.get(i));
            }
        }

        System.out.println("tip count: " + tips.size()); // TODO

        final TestSynchronizationResult result =
                simulateSynchronization(random, addressBook, initialEventsA, initialEventsB, startingTime);

        System.out.println("A received: " + result.eventsReceivedA().size()); // TODO
        System.out.println("B received: " + result.eventsReceivedB().size()); // TODO

        assertFalse(wereDuplicateEventsSent(initialEventsA, result.eventsReceivedA()));
        assertTrue(nodeHasAllEvents(initialEventsA, result.eventsReceivedA(), allEvents));

        assertFalse(wereDuplicateEventsSent(initialEventsB, result.eventsReceivedB()));
        assertTrue(nodeHasAllEvents(initialEventsB, result.eventsReceivedB(), allEvents));
    }
}
