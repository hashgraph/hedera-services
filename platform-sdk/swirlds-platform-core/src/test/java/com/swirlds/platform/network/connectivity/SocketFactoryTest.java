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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.network.SocketConfig_;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class SocketFactoryTest {
    private static final byte[] DATA = {1, 2, 3};
    private static final String STRING_IP = "127.0.0.1";
    private static final int PORT = 30_000;
    private static final SocketConfig NO_IP_TOS;
    private static final SocketConfig IP_TOS;
    private static final Configuration TLS_NO_IP_TOS_CONFIG;
    private static final Configuration TLS_IP_TOS_CONFIG;

    static {
        TLS_NO_IP_TOS_CONFIG = new TestConfigBuilder()
                .withValue(SocketConfig_.IP_TOS, "-1")
                .withValue(SocketConfig_.USE_T_L_S, true)
                .getOrCreateConfig();
        TLS_IP_TOS_CONFIG = new TestConfigBuilder()
                .withValue(SocketConfig_.IP_TOS, "100")
                .withValue(SocketConfig_.USE_T_L_S, true)
                .getOrCreateConfig();

        final Configuration configurationNoIpTos =
                new TestConfigBuilder().withValue(SocketConfig_.IP_TOS, "-1").getOrCreateConfig();
        NO_IP_TOS = configurationNoIpTos.getConfigData(SocketConfig.class);

        final Configuration configurationIpTos =
                new TestConfigBuilder().withValue(SocketConfig_.IP_TOS, "100").getOrCreateConfig();
        IP_TOS = configurationIpTos.getConfigData(SocketConfig.class);
    }

    /**
     * Calls {@link #testSockets(SocketFactory, SocketFactory, List)} twice, to test both factories as server and as client
     *
     * @param socketFactory1 a factory for both server and client sockets
     * @param socketFactory2 a factory for both server and client sockets
     * @param peerInfoList a list of peers
     */
    private static void testSocketsBoth(
            final SocketFactory socketFactory1, final SocketFactory socketFactory2, final List<PeerInfo> peerInfoList)
            throws Throwable {
        testSockets(socketFactory1, socketFactory2, peerInfoList);
        testSockets(socketFactory2, socketFactory1, peerInfoList);
    }

    /**
     * - establishes a connection using the provided factories - transfers some data - verifies the transferred data is
     * correct - closes the sockets
     *
     * @param serverFactory factory to create the server socket
     * @param clientFactory factory to create the client socket
     * @param peerInfoList a list of peers
     */
    private static void testSockets(
            final SocketFactory serverFactory, final SocketFactory clientFactory, final List<PeerInfo> peerInfoList)
            throws Throwable {

        final ServerSocket serverSocket = serverFactory.createServerSocket(PORT);

        final Thread server = new Thread(() -> {
            try {
                final Socket s = serverSocket.accept();
                if (s instanceof SSLSocket) {
                    if (peerInfoList.stream().anyMatch(peer -> mockingDetails(peer.signingCertificate())
                            .isMock())) {
                        // we've passed a mocked peer's certificate as an example of an invalid agreement
                        // certificate - it possesses no valid issuer's principal
                        assertNull(Utilities.validateTLSPeer((SSLSocket) s, peerInfoList));
                    } else {
                        assertNotNull(Utilities.validateTLSPeer((SSLSocket) s, peerInfoList));
                    }
                }
                final byte[] bytes = s.getInputStream().readNBytes(DATA.length);
                assertArrayEquals(DATA, bytes, "Data read from socket must be the same as the data written");
                s.close();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            } finally {
                // for some reason, AutoClosable does not seem to close in time, and subsequent tests fail if used
                try {
                    serverSocket.close();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        final AtomicReference<Throwable> threadException = new AtomicReference<>();
        server.setUncaughtExceptionHandler((t, e) -> threadException.set(e));
        server.start();

        final Socket clientSocket = clientFactory.createClientSocket(STRING_IP, PORT);
        clientSocket.getOutputStream().write(DATA);

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

        final Address address1 = addressBook.getAddress(node1);
        final Address address2 = addressBook.getAddress(node2);
        final PeerInfo peer1 = new PeerInfo(
                node1,
                address1.getSelfName(),
                Objects.requireNonNull(address1.getHostnameExternal()),
                Objects.requireNonNull(address1.getSigCert()));
        final PeerInfo peer2 = new PeerInfo(
                node2,
                address2.getSelfName(),
                Objects.requireNonNull(address2.getHostnameExternal()),
                Objects.requireNonNull(address2.getSigCert()));
        final List<PeerInfo> peerInfoList = List.of(peer1, peer2);

        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, addressBook, keysAndCerts1, TLS_NO_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, addressBook, keysAndCerts2, TLS_NO_IP_TOS_CONFIG),
                peerInfoList);
        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, addressBook, keysAndCerts1, TLS_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, addressBook, keysAndCerts2, TLS_IP_TOS_CONFIG),
                peerInfoList);

        final PeerInfo peer3 = new PeerInfo(
                node1,
                address1.getSelfName(),
                Objects.requireNonNull(address1.getHostnameExternal()),
                mock(X509Certificate.class));
        Mockito.when(((X509Certificate) peer3.signingCertificate()).getSubjectX500Principal())
                .thenReturn(mock(X500Principal.class));

        final List<PeerInfo> peerInfoListNonMatchingCertPeers = List.of(peer3);
        testSocketsBoth(
                NetworkUtils.createSocketFactory(node1, addressBook, keysAndCerts1, TLS_NO_IP_TOS_CONFIG),
                NetworkUtils.createSocketFactory(node2, addressBook, keysAndCerts2, TLS_NO_IP_TOS_CONFIG),
                peerInfoListNonMatchingCertPeers);
    }

    @Test
    void tcpFactoryTest() throws Throwable {
        testSocketsBoth(new TcpFactory(NO_IP_TOS), new TcpFactory(NO_IP_TOS), null);
        testSocketsBoth(new TcpFactory(IP_TOS), new TcpFactory(IP_TOS), null);
    }
}
