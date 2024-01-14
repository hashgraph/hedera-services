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

import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.sync.turbo.TurboSyncTestFramework.TestSynchronizationResult;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TurboSyncTests {

    @Test
    void noEventsTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(8).build();

        final List<EventImpl> eventsA = List.of();
        final List<EventImpl> eventsB = List.of();

        final TestSynchronizationResult result = simulateSynchronization(random, addressBook, eventsA, eventsB);

        assertTrue(result.eventsReceivedA().isEmpty());
        assertTrue(result.eventsReceivedB().isEmpty());
    }
}
