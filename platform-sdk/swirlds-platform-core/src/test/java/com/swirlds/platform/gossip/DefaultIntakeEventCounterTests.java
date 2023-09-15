/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip;

import static org.mockito.Mockito.mock;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("DefaultIntakeEventCounter Tests")
class DefaultIntakeEventCounterTests {
    private Random random;

    @BeforeEach
    void setup() {
        random = RandomUtils.getRandomPrintSeed();
    }

    private IntakeEventCounter createIntakeCounter(@NonNull final Set<NodeId> nodes) {
        final AddressBook addressBook = mock(AddressBook.class);
        Mockito.when(addressBook.getNodeIdSet()).thenReturn(nodes);

        return new DefaultIntakeEventCounter(addressBook);
    }

    private Set<NodeId> generateNodeIds(final int count) {
        final Set<NodeId> nodeIds = new HashSet<>();
        for (int i = 0; i < count; i++) {
            nodeIds.add(new NodeId(random.nextLong(Long.MAX_VALUE)));
        }

        return nodeIds;
    }

    @Test
    @DisplayName("Test reset behavior")
    void testReset() {
        //        final Set<NodeId> nodeIds = generateNodeIds(50);
        //        final IntakeEventCounter intakeCounter = createIntakeCounter(nodeIds);
        //        final Map<NodeId, Long> expectedCounts = generateExpectedCountMap(nodeIds);
        //
        //        performRandomOperations(intakeCounter, nodeIds, expectedCounts, 100);
        //
        //        assertValidState(intakeCounter, nodeIds, expectedCounts);
        //        intakeCounter.reset();
        //
        //        for (final NodeId nodeId : nodeIds) {
        //            assertFalse(intakeCounter.hasUnprocessedEvents(nodeId));
        //        }
    }
}
