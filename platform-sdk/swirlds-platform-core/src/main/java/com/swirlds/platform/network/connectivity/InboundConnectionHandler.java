/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.SYNC;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.Connection;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.SocketConnection;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Accept inbound connections and executes the platform handshake. This class is thread-safe
 */
public class InboundConnectionHandler {
    private static final Logger logger = LogManager.getLogger(InboundConnectionHandler.class);

    private final ConnectionTracker connectionTracker;
    private final NodeId selfId;
    private final AddressBook addressBook;
    private final InterruptableConsumer<Connection> newConnectionConsumer;
    private final SettingsProvider settings;
    private final boolean doVersionCheck;
    private final SoftwareVersion softwareVersion;

    public InboundConnectionHandler(
            final ConnectionTracker connectionTracker,
            final NodeId selfId,
            final AddressBook addressBook,
            final InterruptableConsumer<Connection> newConnectionConsumer,
            final SettingsProvider settings,
            final boolean doVersionCheck,
            final SoftwareVersion softwareVersion) {
        this.connectionTracker = connectionTracker;
        this.selfId = selfId;
        this.addressBook = addressBook;
        this.newConnectionConsumer = newConnectionConsumer;
        this.settings = settings;
        this.doVersionCheck = doVersionCheck;
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
    }

    /**
     * Authenticate the peer that has just established a new connection and create a {@link Connection}
     *
     * @param clientSocket the newly created socket
     */
    public void handle(final Socket clientSocket) {
        SerializableDataInputStream dis = null;
        SerializableDataOutputStream dos = null;
        long otherId = -1;
        long acceptTime = 0;
        try {
            acceptTime = System.currentTimeMillis();
            clientSocket.setTcpNoDelay(settings.isTcpNoDelay());
            clientSocket.setSoTimeout(settings.getTimeoutSyncClientSocket());

            dis = new SerializableDataInputStream(clientSocket.getInputStream());
            dos = new SerializableDataOutputStream(clientSocket.getOutputStream());

            if (doVersionCheck) {
                dos.writeSerializable(softwareVersion, true);
                dos.flush();

                final SoftwareVersion otherVersion = dis.readSerializable();
                if (otherVersion == null
                        || otherVersion.getClass() != softwareVersion.getClass()
                        || otherVersion.compareTo(softwareVersion) != 0) {
                    throw new IOException("This node has software version " + softwareVersion
                            + " but the other node has software version " + otherVersion + ". Closing connection.");
                }
            }

            final String otherKey = dis.readUTF();

            otherId = addressBook.getId(otherKey);

            dos.writeInt(ByteConstants.COMM_CONNECT); // send an ACK for creating connection
            dos.flush();

            final SyncInputStream sis = SyncInputStream.createSyncInputStream(
                    clientSocket.getInputStream(), settings.connectionStreamBufferSize());
            final SyncOutputStream sos = SyncOutputStream.createSyncOutputStream(
                    clientSocket.getOutputStream(), settings.connectionStreamBufferSize());

            final SocketConnection sc = SocketConnection.create(
                    selfId, NodeId.createMain(otherId), connectionTracker, false, clientSocket, sis, sos);
            newConnectionConsumer.accept(sc);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            String formattedException = NetworkUtils.formatException(e);
            logger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Inbound connection from {} to {} was interrupted: {}",
                    selfId,
                    otherId,
                    formattedException);
            NetworkUtils.close(dis, dos, clientSocket);
        } catch (final IOException e) {
            String formattedException = NetworkUtils.formatException(e);
            logger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Inbound connection from {} to {} had IOException: {}",
                    selfId,
                    otherId,
                    formattedException);
            NetworkUtils.close(dis, dos, clientSocket);
        } catch (final RuntimeException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Inbound connection error, remote IP: {}\n" + "Time from accept to exception: {} ms",
                    clientSocket.getInetAddress().toString(),
                    acceptTime == 0 ? "N/A" : (System.currentTimeMillis() - acceptTime),
                    e);
            logger.error(SYNC.getMarker(), "Listener {} hearing {} had general Exception:", selfId, otherId, e);
            NetworkUtils.close(dis, dos, clientSocket);
        }
    }
}
