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

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.communication.handshake.HandshakeCompletedListenerImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.net.ssl.SSLSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listens on a server socket for incoming connections. All new connections are passed on to the supplied handler.
 */
public class ConnectionServer implements InterruptableRunnable {
    /** number of milliseconds to sleep when a server socket binds fails until trying again */
    private static final int SLEEP_AFTER_BIND_FAILED_MS = 100;
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(ConnectionServer.class);
    /** the port that this server listens on for establishing new connections */
    private final int port;
    /** responsible for creating and binding the server socket */
    private final SocketFactory socketFactory;
    /** handles newly established connections */
    private final Consumer<Socket> newConnectionHandler;
    /** a thread pool used to handle incoming connections */
    private final ExecutorService incomingConnPool;

    private final List<PeerInfo> peerInfoList;
    /**
     * @param threadManager        responsible for managing thread lifecycles
     * @param port                 the port ot use
     * @param socketFactory        responsible for creating new sockets
     * @param newConnectionHandler handles a new connection after it has been created
     * @param peerInfoList         List of all peers
     */
    public ConnectionServer(
            final ThreadManager threadManager,
            final int port,
            final SocketFactory socketFactory,
            final Consumer<Socket> newConnectionHandler,
            @NonNull final List<PeerInfo> peerInfoList) {
        this.port = port;
        this.newConnectionHandler = newConnectionHandler;
        this.socketFactory = socketFactory;
        this.incomingConnPool = Executors.newCachedThreadPool(new ThreadConfiguration(threadManager)
                .setThreadName("sync_server")
                .buildFactory());
        this.peerInfoList = Objects.requireNonNull(peerInfoList);
    }

    @Override
    public void run() throws InterruptedException {
        try (final ServerSocket serverSocket = socketFactory.createServerSocket(port)) {
            listen(serverSocket);
        } catch (final RuntimeException | IOException e) {
            logger.error(EXCEPTION.getMarker(), "Cannot bind ServerSocket", e);
        }
        // if the above fails, sleep a while before trying again
        Thread.sleep(SLEEP_AFTER_BIND_FAILED_MS);
    }

    /**
     * listens for incoming connections until interrupted or socket is closed
     */
    private void listen(final ServerSocket serverSocket) throws InterruptedException {
        // Handle incoming connections
        while (!serverSocket.isClosed()) {
            try {
                final Socket clientSocket = serverSocket.accept(); // listen, waiting until someone connects
                if (clientSocket instanceof final SSLSocket sslSocket) {
                    sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListenerImpl(peerInfoList));
                }
                incomingConnPool.submit(() -> newConnectionHandler.accept(clientSocket));
            } catch (final SocketTimeoutException expectedWithNonZeroSOTimeout) {
                // A timeout is expected, so we won't log it
                if (Thread.currentThread().isInterrupted()) {
                    // since accept() cannot be interrupted, we check the interrupted status on a timeout and throw
                    throw new InterruptedException();
                }
            } catch (final RuntimeException | IOException e) {
                logger.error(EXCEPTION.getMarker(), "SyncServer serverSocket.accept() error", e);
            }
        }
    }
}
