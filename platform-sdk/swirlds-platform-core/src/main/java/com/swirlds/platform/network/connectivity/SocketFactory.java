/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.swirlds.platform.network.SocketConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

/**
 * Creates, binds and connects server and client sockets
 */
public interface SocketFactory {
    /** The IPv4 address to listen all interface: [0.0.0.0]. */
    byte[] ALL_INTERFACES = new byte[] {0, 0, 0, 0};

    int IP_TOP_MIN = 0;
    int IP_TOP_MAX = 255;

    static boolean isIpTopInRange(final int ipTos) {
        return IP_TOP_MIN <= ipTos && ipTos <= IP_TOP_MAX;
    }

    /**
     * Configures and binds the provided ServerSocket
     *
     * @param serverSocket
     * 		the socket to configure and bind
     * @param socketConfig
     * 		the configuration for the socket
     * @param port
     * 		the TCP port to bind
     * @throws IOException
     * 		if the bind is unsuccessful
     */
    static void configureAndBind(
            @NonNull final ServerSocket serverSocket, @NonNull final SocketConfig socketConfig, final int port)
            throws IOException {
        Objects.requireNonNull(serverSocket);
        Objects.requireNonNull(socketConfig);
        if (isIpTopInRange(socketConfig.ipTos())) {
            // set the IP_TOS option
            serverSocket.setOption(java.net.StandardSocketOptions.IP_TOS, socketConfig.ipTos());
        }
        final InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getByAddress(ALL_INTERFACES), port);
        serverSocket.bind(endpoint); // try to grab a port on this computer
        serverSocket.setReuseAddress(true);
        // do NOT do clientSocket.setSendBufferSize or clientSocket.setReceiveBufferSize
        // because it causes a major bug in certain situations

        serverSocket.setSoTimeout(socketConfig.timeoutServerAcceptConnect());
    }

    /**
     * Configures and connects the provided client Socket
     *
     * @param clientSocket
     * 		the socket to configure and connect
     * @param socketConfig
     * 		the configuration for the socket
     * @param hostname
     * 		the address to connect to
     * @param port
     * 		the TCP port to connect to
     * @throws IOException
     * 		if the connections fails
     */
    static void configureAndConnect(
            @NonNull final Socket clientSocket,
            @NonNull final SocketConfig socketConfig,
            @NonNull final String hostname,
            final int port)
            throws IOException {
        if (isIpTopInRange(socketConfig.ipTos())) {
            // set the IP_TOS option
            clientSocket.setOption(java.net.StandardSocketOptions.IP_TOS, socketConfig.ipTos());
        }

        clientSocket.setSoTimeout(socketConfig.timeoutSyncClientSocket());
        clientSocket.setTcpNoDelay(socketConfig.tcpNoDelay());
        // do NOT do clientSocket.setSendBufferSize or clientSocket.setReceiveBufferSize
        // because it causes a major bug in certain situations
        clientSocket.connect(new InetSocketAddress(hostname, port), socketConfig.timeoutSyncClientConnect());
    }

    /**
     * Create a new ServerSocket, then binds it to the given port on all interfaces
     *
     * @param port
     * 		the port to bind to
     * @return a new server socket
     * @throws IOException
     * 		if the socket cannot be created
     */
    @NonNull
    ServerSocket createServerSocket(final int port) throws IOException;

    /**
     * Create a new Socket, then connect to the given ip and port.
     *
     * @param hostname
     * 		the address to connect to
     * @param port
     * 		the port to connect to
     * @return the new socket
     * @throws IOException
     * 		if the connection cannot be made
     */
    @NonNull
    Socket createClientSocket(@NonNull final String hostname, final int port) throws IOException;
}
