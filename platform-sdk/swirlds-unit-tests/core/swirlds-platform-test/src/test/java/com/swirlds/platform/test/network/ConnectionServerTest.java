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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.SocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectionServerTest {
    @Test
    void createConnectionTest() throws IOException, InterruptedException {
        final Socket socket = mock(Socket.class);
        final ServerSocket serverSocket = mock(ServerSocket.class);
        final AtomicBoolean serverSocketClosed = new AtomicBoolean(false);
        doAnswer(i -> serverSocketClosed.get()).when(serverSocket).isClosed();
        doAnswer(i -> {
                    // unbind the socket after calling accept
                    serverSocketClosed.set(true);
                    return socket;
                })
                .when(serverSocket)
                .accept();
        final SocketFactory socketFactory = mock(SocketFactory.class);
        doAnswer(i -> serverSocket).when(socketFactory).createServerSocket(anyInt());
        final AtomicReference<Socket> connectionHandler = new AtomicReference<>(null);

        final ConnectionServer server =
                new ConnectionServer(getStaticThreadManager(), 0, socketFactory, connectionHandler::set);

        server.run();
        Assertions.assertSame(
                socket,
                connectionHandler.get(),
                "the socket provided by accept() should have been passed to the connection handler");

        // test interrupt
        serverSocketClosed.set(false);
        doAnswer(i -> {
                    throw new SocketTimeoutException();
                })
                .when(serverSocket)
                .accept();
        Thread.currentThread().interrupt();
        Assertions.assertThrows(InterruptedException.class, server::run);
    }
}
