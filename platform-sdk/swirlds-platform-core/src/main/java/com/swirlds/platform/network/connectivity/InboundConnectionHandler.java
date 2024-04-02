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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.SOCKET_EXCEPTIONS;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.gossip.sync.SyncInputStream;
import com.swirlds.platform.gossip.sync.SyncOutputStream;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.network.SocketConnection;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Accept inbound connections and executes the platform handshake. This class is thread-safe
 */
public class InboundConnectionHandler {
    private static final Logger logger = LogManager.getLogger(InboundConnectionHandler.class);
    private static final RateLimitedLogger rateLimitedLogger =
            new RateLimitedLogger(logger, Time.getCurrent(), Duration.ofMinutes(2));

    private final ConnectionTracker connectionTracker;
    private final NodeId selfId;
    private final InterruptableConsumer<Connection> newConnectionConsumer;
    private final SocketConfig socketConfig;
    /** Rate Limited Logger for SocketExceptions */
    private final RateLimitedLogger socketExceptionLogger;

    private final PlatformContext platformContext;

    public InboundConnectionHandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final ConnectionTracker connectionTracker,
            @NonNull final NodeId selfId,
            @NonNull final InterruptableConsumer<Connection> newConnectionConsumer,
            @NonNull final Time time) {
        this.platformContext = Objects.requireNonNull(platformContext);
        this.connectionTracker = Objects.requireNonNull(connectionTracker);
        this.selfId = Objects.requireNonNull(selfId);
        this.newConnectionConsumer = Objects.requireNonNull(newConnectionConsumer);
        Objects.requireNonNull(time);
        this.socketExceptionLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
        this.socketConfig = platformContext.getConfiguration().getConfigData(SocketConfig.class);
    }

    /**
     * Authenticate the peer that has just established a new connection and create a {@link Connection}
     *
     * @param clientSocket the newly created socket
     */
    public void handle(final Socket clientSocket, final List<PeerInfo> peerInfoList) {
        long acceptTime = 0;
        try {
            acceptTime = System.currentTimeMillis();
            clientSocket.setTcpNoDelay(socketConfig.tcpNoDelay());
            clientSocket.setSoTimeout(socketConfig.timeoutSyncClientSocket());

            PeerInfo connectedPeer = null;
            if (clientSocket instanceof final SSLSocket sslSocket) {
                connectedPeer = getConnectedPeer(sslSocket, peerInfoList);
            }

            final SyncInputStream sis = SyncInputStream.createSyncInputStream(
                    platformContext, clientSocket.getInputStream(), socketConfig.bufferSize());
            final SyncOutputStream sos = SyncOutputStream.createSyncOutputStream(
                    platformContext, clientSocket.getOutputStream(), socketConfig.bufferSize());

            final NodeId otherId = Objects.requireNonNull(connectedPeer).nodeId();

            final SocketConnection sc = SocketConnection.create(
                    selfId,
                    otherId,
                    connectionTracker,
                    false,
                    clientSocket,
                    sis,
                    sos,
                    platformContext.getConfiguration());
            newConnectionConsumer.accept(sc);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            final String formattedException = NetworkUtils.formatException(e);
            logger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Inbound connection from {} to {} was interrupted: {}",
                    "unknown",
                    selfId,
                    formattedException);
            NetworkUtils.close(clientSocket);
        } catch (final IOException e) {
            final String formattedException = NetworkUtils.formatException(e);
            socketExceptionLogger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Inbound connection from {} to {} had IOException: {}",
                    "unknown",
                    selfId,
                    formattedException);
            NetworkUtils.close(clientSocket);
        } catch (final RuntimeException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Inbound connection error, remote IP: {}\n" + "Time from accept to exception: {} ms",
                    clientSocket.getInetAddress().toString(),
                    acceptTime == 0 ? "N/A" : (System.currentTimeMillis() - acceptTime),
                    e);
            NetworkUtils.close(clientSocket);
        }
    }

    private PeerInfo getConnectedPeer(@NonNull final SSLSocket sslSocket, @NonNull final List<PeerInfo> peers) {
        PeerInfo peer = null;
        try {
            peer = NetworkUtils.identifyTlsPeer(sslSocket.getSession().getPeerCertificates(), peers);
            if (peer == null) {
                sslSocket.close();
            }
        } catch (final SSLPeerUnverifiedException e) {
            rateLimitedLogger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Attempt to obtain certificate from an unverified peer {}:{} threw exception {}",
                    sslSocket.getInetAddress(),
                    sslSocket.getPort(),
                    e.getMessage());
        } catch (final IOException e) {
            rateLimitedLogger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Attempt to close connection from {}:{} threw IO exception {}",
                    sslSocket.getInetAddress(),
                    sslSocket.getPort(),
                    e.getMessage());
        }
        return peer;
    }
}
