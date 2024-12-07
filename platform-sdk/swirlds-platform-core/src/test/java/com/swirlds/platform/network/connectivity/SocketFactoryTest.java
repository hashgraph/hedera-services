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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.network.SocketConfig_;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
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
     * @param roster
     * 		the roster of the network
     * @param keysAndCerts
     * 		keys and certificates to use for testing
     * @throws Throwable
     * 		if anything goes wrong
     */
    @ParameterizedTest
    @MethodSource({"com.swirlds.platform.crypto.CryptoArgsProvider#basicTestArgs"})
    void tlsFactoryTest(@NonNull final Roster roster, @NonNull final Map<NodeId, KeysAndCerts> keysAndCerts)
            throws Throwable {
        assertTrue(roster.rosterEntries().size() > 1, "Address book must contain at least 2 nodes");
        // choose 2 random nodes to test
        final Random random = new Random();
        final List<Integer> nodeIndexes = random.ints(0, roster.rosterEntries().size())
                .distinct()
                .limit(2)
                .boxed()
                .toList();
        final NodeId node1 =
                NodeId.of(roster.rosterEntries().get(nodeIndexes.get(0)).nodeId());
        final NodeId node2 =
                NodeId.of(roster.rosterEntries().get(nodeIndexes.get(1)).nodeId());
        final KeysAndCerts keysAndCerts1 = keysAndCerts.get(node1);
        final KeysAndCerts keysAndCerts2 = keysAndCerts.get(node2);
        final List<PeerInfo> node1Peers = Utilities.createPeerInfoList(roster, node1);
        final List<PeerInfo> node2Peers = Utilities.createPeerInfoList(roster, node2);

        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, node1Peers, keysAndCerts1, TLS_NO_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, node2Peers, keysAndCerts2, TLS_NO_IP_TOS_CONFIG));
        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, node1Peers, keysAndCerts1, TLS_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, node2Peers, keysAndCerts2, TLS_IP_TOS_CONFIG));
    }

    /**
     * Tests the binding of the server socket to the specified interface and port
     *
     * @param roster       the roster of the network
     * @param keysAndCerts keys and certificates to use for testing
     * @throws IOException if the server socket cannot be created
     */
    @ParameterizedTest
    @MethodSource({"com.swirlds.platform.crypto.CryptoArgsProvider#basicTestArgs"})
    void bindInterfaceTest(@NonNull final Roster roster, @NonNull final Map<NodeId, KeysAndCerts> keysAndCerts)
            throws IOException {
        assertTrue(roster.rosterEntries().size() > 1, "Address book must contain at least 2 nodes");
        final NodeId node0 = NodeId.of(roster.rosterEntries().getFirst().nodeId());

        final Configuration defaultConfig = new TestConfigBuilder()
                .withValue(SocketConfig_.BIND_INTERFACE_PORT, "12344")
                .getOrCreateConfig();
        testInterfaceBinding(node0, roster, keysAndCerts, defaultConfig);
        final Configuration ipv4Config = new TestConfigBuilder()
                .withValue(SocketConfig_.BIND_INTERFACE_HOSTNAME, "127.0.0.1")
                .withValue(SocketConfig_.BIND_INTERFACE_PORT, "12345")
                .getOrCreateConfig();
        testInterfaceBinding(node0, roster, keysAndCerts, ipv4Config);
        final Configuration ipv6Config = new TestConfigBuilder()
                .withValue(SocketConfig_.BIND_INTERFACE_HOSTNAME, "0:0:0:0:0:0:0:1")
                .withValue(SocketConfig_.BIND_INTERFACE_PORT, "12346")
                .getOrCreateConfig();
        testInterfaceBinding(node0, roster, keysAndCerts, ipv6Config);
        final Configuration localhostConfig = new TestConfigBuilder()
                .withValue(SocketConfig_.BIND_INTERFACE_HOSTNAME, "localhost")
                .withValue(SocketConfig_.BIND_INTERFACE_PORT, "12347")
                .getOrCreateConfig();
        testInterfaceBinding(node0, roster, keysAndCerts, localhostConfig);
    }

    /**
     * Tests the binding of the server socket to the configured interface and port
     *
     * @param selfId        the ID of the node
     * @param roster        the roster of the network
     * @param keysAndCerts  keys and certificates to use for testing
     * @param configuration the configuration of the network
     * @throws IOException  if the server socket cannot be created
     */
    private void testInterfaceBinding(
            @NonNull final NodeId selfId,
            @NonNull final Roster roster,
            @NonNull final Map<NodeId, KeysAndCerts> keysAndCerts,
            @NonNull final Configuration configuration)
            throws IOException {

        final List<PeerInfo> peers = Utilities.createPeerInfoList(roster, selfId);
        final SocketFactory socketFactory =
                NetworkUtils.createSocketFactory(selfId, peers, keysAndCerts.get(selfId), configuration);

        try (final ServerSocket serverSocket = socketFactory.createServerSocket(PORT)) {
            final SocketConfig socketConfig = configuration.getConfigData(SocketConfig.class);

            if (socketConfig.bindInterfaceHostname().equals(SocketConfig.ALL_IPV4_INTERFACES)) {
                assertTrue(
                        serverSocket.getInetAddress().isAnyLocalAddress(),
                        "ServerSocket should be bound to all interfaces");
            } else if (socketConfig.bindInterfaceHostname().equals("127.0.0.1")
                    || socketConfig.bindInterfaceHostname().equals("localhost")) {
                assertTrue(
                        serverSocket.getInetAddress().isLoopbackAddress(),
                        "ServerSocket should be bound to the loopback interface");
            } else {
                assertEquals(
                        serverSocket.getInetAddress().getHostAddress(),
                        socketConfig.bindInterfaceHostname(),
                        "ServerSocket should be bound to the specified interface");
            }

            if (socketConfig.bindInterfacePort() < 0) {
                assertEquals(PORT, serverSocket.getLocalPort(), "ServerSocket should be bound to the specified port");
            } else {
                assertEquals(
                        socketConfig.bindInterfacePort(),
                        serverSocket.getLocalPort(),
                        "ServerSocket should be bound to the specified port");
            }
        }
    }
}
