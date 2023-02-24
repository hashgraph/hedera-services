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

package com.swirlds.platform.test.state;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.platform.metrics.IssMetrics;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssMetrics Tests")
class IssMetricsTests {

    @Test
    @DisplayName("Update Non-Existent Node")
    void updateNonExistentNode() {

        final AddressBook addressBook =
                new RandomAddressBookGenerator().setSize(100).build();

        final IssMetrics issMetrics = new IssMetrics(new NoOpMetrics(), addressBook);

        assertThrows(
                IllegalArgumentException.class,
                () -> issMetrics.stateHashValidityObserver(0L, -1L, randomHash(), randomHash()),
                "should not be able to update stats for non-existent node");
    }

    @Test
    @DisplayName("Update Test")
    void updateTest() {
        final Random random = getRandomPrintSeed();

        final Hash hashA = randomHash(random);
        final Hash hashB = randomHash(random);

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(100).build();

        final IssMetrics issMetrics = new IssMetrics(new NoOpMetrics(), addressBook);

        assertEquals(0, issMetrics.getIssCount(), "there shouldn't be any nodes in an ISS state");
        assertEquals(0, issMetrics.getIssStake(), "there shouldn't be any stake in an ISS state");

        long round = 1;
        int expectedIssCount = 0;
        long expectedIssStake = 0;

        // Change even numbered nodes to have an ISS
        for (final Address address : addressBook) {
            if (address.getId() % 2 == 0) {
                issMetrics.stateHashValidityObserver(round, address.getId(), hashA, hashB);
                expectedIssCount++;
                expectedIssStake += address.getStake();
            } else {
                issMetrics.stateHashValidityObserver(round, address.getId(), hashA, hashA);
            }
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");
        }

        // For the next round, report the same statuses. No change is expected.
        round++;
        for (final Address address : addressBook) {
            final Hash hash = address.getId() % 2 != 0 ? hashA : hashB;
            issMetrics.stateHashValidityObserver(round, address.getId(), hashA, hash);
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");
        }

        // Report data from the same round number. This is expected to be ignored.
        for (final Address address : addressBook) {
            issMetrics.stateHashValidityObserver(round, address.getId(), hashA, hashA);
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");
        }

        // Report data from a lower round number. This is expected to be ignored.
        for (final Address address : addressBook) {
            issMetrics.stateHashValidityObserver(round - 1, address.getId(), hashA, hashA);
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");
        }

        // Switch the status of each node.
        round++;
        for (final Address address : addressBook) {
            if (address.getId() % 2 == 0) {
                issMetrics.stateHashValidityObserver(round, address.getId(), hashA, hashA);
                expectedIssCount--;
                expectedIssStake -= address.getStake();
            } else {
                issMetrics.stateHashValidityObserver(round, address.getId(), hashA, hashB);
                expectedIssCount++;
                expectedIssStake += address.getStake();
            }
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");
        }

        // Report a catastrophic ISS for a round in the past. Should be ignored.
        issMetrics.catastrophicIssObserver(round - 1, null);
        assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
        assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");

        // Report a catastrophic ISS.
        round++;
        issMetrics.catastrophicIssObserver(round, null);
        expectedIssCount = addressBook.getSize();
        expectedIssStake = addressBook.getTotalStake();
        assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
        assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");

        // Heal all nodes.
        round++;
        for (final Address address : addressBook) {
            issMetrics.stateHashValidityObserver(round, address.getId(), hashA, hashA);
            expectedIssCount--;
            expectedIssStake -= address.getStake();
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssStake, issMetrics.getIssStake(), "unexpected ISS stake");
        }

        assertEquals(0, issMetrics.getIssCount(), "unexpected ISS count");
        assertEquals(0, issMetrics.getIssStake(), "unexpected ISS stake");
    }
}
