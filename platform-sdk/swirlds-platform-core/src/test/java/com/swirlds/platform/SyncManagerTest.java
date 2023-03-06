/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.reconnect.FallenBehindManagerImpl;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.stream.Stream;

import static com.swirlds.common.system.EventCreationRuleResponse.CREATE;
import static com.swirlds.common.system.EventCreationRuleResponse.DONT_CREATE;
import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

// Tests utilize static Settings configuration and must not be run in parallel
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SyncManagerTest {
    private static final long ID = 0L;
    private static final NodeId OTHER_ID = new NodeId(false, 1L);

    private static Stream<Arguments> swirldStateManagers() {
        return Stream.of(
                Arguments.of(spy(SwirldStateManagerSingle.class)), Arguments.of(spy(SwirldStateManagerDouble.class)));
    }

    /**
     * A helper class that contains dummy data to feed into SyncManager lambdas.
     */
    private static class SyncManagerTestData {

        FreezeManager freezeManager;
        StartUpEventFrozenManager startUpEventFrozenManager;
        public DummyHashgraph hashgraph;
        public EventTransactionPool eventTransactionPool;
        public SwirldStateManager swirldStateManager;
        public RandomGraph connectionGraph;
        public SyncManagerImpl syncManager;
        public CriticalQuorum criticalQuorum;
        public DummyEventQueue eventQueue;

        public SyncManagerTestData() {
            this(new NodeId(false, ID), spy(SwirldStateManager.class));
        }

        public SyncManagerTestData(final SwirldStateManager swirldStateManager) {
            this(new NodeId(false, ID), swirldStateManager);
        }

        public SyncManagerTestData(final NodeId nodeId) {
            this(nodeId, spy(SwirldStateManager.class));
        }

        public SyncManagerTestData(final NodeId nodeId, final SwirldStateManager swirldStateManager) {
            freezeManager = mock(FreezeManager.class);
            startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
            hashgraph = new DummyHashgraph();
            eventTransactionPool = spy(EventTransactionPool.class);

            this.swirldStateManager = swirldStateManager;

            doReturn(0).when(eventTransactionPool).numTransForEvent();
            doReturn(false).when(swirldStateManager).isInFreezePeriod(any());

            connectionGraph = new RandomGraph(100, 40, 0);
            criticalQuorum = new CriticalQuorum() {
                @Override
                public boolean isInCriticalQuorum(final long nodeId) {
                    if (hashgraph.isInCriticalQuorum.containsKey(nodeId)) {
                        return hashgraph.isInCriticalQuorum.get(nodeId);
                    } else {
                        return false;
                    }
                }

                @Override
                public void eventAdded(final EventImpl event) {}

                @Override
                public EventCreationRuleResponse shouldCreateEvent(BaseEvent selfParent, BaseEvent otherParent) {
                    return null;
                }
            };

            eventQueue = new DummyEventQueue(hashgraph);
            syncManager = new SyncManagerImpl(
                    eventQueue,
                    connectionGraph,
                    nodeId,
                    new EventCreationRules(List.of(nodeId, startUpEventFrozenManager, freezeManager)),
                    criticalQuorum,
                    hashgraph.getAddressBook(),
                    new FallenBehindManagerImpl(
                            nodeId,
                            connectionGraph,
                            () -> {},
                            () -> {},
                            Settings.getInstance().getReconnect()));
        }
    }

    protected void resetTestSettings() {
        Settings.getInstance().getReconnect().fallenBehindThreshold = 0.25;
        Settings.getInstance().setEventIntakeQueueThrottleSize(100);
        Settings.getInstance().setMaxIncomingSyncsInc(10);
        Settings.getInstance().setMaxOutgoingSyncs(10);
        Settings.getInstance().getState().saveStatePeriod = 100;
        Settings.getInstance().setStaleEventPreventionThreshold(10);
    }

    /**
     * Verify that SyncManager's core functionality is working with basic input.
     */
    @Test
    @Order(0)
    void basicTest() {
        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();

        final int[] neighbors = test.connectionGraph.getNeighbors(0);

        // we should not think we have fallen behind initially
        assertFalse(test.syncManager.hasFallenBehind());
        // should be null as we have no indication of falling behind
        assertNull(test.syncManager.getNeededForFallenBehind());

        // neighbors 0 and 1 report fallen behind
        test.syncManager.reportFallenBehind(new NodeId(false, neighbors[0]));
        test.syncManager.reportFallenBehind(new NodeId(false, neighbors[1]));

        // we still dont have enough reports that we have fallen behind, we need more than [fallenBehindThreshold] of
        // the neighbors
        assertFalse(test.syncManager.hasFallenBehind());

        // add more reports
        for (int i = 2; i < 10; i++) {
            test.syncManager.reportFallenBehind(new NodeId(false, neighbors[i]));
        }

        // we are still missing 1 report
        assertFalse(test.syncManager.hasFallenBehind());

        // get the list of nodes we need to call
        final List<Long> list = test.syncManager.getNeededForFallenBehind();
        for (final Long nodeId : list) {
            // none of the nodes we need to call should be those who already reported we have fallen behind
            for (int i = 0; i < 10; i++) {
                assertTrue(nodeId != neighbors[i]);
            }
        }

        // add the report that will go over the [fallenBehindThreshold]
        test.syncManager.reportFallenBehind(new NodeId(false, neighbors[10]));

        // we should now say we have fallen behind
        assertTrue(test.syncManager.hasFallenBehind());

        // reset it
        test.syncManager.resetFallenBehind();

        // we should now be back where we started
        assertFalse(test.syncManager.hasFallenBehind());
    }

    /**
     * Test when the SyncManager should accept an incoming sync
     */
    @Test
    @Order(1)
    void shouldAcceptSyncTest() {
        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();

        // We should accept a sync if the event queue is empty and we aren't exceeding the maximum number of syncs
        test.hashgraph.eventIntakeQueueSize = 0;
        assertTrue(test.syncManager.shouldAcceptSync());

        // We should not accept a sync if the event queue fills up
        test.hashgraph.eventIntakeQueueSize = 101;
        assertFalse(test.syncManager.shouldAcceptSync());
        test.hashgraph.eventIntakeQueueSize = 0;

        // Once the queue and concurrent syncs decrease we should be able to sync again.
        assertTrue(test.syncManager.shouldAcceptSync());
    }

    /**
     * Test when the sync manager should initiate a sync of its own.
     */
    @Test
    @Order(2)
    void shouldInitiateSyncTest() {
        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();

        // It is ok to initiate a sync if the intake queue is not full.
        test.hashgraph.eventIntakeQueueSize = 0;
        assertTrue(test.syncManager.shouldInitiateSync());

        // It is not ok to initiate a sync if the intake queue is full.
        test.hashgraph.eventIntakeQueueSize = 101;
        assertFalse(test.syncManager.shouldInitiateSync());
    }

    /**
     * Verify the behavior of SyncManager's getNeighborsToCall function
     */
    @Test
    @Order(3)
    void getNeighborsToCall() {
        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();

        // Test of the current algorithm
        for (int i = 0; i < 10; i++) {
            final List<Long> next = test.syncManager.getNeighborsToCall();
            assertNotEquals(next, null);
            assertEquals(next.size(), 1);
            assertTrue(next.get(0) > 0 && next.get(0) <= 100);
        }
    }

    /**
     * Verify basic behavior of shouldCreateEvent()
     */
    @Test
    @Order(5)
    void shouldCreateEventTest() {
        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();

        // The first time this is called it should return false.
        // This is because the dummy hashgraph always returns false for isStrongMinorityInMaxRound by default
        // and we will never create an event if this node (ID 0) or the other node are not a part of the super-minority.
        assertFalse(test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0));

        // If the current node is in the critical quorum then an event should be created.
        test.hashgraph.isInCriticalQuorum.put(ID, true);
        assertTrue(test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0));
        test.hashgraph.isInCriticalQuorum.put(ID, false);
        assertFalse(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0),
                "if neither node is part of the superMinority in the latest round, don't create an event");

        // If the other node is in the critical quorum then an event should be created.
        test.hashgraph.isInCriticalQuorum.put(OTHER_ID.getId(), true);
        assertTrue(test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0));
        test.hashgraph.isInCriticalQuorum.put(OTHER_ID.getId(), false);
        assertFalse(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0),
                "if neither node is part of the superMinority in the latest round, don't create an event");

        // If both are in the critical quorum then an event should be created.
        test.hashgraph.isInCriticalQuorum.put(ID, true);
        test.hashgraph.isInCriticalQuorum.put(OTHER_ID.getId(), true);
        assertTrue(test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0));
    }

    /**
     * A mirror node should not create events
     */
    @Test
    @Order(6)
    void shouldCreateEventTestMirrorNode() {
        final SyncManagerTestData test = new SyncManagerTestData(new NodeId(true, ID));
        resetTestSettings();
        test.hashgraph.isInCriticalQuorum.put(ID, true);
        assertFalse(test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0), "mirror node cannot create events");
    }

    /**
     * If there any frozen transaction events then do not create any new events.
     */
    @Test
    @Order(7)
    void shouldCreateEventFreeze() {
        resetTestSettings();

        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();
        test.hashgraph.isInCriticalQuorum.put(ID, true);

        when(test.startUpEventFrozenManager.shouldCreateEvent()).thenReturn(PASS);
        when(test.freezeManager.shouldCreateEvent()).thenReturn(DONT_CREATE);
        assertFalse(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0),
                "should not create event when startUpEventFrozenManager returns PASS and freezeManager returns "
                        + "DONT_CREATE");

        when(test.startUpEventFrozenManager.shouldCreateEvent()).thenReturn(DONT_CREATE);
        when(test.freezeManager.shouldCreateEvent()).thenReturn(CREATE);
        assertFalse(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0),
                "should not create event when startUpEventFrozenManager returns DONT_CREATE");

        when(test.startUpEventFrozenManager.shouldCreateEvent()).thenReturn(CREATE);
        when(test.freezeManager.shouldCreateEvent()).thenReturn(DONT_CREATE);
        assertTrue(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, 0, 0),
                "should create event when startUpEventFrozenManager returns CREATE");
    }

    @Test
    @Order(8)
    void shouldCreateEventFallenBehind() {
        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();
        // If one node has fallen behind then do not create new events.
        assertFalse(
                test.syncManager.shouldCreateEvent(OTHER_ID, true, 0, 0),
                "when one node fallen behind, should not create events");
    }

    /**
     * Verify shouldCreateEvent() while throttled
     */
    @Test
    @Order(9)
    void shouldCreateEventThrottled() {
        resetTestSettings();
        final int eventsRead = 0;
        final int eventsWritten = 0;

        final NodeId mainNodeId = new NodeId(false, ID);
        final SyncManagerTestData test = new SyncManagerTestData(mainNodeId);
        test.hashgraph.isInCriticalQuorum.put(ID, true);

        when(test.startUpEventFrozenManager.shouldCreateEvent()).thenReturn(DONT_CREATE);
        assertFalse(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, eventsRead, eventsWritten),
                "when startUpEventFrozenManager.shouldNotCreateEvent returns true , should not create events");
        when(test.startUpEventFrozenManager.shouldCreateEvent()).thenReturn(PASS);

        when(test.freezeManager.shouldCreateEvent()).thenReturn(DONT_CREATE);
        assertFalse(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, eventsRead, eventsWritten),
                "when freezeManager.shouldNotCreateEvent returns true , should not create events");
        when(test.freezeManager.shouldCreateEvent()).thenReturn(PASS);

        assertTrue(
                test.syncManager.shouldCreateEvent(OTHER_ID, false, eventsRead, eventsWritten),
                "if all checks pass, an event should be created");
    }

    /**
     * Verify behavior of shouldCreateEvent() when a large number of events are read.
     */
    @Test
    @Order(10)
    void shouldCreateEventLargeRead() {
        final SyncManagerTestData test = new SyncManagerTestData();
        resetTestSettings();

        // If events read is too large then do not create an event
        test.hashgraph.isInCriticalQuorum.put(ID, true);
        assertFalse(
                test.syncManager.shouldCreateEvent(
                        OTHER_ID,
                        false,
                        Settings.getInstance().getStaleEventPreventionThreshold()
                                        * test.hashgraph.getAddressBook().getSize()
                                + 1,
                        0),
                "if we read too many events during this sync, we skip creating an event to reduce the probability of "
                        + "having a stale event");
    }
}
