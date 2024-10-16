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

import com.swirlds.common.AddressBook;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.PeerInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SocketFactoryTest extends ConnectivityTestBase {
    private static final int PORT = 30_000;

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

        final Thread serverThread = createSocketThread(serverSocket);
        serverThread.start();
        final AtomicReference<Throwable> threadException = new AtomicReference<>();
        serverThread.setUncaughtExceptionHandler((t, e) -> threadException.set(e));

        final Socket clientSocket = clientFactory.createClientSocket(STRING_IP, PORT);
        clientSocket.getOutputStream().write(TEST_DATA);

        serverThread.join();
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
    void tlsFactoryTest(@NonNull final AddressBook addressBook, @NonNull final Map<NodeId, KeysAndCerts> keysAndCerts)
            throws Throwable {
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
        final List<PeerInfo> node1Peers = Utilities.createPeerInfoList(addressBook, node1);
        final List<PeerInfo> node2Peers = Utilities.createPeerInfoList(addressBook, node2);

        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, node1Peers, keysAndCerts1, TLS_NO_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, node2Peers, keysAndCerts2, TLS_NO_IP_TOS_CONFIG));
        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, node1Peers, keysAndCerts1, TLS_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, node2Peers, keysAndCerts2, TLS_IP_TOS_CONFIG));
    }
}
