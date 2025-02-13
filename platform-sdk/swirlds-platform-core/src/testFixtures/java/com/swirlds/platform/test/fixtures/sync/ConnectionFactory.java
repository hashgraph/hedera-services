/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.sync;

import com.swirlds.base.utility.Pair;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.Connection;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

@FunctionalInterface
public interface ConnectionFactory {
    int DEFAULT_CONNECTION_BUFFER_SIZE = 8 * 1024;

    /**
     * Creates a new pair of {@link Connection} instances.
     */
    static Pair<Connection, Connection> createLocalConnections(final NodeId nodeA, final NodeId nodeB)
            throws IOException {
        final PipedInputStream inputStreamA = new PipedInputStream();
        // Bob sends information on outputStreamB which Alice receives on inputStreamA
        final PipedOutputStream outputStreamB = new PipedOutputStream(inputStreamA);

        final PipedInputStream inputStreamB = new PipedInputStream();
        // Alice sends information on outputStreamA which Bob receives on inputStreamB
        final PipedOutputStream outputStreamA = new PipedOutputStream(inputStreamB);

        final LocalConnection callerConnection =
                new LocalConnection(nodeA, nodeB, inputStreamA, outputStreamA, DEFAULT_CONNECTION_BUFFER_SIZE, true);
        final LocalConnection listenerConnection =
                new LocalConnection(nodeB, nodeA, inputStreamB, outputStreamB, DEFAULT_CONNECTION_BUFFER_SIZE, false);
        return Pair.of(callerConnection, listenerConnection);
    }

    static Pair<Connection, Connection> createSocketConnections(final NodeId nodeA, final NodeId nodeB)
            throws IOException {
        final int bufferSize = 128;
        final String listenIP = "127.0.0.1";
        final int listenPort = 30123;
        final int timeout = 400;

        // open server socket
        final ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(listenIP, listenPort));
        serverSocket.setSoTimeout(timeout);

        // wait for the client
        final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
        final Thread thread = new Thread(() -> {
            try {
                final Socket socket = serverSocket.accept();
                acceptedSocket.set(socket);
                socket.setSoTimeout(timeout);
                socket.setSendBufferSize(bufferSize);
                socket.setReceiveBufferSize(bufferSize);
                serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();

        final Socket clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(listenIP, listenPort), timeout);
        clientSocket.setSoTimeout(timeout);
        clientSocket.setSendBufferSize(bufferSize);
        clientSocket.setReceiveBufferSize(bufferSize);

        try {
            thread.join(timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (thread.isAlive()) {
            throw new RuntimeException("Timed out waiting for the server socket thread to finish");
        }

        final LocalConnection callerConnection = new LocalConnection(
                nodeA, nodeB, clientSocket.getInputStream(), clientSocket.getOutputStream(), bufferSize, true);
        final LocalConnection listenerConnection = new LocalConnection(
                nodeB,
                nodeA,
                acceptedSocket.get().getInputStream(),
                acceptedSocket.get().getOutputStream(),
                bufferSize,
                false);
        return Pair.of(callerConnection, listenerConnection);
    }

    Pair<Connection, Connection> createConnections(final NodeId nodeA, final NodeId nodeB) throws IOException;
}
