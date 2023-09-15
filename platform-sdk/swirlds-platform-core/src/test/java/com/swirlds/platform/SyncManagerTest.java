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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.state.SwirldStateManager;
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
public class SyncManagerTest {

    /**
     * A helper class that contains dummy data to feed into SyncManager lambdas.
     */
    private static class SyncManagerTestData {

        FreezeManager freezeManager;
        public DummyHashgraph hashgraph;
        public AddressBook addressBook;
        public NodeId selfId;
        public TransactionPool transactionPool;
        public SwirldStateManager swirldStateManager;
        public RandomGraph connectionGraph;
        public SyncManagerImpl syncManager;
        public Configuration configuration;

        public SyncManagerTestData() {
            this(spy(SwirldStateManager.class));
        }

        public SyncManagerTestData(final SwirldStateManager swirldStateManager) {
            freezeManager = mock(FreezeManager.class);
            final Random random = getRandomPrintSeed();
            hashgraph = new DummyHashgraph(random, 0);
            final PlatformContext platformContext =
                    TestPlatformContextBuilder.create().build();

            transactionPool = spy(new TransactionPool(platformContext));

            this.swirldStateManager = swirldStateManager;

            doReturn(false).when(swirldStateManager).isInFreezePeriod(any());

            this.addressBook = hashgraph.getAddressBook();
            this.selfId = addressBook.getNodeId(0);
            final int size = addressBook.getSize();

            connectionGraph = new RandomGraph(size, 40, 0);
            configuration = new TestConfigBuilder()
                    .withValue("reconnect.fallenBehindThreshold", "0.25")
                    .withValue("event.eventIntakeQueueThrottleSize", "100")
                    .withValue("event.staleEventPreventionThreshold", "10")
                    .getOrCreateConfig();
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
}
