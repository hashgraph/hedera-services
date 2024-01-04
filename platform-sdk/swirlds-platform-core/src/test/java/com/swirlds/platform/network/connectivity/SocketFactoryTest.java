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

import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.network.SocketConfig_;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SocketFactoryTest {
    private static final byte[] DATA = {1, 2, 3};
    private static final byte[] BYTES_IP = {127, 0, 0, 1};
    private static final String STRING_IP = "127.0.0.1";
    private static final int PORT = 30_000;
    private static final SocketConfig NO_IP_TOS;
    private static final SocketConfig IP_TOS;
    private static final CryptoConfig CRYPTO_CONFIG;

    static {
        final Configuration configurationNoIpTos =
                new TestConfigBuilder().withValue(SocketConfig_.IP_TOS, "-1").getOrCreateConfig();
        NO_IP_TOS = configurationNoIpTos.getConfigData(SocketConfig.class);

        final Configuration configurationIpTos =
                new TestConfigBuilder().withValue(SocketConfig_.IP_TOS, "100").getOrCreateConfig();
        IP_TOS = configurationIpTos.getConfigData(SocketConfig.class);

        CRYPTO_CONFIG = configurationIpTos.getConfigData(CryptoConfig.class);
    }

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

        final ServerSocket serverSocket = serverFactory.createServerSocket(BYTES_IP, PORT);

        final Thread server = new Thread(() -> {
            try {
                final Socket s = serverSocket.accept();
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
     * 		address book of the network
     * @param keysAndCerts
     * 		keys and certificates to use for testing
     * @throws Throwable
     * 		if anything goes wrong
     */
    @ParameterizedTest
    @MethodSource({"com.swirlds.platform.CryptoArgsProvider#basicTestArgs"})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void tlsFactoryTest(final AddressBook addressBook, final KeysAndCerts[] keysAndCerts) throws Throwable {
        // choose 2 random nodes to test
        final Random random = new Random();
        final int node1 = random.nextInt(addressBook.getSize());
        final int node2 = random.nextInt(addressBook.getSize());

        testSocketsBoth(
                new TlsFactory(keysAndCerts[node1], NO_IP_TOS, CRYPTO_CONFIG),
                new TlsFactory(keysAndCerts[node2], NO_IP_TOS, CRYPTO_CONFIG));
        testSocketsBoth(
                new TlsFactory(keysAndCerts[node1], IP_TOS, CRYPTO_CONFIG),
                new TlsFactory(keysAndCerts[node2], IP_TOS, CRYPTO_CONFIG));
    }

    @Test
    void tcpFactoryTest() throws Throwable {
        testSocketsBoth(new TcpFactory(NO_IP_TOS), new TcpFactory(NO_IP_TOS));
        testSocketsBoth(new TcpFactory(IP_TOS), new TcpFactory(IP_TOS));
    }
}
