/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.service.gossip.IntakeEventCounter;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.AddressBook;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("DefaultIntakeEventCounter Tests")
class DefaultIntakeEventCounterTests {
    private IntakeEventCounter intakeCounter;

    final NodeId nodeId1 = new NodeId(1);
    final NodeId nodeId2 = new NodeId(2);

    @BeforeEach
    void setup() {
        final AddressBook addressBook = mock(AddressBook.class);
        Mockito.when(addressBook.getNodeIdSet()).thenReturn(Set.of(nodeId1, nodeId2));

        this.intakeCounter = new DefaultIntakeEventCounter(addressBook);
    }

    @Test
    @DisplayName("Test unprocessed events check")
    void unprocessedEvents() {
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId1));

        intakeCounter.eventEnteredIntakePipeline(nodeId1);
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.eventEnteredIntakePipeline(nodeId1);
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.eventExitedIntakePipeline(nodeId1);
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.eventExitedIntakePipeline(nodeId1);
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));
    }

    @Test
    @DisplayName("Test reset")
    void reset() {
        intakeCounter.eventEnteredIntakePipeline(nodeId1);
        intakeCounter.eventEnteredIntakePipeline(nodeId1);
        intakeCounter.eventEnteredIntakePipeline(nodeId2);

        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertTrue(intakeCounter.hasUnprocessedEvents(nodeId2));

        intakeCounter.reset();
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId1));
        assertFalse(intakeCounter.hasUnprocessedEvents(nodeId2));
    }
}
