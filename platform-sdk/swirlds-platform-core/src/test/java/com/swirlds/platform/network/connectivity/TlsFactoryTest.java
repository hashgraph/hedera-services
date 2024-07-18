/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.connectivity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.crypto.AddressBookAndCerts;
import com.swirlds.platform.crypto.CryptoArgsProvider;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The tests in this class are unidirectional server socket tests. For bidirectional tests, see
 * {@link SocketFactoryTest}
 */
class TlsFactoryTest extends ConnectivityTestBase {
    private static final int PORT = 34_000;

    private static SocketFactory socketFactoryA;
    private static SocketFactory socketFactoryC;
    private static Socket clientSocketB;
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private final AtomicBoolean closeSeverConnection = new AtomicBoolean(false);
    List<PeerInfo> peersA;
    /**
     * Set up the test by creating the address book, keys and certs, and the socket factories for nodes A and B. The
     * base case is that the client socket of a node B can connect to the server socket of another node A. Subsequent
     * tests verify the different behaviours of a third node C
     */
    @BeforeEach
    void setUp() throws Throwable {
        // create addressBook, keysAndCerts
        final AddressBookAndCerts addressBookAndCerts = CryptoArgsProvider.loadAddressBookWithKeys(2);
        final AddressBook addressBook = addressBookAndCerts.addressBook();
        final Map<NodeId, KeysAndCerts> keysAndCerts = addressBookAndCerts.nodeIdKeysAndCertsMap();
        assertTrue(addressBook.getSize() > 1, "Address book must contain at least 2 nodes");

        // choose 2 nodes to test connections
        final NodeId nodeA = addressBook.getNodeId(0);
        final NodeId nodeB = addressBook.getNodeId(1);

        peersA = Utilities.createPeerInfoList(addressBook, nodeA);
        final List<PeerInfo> peersB = Utilities.createPeerInfoList(addressBook, nodeB);

        // create their socket factories
        socketFactoryA = NetworkUtils.createSocketFactory(nodeA, peersA, keysAndCerts.get(nodeA), TLS_NO_IP_TOS_CONFIG);
        final SocketFactory socketFactoryB =
                NetworkUtils.createSocketFactory(nodeB, peersB, keysAndCerts.get(nodeB), TLS_NO_IP_TOS_CONFIG);

        // test that B can talk to A - A(serverSocket) -> B(clientSocket1)
        serverSocket = socketFactoryA.createServerSocket(PORT);
        serverThread = createSocketThread(serverSocket, closeSeverConnection);

        clientSocketB = socketFactoryB.createClientSocket(STRING_IP, PORT);
        testSocket(serverThread, clientSocketB);
        Assertions.assertFalse(serverSocket.isClosed());

        // create a new address book with keys and new set of nodes
        final AddressBookAndCerts updatedAddressBookAndCerts = CryptoArgsProvider.loadAddressBookWithKeys(6);
        final AddressBook updatedAddressBook = updatedAddressBookAndCerts.addressBook();
        final Address address = addressBook.getAddress(nodeA).copySetNodeId(updatedAddressBook.getNextNodeId());
        updatedAddressBook.add(address); // ensure node A is in new addressBook
        final Map<NodeId, KeysAndCerts> updatedKeysAndCerts = updatedAddressBookAndCerts.nodeIdKeysAndCertsMap();
        assertTrue(updatedAddressBook.getSize() > 1, "Address book must contain at least 2 nodes");

        peersA = Utilities.createPeerInfoList(updatedAddressBook, nodeA); // Peers of A as in updated addressBook

        // pick a node for the 3rd connection C.
        final NodeId nodeC = updatedAddressBook.getNodeId(4);
        final List<PeerInfo> peersC = Utilities.createPeerInfoList(updatedAddressBook, nodeC);
        socketFactoryC =
                NetworkUtils.createSocketFactory(nodeC, peersC, updatedKeysAndCerts.get(nodeC), TLS_NO_IP_TOS_CONFIG);
    }

    /**
     * Asserts that for sockets A and B that can connect to each other, if A's peer list changes and in effect its trust
     * store reloaded, B, as well as a new peer C in the updated peer list can both connect to A.
     */
    @Test
    void tlsFactoryRefreshTest() throws Throwable {
        // we expect that C can't talk to A yet, as C's certificate is not yet in A's trust store
        assertThrows(IOException.class, () -> socketFactoryC.createClientSocket(STRING_IP, PORT));
        // re-initialize SSLContext for A using a new peer list which contains C
        socketFactoryA.reload(peersA);
        // now, we expect that C can talk to A
        final Socket clientSocketC = socketFactoryC.createClientSocket(STRING_IP, PORT);
        testSocket(serverThread, clientSocketC);
        // also, B can still talk to A
        testSocket(serverThread, clientSocketB);

        // we're done
        closeSeverConnection.set(true);
        serverThread.join();
        Assertions.assertTrue(serverSocket.isClosed());
    }
}
