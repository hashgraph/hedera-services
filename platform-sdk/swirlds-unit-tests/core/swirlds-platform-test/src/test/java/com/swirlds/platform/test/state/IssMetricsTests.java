/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.metrics.IssMetrics;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssMetrics Tests")
class IssMetricsTests {

    @Test
    @DisplayName("Update Non-Existent Node")
    void updateNonExistentNode() {
        final Randotron randotron = Randotron.create();

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(100).build();

        final IssMetrics issMetrics = new IssMetrics(new NoOpMetrics(), addressBook);

        assertThrows(
                IllegalArgumentException.class,
                () -> issMetrics.stateHashValidityObserver(
                        0L, NodeId.of(Integer.MAX_VALUE), randomHash(), randomHash()),
                "should not be able to update stats for non-existent node");
    }

    @Test
    @DisplayName("Update Test")
    void updateTest() {
        final Random random = getRandomPrintSeed();

        final Hash hashA = randomHash(random);
        final Hash hashB = randomHash(random);

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(random).withSize(100).build();

        final IssMetrics issMetrics = new IssMetrics(new NoOpMetrics(), addressBook);

        assertEquals(0, issMetrics.getIssCount(), "there shouldn't be any nodes in an ISS state");
        assertEquals(0, issMetrics.getIssWeight(), "there shouldn't be any weight in an ISS state");

        long round = 1;
        int expectedIssCount = 0;
        long expectedIssweight = 0;

        // Change even numbered nodes to have an ISS
        for (final Address address : addressBook) {
            if (address.getNodeId().id() % 2 == 0) {
                issMetrics.stateHashValidityObserver(round, address.getNodeId(), hashA, hashB);
                expectedIssCount++;
                expectedIssweight += address.getWeight();
            } else {
                issMetrics.stateHashValidityObserver(round, address.getNodeId(), hashA, hashA);
            }
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");
        }

        // For the next round, report the same statuses. No change is expected.
        round++;
        for (final Address address : addressBook) {
            final Hash hash = address.getNodeId().id() % 2 != 0 ? hashA : hashB;
            issMetrics.stateHashValidityObserver(round, address.getNodeId(), hashA, hash);
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");
        }

        // Report data from the same round number. This is expected to be ignored.
        for (final Address address : addressBook) {
            issMetrics.stateHashValidityObserver(round, address.getNodeId(), hashA, hashA);
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");
        }

        // Report data from a lower round number. This is expected to be ignored.
        for (final Address address : addressBook) {
            issMetrics.stateHashValidityObserver(round - 1, address.getNodeId(), hashA, hashA);
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");
        }

        // Switch the status of each node.
        round++;
        for (final Address address : addressBook) {
            if (address.getNodeId().id() % 2 == 0) {
                issMetrics.stateHashValidityObserver(round, address.getNodeId(), hashA, hashA);
                expectedIssCount--;
                expectedIssweight -= address.getWeight();
            } else {
                issMetrics.stateHashValidityObserver(round, address.getNodeId(), hashA, hashB);
                expectedIssCount++;
                expectedIssweight += address.getWeight();
            }
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");
        }

        // Report a catastrophic ISS for a round in the past. Should be ignored.
        issMetrics.catastrophicIssObserver(round - 1);
        assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
        assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");

        // Report a catastrophic ISS.
        round++;
        issMetrics.catastrophicIssObserver(round);
        expectedIssCount = addressBook.getSize();
        expectedIssweight = addressBook.getTotalWeight();
        assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
        assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");

        // Heal all nodes.
        round++;
        for (final Address address : addressBook) {
            issMetrics.stateHashValidityObserver(round, address.getNodeId(), hashA, hashA);
            expectedIssCount--;
            expectedIssweight -= address.getWeight();
            assertEquals(expectedIssCount, issMetrics.getIssCount(), "unexpected ISS count");
            assertEquals(expectedIssweight, issMetrics.getIssWeight(), "unexpected ISS weight");
        }

        assertEquals(0, issMetrics.getIssCount(), "unexpected ISS count");
        assertEquals(0, issMetrics.getIssWeight(), "unexpected ISS weight");
    }
}
