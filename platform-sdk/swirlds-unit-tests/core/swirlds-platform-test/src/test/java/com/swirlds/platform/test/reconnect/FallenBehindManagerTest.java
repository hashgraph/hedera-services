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

package com.swirlds.platform.test.reconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.reconnect.FallenBehindManagerImpl;
import com.swirlds.platform.reconnect.ReconnectSettingsImpl;
import com.swirlds.platform.sync.FallenBehindManager;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FallenBehindManagerTest {
    final int numNodes = 11;
    final double fallenBehindThreshold = 0.5;
    final NodeId selfId = NodeId.createMain(0);
    final RandomGraph graph = new RandomGraph(numNodes, numNodes + (numNodes % 2), numNodes);
    final AtomicInteger platformNotification = new AtomicInteger(0);
    final AtomicInteger fallenBehindNotification = new AtomicInteger(0);
    final ReconnectSettingsImpl settings = new ReconnectSettingsImpl();
    final FallenBehindManager manager = new FallenBehindManagerImpl(
            selfId, graph, platformNotification::incrementAndGet, fallenBehindNotification::incrementAndGet, settings);

    @Test
    void test() {
        settings.fallenBehindThreshold = fallenBehindThreshold;

        assertFallenBehind(false, 0, "default should be none report fallen behind");

        // node 1 reports fallen behind
        manager.reportFallenBehind(NodeId.createMain(1));
        assertFallenBehind(false, 1, "one node only reported fallen behind");

        // if the same node reports again, nothing should change
        manager.reportFallenBehind(NodeId.createMain(1));
        assertFallenBehind(false, 1, "if the same node reports again, nothing should change");

        manager.reportFallenBehind(NodeId.createMain(2));
        manager.reportFallenBehind(NodeId.createMain(3));
        manager.reportFallenBehind(NodeId.createMain(4));
        manager.reportFallenBehind(NodeId.createMain(5));
        assertFallenBehind(false, 5, "we should still be missing one for fallen behind");

        manager.reportFallenBehind(NodeId.createMain(6));
        assertFallenBehind(true, 6, "we should be fallen behind");

        manager.reportFallenBehind(NodeId.createMain(1));
        manager.reportFallenBehind(NodeId.createMain(2));
        manager.reportFallenBehind(NodeId.createMain(3));
        manager.reportFallenBehind(NodeId.createMain(4));
        manager.reportFallenBehind(NodeId.createMain(5));
        manager.reportFallenBehind(NodeId.createMain(6));
        assertFallenBehind(true, 6, "if the same nodes report again, nothing should change");

        manager.reportFallenBehind(NodeId.createMain(7));
        manager.reportFallenBehind(NodeId.createMain(8));
        assertFallenBehind(true, 8, "more nodes reported, but the status should be the same");

        manager.resetFallenBehind();
        fallenBehindNotification.set(0);
        assertFallenBehind(false, 0, "resetting should return to default");
    }

    private void assertFallenBehind(
            final boolean expectedFallenBehind, final int expectedNumFallenBehind, final String message) {
        assertEquals(expectedFallenBehind, manager.hasFallenBehind(), message);
        assertEquals(expectedNumFallenBehind, manager.numReportedFallenBehind(), message);
        if (expectedFallenBehind) {
            assertEquals(
                    1,
                    fallenBehindNotification.get(),
                    "if fallen behind, the platform should be notified exactly once");
        } else {
            assertEquals(
                    0,
                    fallenBehindNotification.get(),
                    "if not fallen behind, the platform should not have been notified");
        }
    }
}
