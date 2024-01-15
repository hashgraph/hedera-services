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
import static com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.simulateSynchronization;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.TestSynchronizationResult;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * This class contains tests with static hashgraphs. At the start of each test, each node is given a set of events,
 * and new events are not introduced into the system during the test. In this scenario, we expect to see zero
 * duplicate events, and both nodes should end up with a complete set of events.
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
        final Random random = getRandomPrintSeed(0); // TODO

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(8).build();

        final List<EventImpl> initialEventsA = List.of();
        final List<EventImpl> initialEventsB = List.of();

        final TestSynchronizationResult result =
                simulateSynchronization(random, addressBook, initialEventsA, initialEventsB);

        assertTrue(result.eventsReceivedA().isEmpty());
        assertTrue(result.eventsReceivedB().isEmpty());
    }

    //    @Test
    //    void oneNodeHasAllTheEvents() throws IOException {
    //        final Random random = getRandomPrintSeed();
    //
    //        final AddressBook addressBook =
    //                new RandomAddressBookGenerator(random).setSize(8).build();
    //
    //        final List<EventImpl> initialEventsA = generateEvents(random, addressBook, 100);
    //        final List<EventImpl> initialEventsB = List.of();
    //
    //        final TestSynchronizationResult result = simulateSynchronization(random, addressBook, initialEventsA,
    // initialEventsB);
    //
    //        assertTrue(result.eventsReceivedA().isEmpty());
    //
    //        assertFalse(wereDuplicateEventsSent(initialEventsB, result.eventsReceivedB()));
    //        System.out.println("eventsReceivedB: " + result.eventsReceivedB().size());
    //        assertTrue(nodeHasAllEvents(initialEventsB, result.eventsReceivedB(), initialEventsA));
    //    }
}
