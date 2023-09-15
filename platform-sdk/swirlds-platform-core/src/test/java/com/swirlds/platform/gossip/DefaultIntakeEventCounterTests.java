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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("DefaultIntakeEventCounter Tests")
class DefaultIntakeEventCounterTests {
    private IntakeEventCounter createIntakeCounter(@NonNull final Set<NodeId> nodes) {
        final AddressBook addressBook = mock(AddressBook.class);
        Mockito.when(addressBook.getNodeIdSet()).thenReturn(nodes);

        return new DefaultIntakeEventCounter(addressBook);
    }

    @Test
    @DisplayName("Test unprocessed events check")
    void unprocessedEvents() {
        final NodeId nodeId1 = new NodeId(1);
        final NodeId nodeId2 = new NodeId(2);
        final IntakeEventCounter intakeCounter = createIntakeCounter(Set.of(nodeId1, nodeId2));

        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId1));

        intakeCounter.getPeerCounter(nodeId1).incrementAndGet();
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.getPeerCounter(nodeId1).incrementAndGet();
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.getPeerCounter(nodeId1).decrementAndGet();
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.getPeerCounter(nodeId1).decrementAndGet();
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));
    }

    @Test
    @DisplayName("Test reset")
    void reset() {
        final NodeId nodeId1 = new NodeId(1);
        final NodeId nodeId2 = new NodeId(2);
        final IntakeEventCounter intakeCounter = createIntakeCounter(Set.of(nodeId1, nodeId2));

        intakeCounter.getPeerCounter(nodeId1).incrementAndGet();
        intakeCounter.getPeerCounter(nodeId1).incrementAndGet();
        intakeCounter.getPeerCounter(nodeId2).incrementAndGet();

        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.reset();
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));
    }
}
