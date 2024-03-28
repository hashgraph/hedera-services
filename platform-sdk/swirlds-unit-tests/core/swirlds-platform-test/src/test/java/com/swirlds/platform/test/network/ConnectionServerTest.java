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

import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.network.SocketConfig_;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.connectivity.TcpFactory;
import com.swirlds.platform.wiring.NoInput;
import java.io.IOException;
import java.net.Socket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectionServerTest {

    @Test
    void listenOverTCP_NoClientConnectsTest() {
        final Configuration configurationNoIpTos =
                new TestConfigBuilder().withValue(SocketConfig_.IP_TOS, "-1").getOrCreateConfig();
        final SocketConfig NO_IP_TOS = configurationNoIpTos.getConfigData(SocketConfig.class);
        final SocketFactory socketFactory = new TcpFactory(NO_IP_TOS, 31_000);

        final ConnectionServer connectionServer = new ConnectionServer(socketFactory);

        final Socket socket = connectionServer.listen(NoInput.getInstance());
        Assertions.assertNull(socket);
        connectionServer.stop();
    }

    @Test
    void listenOverTCP_ClientConnectsTest() throws IOException {
        final int PORT = 40_000;
        final Configuration configurationNoIpTos =
                new TestConfigBuilder().withValue(SocketConfig_.IP_TOS, "-1").getOrCreateConfig();
        final SocketConfig NO_IP_TOS = configurationNoIpTos.getConfigData(SocketConfig.class);
        final SocketFactory serverFactory = new TcpFactory(NO_IP_TOS, PORT);

        final ConnectionServer connectionServer = new ConnectionServer(serverFactory);
        final Thread connServerThread = new Thread(() -> {
            final Socket serverSocket = connectionServer.listen(NoInput.getInstance());
            Assertions.assertTrue(serverSocket.isConnected());
        });
        connServerThread.start();

        final SocketFactory clientFactory = new TcpFactory(NO_IP_TOS, PORT);
        try (final Socket clientSocket = clientFactory.createClientSocket("127.0.0.1", PORT)) {
            Assertions.assertTrue(clientSocket.isConnected());
        }
        connectionServer.stop();
    }
}
