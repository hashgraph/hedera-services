/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.platform.SettingsProvider;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Creates, binds and connects server and client sockets
 */
public interface SocketFactory {
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
     * @param settings
     * 		the settings for configuration
     * @param ipAddress
     * 		the IP address to bind
     * @param port
     * 		the TCP port to bind
     * @throws IOException
     * 		if the bind is unsuccessful
     */
    static void configureAndBind(
            final ServerSocket serverSocket, final SettingsProvider settings, final byte[] ipAddress, final int port)
            throws IOException {
        if (isIpTopInRange(settings.getSocketIpTos())) {
            // set the IP_TOS option
            serverSocket.setOption(java.net.StandardSocketOptions.IP_TOS, settings.getSocketIpTos());
        }
        InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getByAddress(ipAddress), port);
        serverSocket.bind(endpoint); // try to grab a port on this computer
        serverSocket.setReuseAddress(true);
        // do NOT do clientSocket.setSendBufferSize or clientSocket.setReceiveBufferSize
        // because it causes a major bug in certain situations

        serverSocket.setSoTimeout(settings.getTimeoutServerAcceptConnect());
    }

    /**
     * Configures and connects the provided Socket
     *
     * @param clientSocket
     * 		the socket to configure and connect
     * @param settings
     * 		the settings for configuration
     * @param ipAddress
     * 		the IP address to connect to
     * @param port
     * 		the TCP port to connect to
     * @throws IOException
     * 		if the connections fails
     */
    static void configureAndConnect(
            final Socket clientSocket, final SettingsProvider settings, final String ipAddress, final int port)
            throws IOException {
        if (isIpTopInRange(settings.getSocketIpTos())) {
            // set the IP_TOS option
            clientSocket.setOption(java.net.StandardSocketOptions.IP_TOS, settings.getSocketIpTos());
        }

        clientSocket.setSoTimeout(settings.getTimeoutSyncClientSocket());
        clientSocket.setTcpNoDelay(settings.isTcpNoDelay());
        // do NOT do clientSocket.setSendBufferSize or clientSocket.setReceiveBufferSize
        // because it causes a major bug in certain situations
        clientSocket.connect(new InetSocketAddress(ipAddress, port), settings.getTimeoutSyncClientConnect());
    }

    /**
     * Create a new ServerSocket, then binds it to the given ip and port.
     * <p>
     *
     * @param ipAddress
     * 		the ip address to bind to
     * @param port
     * 		the port to bind to
     * @return a new server socket
     * @throws IOException
     * 		if the socket cannot be created
     */
    ServerSocket createServerSocket(final byte[] ipAddress, final int port) throws IOException;

    /**
     * Create a new Socket, then connect to the given ip and port.
     *
     * @param ipAddress
     * 		the ip address to connect to
     * @param port
     * 		the port to connect to
     * @return the new socket
     * @throws IOException
     * 		if the connection cannot be made
     */
    Socket createClientSocket(final String ipAddress, final int port) throws IOException;
}
