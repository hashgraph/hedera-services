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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.network.SocketConfig_;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class ConnectivityTestBase {

    protected static final String STRING_IP = "127.0.0.1";
    protected static final SocketConfig NO_IP_TOS;
    protected static final SocketConfig IP_TOS;
    protected static final Configuration TLS_NO_IP_TOS_CONFIG;
    protected static final Configuration TLS_IP_TOS_CONFIG;

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
     * creates a server socket thread for testing purposes
     *
     * @param serverSocket the server socket to listen on
     */
    static Thread createSocketThread(final ServerSocket serverSocket, final byte[] data) {
        return new Thread(() -> {
            try {
                final Socket s = serverSocket.accept();
                final byte[] bytes = s.getInputStream().readNBytes(data.length);
                assertArrayEquals(data, bytes, "Data read from socket must be the same as the data written");
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
    }
}
