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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listens on a server socket for incoming connections. All new connections are passed on to the supplied handler.
 */
public class ConnectionServer {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(ConnectionServer.class);

    private final ServerSocket serverSocket;

    /**
     * @param socketFactory        responsible for creating new sockets
     */
    public ConnectionServer(@NonNull final SocketFactory socketFactory) {
        this.serverSocket = Objects.requireNonNull(createServerSocket(Objects.requireNonNull(socketFactory)));
    }

    /**
     * listens and blocks until either a peer connection is made, or timeout is reached
     * @param noInput dummy input to signal the handler. Not used
     *
     * @return the connected peer's socket or null if timed-out
     * */
    public Socket listen(@NonNull final NoInput noInput) {
        // check for serversocket bound, if not, try to bind
        Socket clientSocket = null;
        try {
            serverSocket.setSoTimeout(50_000);
            clientSocket = serverSocket.accept();
        } catch (final RuntimeException | IOException e) {
            // timeout, no connection. Ignore
        }
        return clientSocket;
    }

    /**
     * Attempts to close this connection, in effect, stopping this server
     */
    public void stop() {
        if (!serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (final IOException e) {
                logger.warn(EXCEPTION.getMarker(), "ConnectionServer unable to close server socket", e);
            }
        }
    }

    /**
     * create the server socket
     *
     * @return the created socket if successful, or null otherwise
     * */
    private static ServerSocket createServerSocket(final SocketFactory socketFactory) {
        ServerSocket ss = null;
        try {
            ss = socketFactory.createServerSocket();
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Error creating server socket", e);
        }
        return ss;
    }
}
