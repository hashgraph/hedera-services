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

package com.swirlds.platform.test.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaticConnectionManagersTest {
    @Mock
    OutboundConnectionCreator connectionCreator;

    private static List<Arguments> topologicalVariations() {
        return List.of(Arguments.of(10, 10), Arguments.of(20, 20), Arguments.of(50, 40), Arguments.of(60, 40));
    }

    @ParameterizedTest
    @MethodSource("topologicalVariations")
    void testShouldConnectToMe(final int numNodes, final int numNeighbors) throws Exception {
        final Random r = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook =
                new RandomAddressBookGenerator(r).setSize(numNodes).build();
        final NodeId selfId = addressBook.getNodeId(r.nextInt(numNodes));
        final StaticTopology topology = new StaticTopology(addressBook, selfId, numNeighbors);
        final StaticConnectionManagers managers = new StaticConnectionManagers(topology, connectionCreator);
        final List<NodeId> neighbors = topology.getNeighbors();
        final NodeId neighbor = neighbors.get(r.nextInt(neighbors.size()));

        if (topology.shouldConnectToMe(neighbor)) {
            final ConnectionManager manager = managers.getManager(neighbor, false);
            assertNotNull(manager, "should have a manager for this connection");
            final Connection c1 = new FakeConnection(selfId, neighbor);
            managers.newConnection(c1);
            assertSame(c1, manager.waitForConnection(), "the manager should have received the connection supplied");
            assertTrue(c1.connected(), "a new inbound connection should be connected");
            final Connection c2 = new FakeConnection(selfId, neighbor);
            managers.newConnection(c2);
            assertFalse(c1.connected(), "the new connection should have disconnected the old one");
            assertSame(c2, manager.waitForConnection(), "c2 should have replaced c1");
        } else {
            final ConnectionManager manager = managers.getManager(neighbor, false);
            assertNull(manager, "should not have a manager for this connection");
            final Connection c = new FakeConnection(selfId, neighbor);
            managers.newConnection(c);
            assertFalse(
                    c.connected(), "if an illegal connection is established, it should be disconnected immediately");
        }
    }

    @ParameterizedTest
    @MethodSource("topologicalVariations")
    void testShouldConnectTo(final int numNodes, final int numNeighbors) throws Exception {
        final Random r = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook =
                new RandomAddressBookGenerator(r).setSize(numNodes).build();
        final NodeId selfId = addressBook.getNodeId(r.nextInt(numNodes));
        final StaticTopology topology = new StaticTopology(addressBook, selfId, numNeighbors);
        final StaticConnectionManagers managers = new StaticConnectionManagers(topology, connectionCreator);
        final List<NodeId> neighbors = topology.getNeighbors();
        final NodeId neighbor = neighbors.get(r.nextInt(neighbors.size()));

        if (topology.shouldConnectTo(neighbor)) {
            Mockito.when(connectionCreator.createConnection(Mockito.any())).thenAnswer(inv -> {
                final NodeId peerId = inv.getArgument(0, NodeId.class);
                return new FakeConnection(selfId, peerId);
            });
            final ConnectionManager manager = managers.getManager(neighbor, true);
            assertNotNull(manager, "should have a manager for this connection");
            assertTrue(
                    manager.waitForConnection().connected(),
                    "outbound connections should be esablished by the manager");
        } else {
            final ConnectionManager manager = managers.getManager(neighbor, true);
            assertNull(manager, "should not have a manager for this connection");
        }
    }
}
