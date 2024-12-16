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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.ResettableRandom;
import com.swirlds.platform.test.fixtures.addressbook.RandomRosterEntryBuilder;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultIntakeEventCounter Tests")
class DefaultIntakeEventCounterTests {
    private IntakeEventCounter intakeCounter;

    final NodeId nodeId1 = NodeId.of(1);
    final NodeId nodeId2 = NodeId.of(2);

    @BeforeEach
    void setup() {
        final ResettableRandom random = getRandomPrintSeed();
        final ArrayList<RosterEntry> rosterEntries = new ArrayList<>();
        rosterEntries.add(
                RandomRosterEntryBuilder.create(random).withNodeId(nodeId1.id()).build());
        rosterEntries.add(
                RandomRosterEntryBuilder.create(random).withNodeId(nodeId2.id()).build());

        this.intakeCounter = new DefaultIntakeEventCounter(
                Roster.newBuilder().rosterEntries(rosterEntries).build());
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
