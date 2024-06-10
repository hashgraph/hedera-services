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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.topology.ConnectivityManager;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectivityManagerTest {
    @Mock
    OutboundConnectionCreator connectionCreator;

    @ParameterizedTest
    @MethodSource("topologicalVariations")
    void testShouldConnectToMe(final int numNodes) throws Exception {
        final Random r = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(r).withSize(numNodes).build();
        final NodeId selfId = addressBook.getNodeId(r.nextInt(numNodes));

        final List<PeerInfo> peers = Utilities.createPeerInfoList(addressBook, selfId);
        final NetworkTopology topology = new StaticTopology(peers, selfId);

        final ConnectivityManager managers = new ConnectivityManager(topology, connectionCreator);
        final List<NodeId> neighbors = topology.getNeighbors().stream().toList();
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

    /**
     * asserts that the manager is created and the connection is established if the topology allows it,
     *  and that the manager is not created if the topology does not allow it
     */
    @ParameterizedTest
    @MethodSource("topologicalVariations")
    void testShouldConnectTo(final int numNodes) throws Exception {
        final Random r = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(r).withSize(numNodes).build();
        final NodeId selfId = addressBook.getNodeId(r.nextInt(numNodes));
        final List<PeerInfo> peers = Utilities.createPeerInfoList(addressBook, selfId);
        final NetworkTopology topology = new StaticTopology(peers, selfId);

        final ConnectivityManager managers = new ConnectivityManager(topology, connectionCreator);
        final List<NodeId> neighbors = topology.getNeighbors().stream().toList();
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

    /**
     * asserts that when a peerList containing known peer is passed, the manager for that peer is left unchanged
     */
    @Test
    void testAddKnownPeerLeftUnchanged() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(r).withSize(10).build();
        final NodeId selfId = addressBook.getNodeId(2);

        final List<PeerInfo> peerInfos = Utilities.createPeerInfoList(addressBook, selfId);
        final NetworkTopology topology = new StaticTopology(peerInfos, selfId);

        final ConnectivityManager factory = new ConnectivityManager(topology, connectionCreator);

        final NodeId testNode1 = addressBook.getNodeId(3);
        final PeerInfo peer1 = new PeerInfo(testNode1, "localhost", "testHost1", Mockito.mock(Certificate.class));
        final List<PeerInfo> peers = List.of(peer1);
        // keep a reference to the manager for the testPeer. We don't know if it's an inbound or outbound
        final ConnectionManager manager5 = factory.getManager(testNode1, true);
        final List<ConnectionManager> updatedManagers1 = factory.updatePeers(peers);

        assertNotNull(updatedManagers1);
        assertEquals(1, updatedManagers1.size());
        // The manager should have been unchanged for this existing peer
        assertEquals(updatedManagers1.getFirst(), manager5);
    }

    /**
     * asserts that when a peerList containing a new peer is passed, the manager for that peer is created
     */
    @Test
    void testAddNewPeerUpdatesManagers() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(r).withSize(12).build();
        final NodeId selfId = addressBook.getNodeId(11);

        final List<PeerInfo> peers = Utilities.createPeerInfoList(addressBook, selfId);
        final NetworkTopology topology = new StaticTopology(peers, selfId);

        final ConnectivityManager factory = new ConnectivityManager(topology, connectionCreator);

        final NodeId testNode1 = addressBook.getNodeId(9);
        final PeerInfo peer1 = new PeerInfo(testNode1, "localhost", "testHost1", Mockito.mock(Certificate.class));

        // we add a new peer and ensure that the manager is created for all peers, including peer2
        final NodeId testPeer2 = addressBook.getNodeId(10);
        final PeerInfo peer2 = new PeerInfo(testPeer2, "localhost", "testHost2", Mockito.mock(Certificate.class));
        final List<PeerInfo> peers2 = List.of(peer1, peer2);
        final List<ConnectionManager> updatedManagers2 = factory.updatePeers(peers2);
        assertNotNull(updatedManagers2);
        assertEquals(2, updatedManagers2.size());
    }

    /**
     * asserts that when an empty peerList is passed, no manager is created
     */
    @Test
    void testRemoveNewPeerUpdated() {
        final Random r = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(r).withSize(21).build();
        final NodeId selfId = addressBook.getNodeId(20);

        final List<PeerInfo> peers = Utilities.createPeerInfoList(addressBook, selfId);
        final NetworkTopology topology = new StaticTopology(peers, selfId);
        final ConnectivityManager factory = new ConnectivityManager(topology, connectionCreator);

        final NodeId testNode1 = addressBook.getNodeId(4);
        final PeerInfo peer1 = new PeerInfo(testNode1, "localhost", "testHost1", Mockito.mock(Certificate.class));
        final NodeId testPeer2 = addressBook.getNodeId(5);
        final PeerInfo peer2 = new PeerInfo(testPeer2, "localhost", "testHost2", Mockito.mock(Certificate.class));
        final List<PeerInfo> peers2 = List.of(peer1, peer2);
        final List<ConnectionManager> updatedManagers2 = factory.updatePeers(peers2);
        assertNotNull(updatedManagers2);
        assertEquals(2, updatedManagers2.size());

        // remove the two peers and ensure that the managers are removed
        final List<PeerInfo> peers3 = List.of();
        final List<ConnectionManager> updatedManagers3 = factory.updatePeers(peers3);
        assertNotNull(updatedManagers3);
        assertEquals(0, updatedManagers3.size());
    }

    private static List<Arguments> topologicalVariations() {
        return List.of(Arguments.of(10, 10), Arguments.of(20, 20), Arguments.of(50, 40), Arguments.of(60, 40));
    }
}
