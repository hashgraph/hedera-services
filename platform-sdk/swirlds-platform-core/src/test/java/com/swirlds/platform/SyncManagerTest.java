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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

// Tests utilize static Settings configuration and must not be run in parallel
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SyncManagerTest {

    /**
     * A helper class that contains dummy data to feed into SyncManager lambdas.
     */
    private static class SyncManagerTestData {
        public DummyHashgraph hashgraph;
        public AddressBook addressBook;
        public NodeId selfId;
        public TransactionPool transactionPool;
        public RandomGraph connectionGraph;
        public SyncManagerImpl syncManager;
        public CriticalQuorum criticalQuorum;
        public DummyEventQueue eventQueue;
        public Configuration configuration;

        public SyncManagerTestData() {
            final Random random = getRandomPrintSeed();
            hashgraph = new DummyHashgraph(random, 0);
            final PlatformContext platformContext =
                    TestPlatformContextBuilder.create().build();

            transactionPool = spy(new TransactionPool(platformContext));

            this.addressBook = hashgraph.getAddressBook();
            this.selfId = addressBook.getNodeId(0);
            final int size = addressBook.getSize();

            connectionGraph = new RandomGraph(size, 40, 0);
            criticalQuorum = new CriticalQuorum() {
                @Override
                public boolean isInCriticalQuorum(final NodeId nodeId) {
                    if (hashgraph.isInCriticalQuorum.containsKey(nodeId)) {
                        return hashgraph.isInCriticalQuorum.get(nodeId);
                    } else {
                        return false;
                    }
                }

                @Override
                public void eventAdded(final EventImpl event) {}
            };
            configuration = new TestConfigBuilder()
                    .withValue("reconnect.fallenBehindThreshold", "0.25")
                    .withValue("event.eventIntakeQueueThrottleSize", "100")
                    .withValue("event.staleEventPreventionThreshold", "10")
                    .getOrCreateConfig();
            final ReconnectConfig reconnectConfig = configuration.getConfigData(ReconnectConfig.class);
            final EventConfig eventConfig = configuration.getConfigData(EventConfig.class);
            eventQueue = new DummyEventQueue(hashgraph);
            syncManager = new SyncManagerImpl(
                    platformContext,
                    eventQueue,
                    connectionGraph,
                    selfId,
                    criticalQuorum,
                    hashgraph.getAddressBook(),
                    new FallenBehindManagerImpl(
                            addressBook,
                            selfId,
                            connectionGraph,
                            mock(StatusActionSubmitter.class),
                            () -> {},
                            reconnectConfig),
                    eventConfig);
        }
    }

    /**
     * Verify that SyncManager's core functionality is working with basic input.
     */
    @Test
    @Order(0)
    void basicTest() {
        final SyncManagerTestData test = new SyncManagerTestData();

        final int[] neighbors = test.connectionGraph.getNeighbors(0);

        // we should not think we have fallen behind initially
        assertFalse(test.syncManager.hasFallenBehind());
        // should be null as we have no indication of falling behind
        assertNull(test.syncManager.getNeededForFallenBehind());

        // neighbors 0 and 1 report fallen behind
        test.syncManager.reportFallenBehind(test.addressBook.getNodeId(neighbors[0]));
        test.syncManager.reportFallenBehind(test.addressBook.getNodeId(neighbors[1]));

        // we still dont have enough reports that we have fallen behind, we need more than [fallenBehindThreshold] of
        // the neighbors
        assertFalse(test.syncManager.hasFallenBehind());

        // add more reports
        for (int i = 2; i < 10; i++) {
            test.syncManager.reportFallenBehind(test.addressBook.getNodeId(neighbors[i]));
        }

        // we are still missing 1 report
        assertFalse(test.syncManager.hasFallenBehind());

        // get the list of nodes we need to call
        final List<NodeId> list = test.syncManager.getNeededForFallenBehind();
        for (final NodeId nodeId : list) {
            // none of the nodes we need to call should be those who already reported we have fallen behind
            for (int i = 0; i < 10; i++) {
                assertTrue(test.addressBook.getIndexOfNodeId(nodeId) != neighbors[i]);
            }
        }

        // add the report that will go over the [fallenBehindThreshold]
        test.syncManager.reportFallenBehind(test.addressBook.getNodeId(neighbors[10]));

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
        final AddressBook addressBook = test.hashgraph.getAddressBook();
        final NodeId selfId = test.hashgraph.selfId;
        final NodeId firstNode = addressBook.getNodeId(0);
        final int lastIndex = addressBook.getSize() - 1;
        final NodeId lastNode = addressBook.getNodeId(lastIndex);

        // Test of the current algorithm
        for (int i = 0; i < 10; i++) {
            final List<NodeId> next = test.syncManager.getNeighborsToCall();
            final int firstIndex = addressBook.getIndexOfNodeId(firstNode);
            final int nextIndex = addressBook.getIndexOfNodeId(next.get(0));
            final int selfIndex = addressBook.getIndexOfNodeId(selfId);
            assertNotEquals(null, next);
            assertTrue(next.size() <= 10);
            assertTrue(nextIndex >= firstIndex && nextIndex <= lastIndex && nextIndex != selfIndex);
        }
    }
}
