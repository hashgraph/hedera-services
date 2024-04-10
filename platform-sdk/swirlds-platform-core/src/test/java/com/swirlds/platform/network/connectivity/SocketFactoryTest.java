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

package com.swirlds.platform.network.connectivity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.utility.Pair;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.crypto.CryptoArgsProvider;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.system.address.AddressBook;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SocketFactoryTest extends ConnectivityTestBase {
    private static final int PORT = 30_000;
    private static final byte[] TEST_DATA = new byte[]{1, 2, 3};

    /**
     * Calls {@link #testSockets(SocketFactory, SocketFactory)} twice, to test both factories as server and as client
     *
     * @param socketFactory1
     * 		a factory for both server and client sockets
     * @param socketFactory2
     * 		a factory for both server and client sockets
     */
    private static void testSocketsBoth(final SocketFactory socketFactory1, final SocketFactory socketFactory2)
            throws Throwable {
        testSockets(socketFactory1, socketFactory2);
        testSockets(socketFactory2, socketFactory1);
    }

    /**
     * - establishes a connection using the provided factories
     * - transfers some data
     * - verifies the transferred data is correct
     * - closes the sockets
     *
     * @param serverFactory
     * 		factory to create the server socket
     * @param clientFactory
     * 		factory to create the client socket
     */
    private static void testSockets(final SocketFactory serverFactory, final SocketFactory clientFactory)
            throws Throwable {

        final ServerSocket serverSocket = serverFactory.createServerSocket(PORT);

        final Thread server = createSocketThread(serverSocket, TEST_DATA);
        final AtomicReference<Throwable> threadException = new AtomicReference<>();
        server.setUncaughtExceptionHandler((t, e) -> threadException.set(e));
        server.start();

        final Socket clientSocket = clientFactory.createClientSocket(STRING_IP, PORT);
        clientSocket.getOutputStream().write(TEST_DATA);

        server.join();
        clientSocket.close();

        if (threadException.get() != null) {
            throw threadException.get();
        }
    }

    /**
     * Tests the functionality {@link KeysAndCerts} are currently used for, signing and establishing TLS connections.
     *
     * @param addressBook
     * 		the address book of the network
     * @param keysAndCerts
     * 		keys and certificates to use for testing
     * @throws Throwable
     * 		if anything goes wrong
     */
    @ParameterizedTest
    @MethodSource({"com.swirlds.platform.crypto.CryptoArgsProvider#basicTestArgs"})
    void tlsFactoryTest(final AddressBook addressBook, final Map<NodeId, KeysAndCerts> keysAndCerts) throws Throwable {
        assertTrue(addressBook.getSize() > 1, "Address book must contain at least 2 nodes");
        // choose 2 random nodes to test
        final Random random = new Random();
        final List<Integer> nodeIndexes = random.ints(0, addressBook.getSize())
                .distinct()
                .limit(2)
                .boxed()
                .toList();
        final NodeId node1 = addressBook.getNodeId(nodeIndexes.get(0));
        final NodeId node2 = addressBook.getNodeId(nodeIndexes.get(1));
        final KeysAndCerts keysAndCerts1 = keysAndCerts.get(node1);
        final KeysAndCerts keysAndCerts2 = keysAndCerts.get(node2);

        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, addressBook, keysAndCerts1, TLS_NO_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, addressBook, keysAndCerts2, TLS_NO_IP_TOS_CONFIG));
        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, addressBook, keysAndCerts1, TLS_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, addressBook, keysAndCerts2, TLS_IP_TOS_CONFIG));
    }

    /**
     * Asserts that for sockets A and B that can connect to each other, if A's peer list changes and in effect its trust store
     * is reloaded, B, as well as peer C in the updated peer list can still connect to A, and A to them.
     */
    @Test
    void tlsFactoryTestDynamicPeers() throws Throwable {
        // create addressBook, keysAndCerts
        final Pair<AddressBook, Map<NodeId, KeysAndCerts>> akPair1 = CryptoArgsProvider.getAddressBookWithKeys(2);
        final AddressBook addressBook = akPair1.left();
        final Map<NodeId, KeysAndCerts> keysAndCerts = akPair1.right();
        assertTrue(addressBook.getSize() > 1, "Address book must contain at least 2 nodes");

        // choose 2 random nodes to test connections
        Random random = RandomUtils.getRandomPrintSeed();
        List<Integer> nodeIndexes = random.ints(0, addressBook.getSize())
                .distinct()
                .limit(2)
                .boxed()
                .toList();
        final NodeId node1 = addressBook.getNodeId(nodeIndexes.get(0));
        final NodeId node2 = addressBook.getNodeId(nodeIndexes.get(1));

        final KeysAndCerts keysAndCerts1 = keysAndCerts.get(node1);
        final KeysAndCerts keysAndCerts2 = keysAndCerts.get(node2);
        // create their socket factories
        final SocketFactory socketFactory1 =
                NetworkUtils.createSocketFactory(node1, addressBook, keysAndCerts1, TLS_NO_IP_TOS_CONFIG);
        final SocketFactory socketFactory2 =
                NetworkUtils.createSocketFactory(node2, addressBook, keysAndCerts2, TLS_NO_IP_TOS_CONFIG);
        // test that A and B can talk to each other - A(socketfactory1) -> B(socketfactory2) -> A(socketfactory1)
        testSocketsBoth(socketFactory1, socketFactory2);

        // create a new address book with keys and new set of nodes
        final Pair<AddressBook, Map<NodeId, KeysAndCerts>> akPair2 = CryptoArgsProvider.getAddressBookWithKeys(5);
        final AddressBook updatedAB = akPair2.left();
        final Map<NodeId, KeysAndCerts> updatedKeysAndCerts = akPair2.right();
        assertTrue(updatedAB.getSize() > 1, "Address book must contain at least 2 nodes");
        random = RandomUtils.getRandomPrintSeed();
        nodeIndexes =
                random.ints(0, updatedAB.getSize()).distinct().limit(5).boxed().toList();
        // pick a node for the 3rd connection C. Picking the last one increases the distinctiveness, so we do it
        final NodeId node3 = updatedAB.getNodeId(nodeIndexes.getLast());
        final KeysAndCerts keysAndCerts3 = updatedKeysAndCerts.get(node3);
        // create socket factory for the 3rd node
        final SocketFactory socketFactory3 =
                NetworkUtils.createSocketFactory(node3, updatedAB, keysAndCerts3, TLS_NO_IP_TOS_CONFIG);

        // re-initialize SSLContext for A (socketfactory1) using a new peerList
        socketFactory1.reload(Utilities.createPeerInfoList(updatedAB, node1));
        // doing so, we expect that C(socketfactory3) -> A(socketfactory1) -> C(socketfactory3)
        testSocketsBoth(socketFactory1, socketFactory3);
        // also, B(socketfactory2) -> A(socketfactory1) -> B(socketfactory2)
        testSocketsBoth(socketFactory1, socketFactory2);
    }

    @Test
    void tcpFactoryTest() throws Throwable {
        testSocketsBoth(new TcpFactory(NO_IP_TOS), new TcpFactory(NO_IP_TOS));
        testSocketsBoth(new TcpFactory(IP_TOS), new TcpFactory(IP_TOS));
    }
}
