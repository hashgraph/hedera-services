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

package com.swirlds.platform.test.reconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig_;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FallenBehindManagerTest {
    private final int numNodes = 11;
    private final AddressBook addressBook = RandomAddressBookBuilder.create(Randotron.create())
            .withSize(numNodes)
            .build();
    private final double fallenBehindThreshold = 0.5;
    private final NodeId selfId = addressBook.getNodeId(0);
    private final AtomicInteger fallenBehindNotification = new AtomicInteger(0);
    private final ReconnectConfig config = new TestConfigBuilder()
            .withValue(ReconnectConfig_.FALLEN_BEHIND_THRESHOLD, fallenBehindThreshold)
            .getOrCreateConfig()
            .getConfigData(ReconnectConfig.class);
    final List<PeerInfo> peers = Utilities.createPeerInfoList(addressBook, selfId);
    final NetworkTopology topology = new StaticTopology(peers, selfId);
    private final FallenBehindManager manager = new FallenBehindManagerImpl(
            addressBook,
            selfId,
            topology,
            mock(StatusActionSubmitter.class),
            fallenBehindNotification::incrementAndGet,
            config);

    @Test
    void test() {
        assertFallenBehind(false, 0, "default should be none report fallen behind");

        // node 1 reports fallen behind
        manager.reportFallenBehind(NodeId.of(1));
        assertFallenBehind(false, 1, "one node only reported fallen behind");

        // if the same node reports again, nothing should change
        manager.reportFallenBehind(NodeId.of(1));
        assertFallenBehind(false, 1, "if the same node reports again, nothing should change");

        manager.reportFallenBehind(NodeId.of(2));
        manager.reportFallenBehind(NodeId.of(3));
        manager.reportFallenBehind(NodeId.of(4));
        manager.reportFallenBehind(NodeId.of(5));
        assertFallenBehind(false, 5, "we should still be missing one for fallen behind");

        manager.reportFallenBehind(NodeId.of(6));
        assertFallenBehind(true, 6, "we should be fallen behind");

        manager.reportFallenBehind(NodeId.of(1));
        manager.reportFallenBehind(NodeId.of(2));
        manager.reportFallenBehind(NodeId.of(3));
        manager.reportFallenBehind(NodeId.of(4));
        manager.reportFallenBehind(NodeId.of(5));
        manager.reportFallenBehind(NodeId.of(6));
        assertFallenBehind(true, 6, "if the same nodes report again, nothing should change");

        manager.reportFallenBehind(NodeId.of(7));
        manager.reportFallenBehind(NodeId.of(8));
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
