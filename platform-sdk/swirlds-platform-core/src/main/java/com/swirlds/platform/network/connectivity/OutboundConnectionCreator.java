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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.NETWORK;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.TCP_CONNECT_EXCEPTIONS;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Connection;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.SocketConnection;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.connection.NotConnectedConnection;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates outbound connections to the requested peers
 */
public class OutboundConnectionCreator {
    private static final Logger logger = LogManager.getLogger(OutboundConnectionCreator.class);
    private static final byte[] LOCALHOST = new byte[] {127, 0, 0, 1};
    private final NodeId selfId;
    private final SettingsProvider settings;
    private final ConnectionTracker connectionTracker;
    private final SocketFactory socketFactory;
    private final AddressBook addressBook;
    private final boolean doVersionCheck;
    private final SoftwareVersion softwareVersion;

    public OutboundConnectionCreator(
            final NodeId selfId,
            final SettingsProvider settings,
            final ConnectionTracker connectionTracker,
            final SocketFactory socketFactory,
            final AddressBook addressBook,
            final boolean doVersionCheck,
            final SoftwareVersion softwareVersion) {
        this.selfId = selfId;
        this.settings = settings;
        this.connectionTracker = connectionTracker;
        this.socketFactory = socketFactory;
        this.addressBook = addressBook;
        this.doVersionCheck = doVersionCheck;
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
    }

    /**
     * Try to connect to the member with the given ID. If it doesn't work on the first try, give up immediately. Return
     * the connection, or a connection that is not connected if it fails.
     *
     * @param otherId which member to connect to
     * @return the new connection, or a connection that is not connected if it couldn't connect on the first try
     */
    public Connection createConnection(final NodeId otherId) {
        final Address other = addressBook.getAddress(otherId.getId());
        final Address ownAddress = addressBook.getAddress(selfId.getId());
        final int port = other.getConnectPortIpv4(ownAddress);
        final byte[] ip = getConnectAddressIpv4(ownAddress, other);
        final String ipAddress = Address.ipString(ip);

        Socket clientSocket = null;
        SyncOutputStream dos = null;
        SyncInputStream dis = null;

        try {
            clientSocket = socketFactory.createClientSocket(ipAddress, port);

            dos = SyncOutputStream.createSyncOutputStream(
                    clientSocket.getOutputStream(), settings.connectionStreamBufferSize());
            dis = SyncInputStream.createSyncInputStream(
                    clientSocket.getInputStream(), settings.connectionStreamBufferSize());

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

            dos.writeUTF(addressBook.getAddress(selfId.getId()).getNickname());
            dos.flush();

            final int ack = dis.readInt(); // read the ACK for creating the connection
            if (ack != ByteConstants.COMM_CONNECT) { // this is an ACK for creating the connection
                throw new ConnectException("ack is not " + ByteConstants.COMM_CONNECT + ", it is " + ack);
            }
            logger.debug(NETWORK.getMarker(), "`connect` : finished, {} connected to {}", selfId, otherId);

            return SocketConnection.create(selfId, otherId, connectionTracker, true, clientSocket, dis, dos);
        } catch (final SocketTimeoutException | SocketException e) {
            NetworkUtils.close(clientSocket, dis, dos);
            logger.debug(
                    TCP_CONNECT_EXCEPTIONS.getMarker(), "{} failed to connect to {} with error:", selfId, otherId, e);
            // ConnectException (which is a subclass of SocketException) happens when calling someone
            // who isn't running yet. So don't worry about it.
            // Also ignore the other socket-related errors (SocketException) in case it times out while
            // connecting.
        } catch (final IOException e) {
            NetworkUtils.close(clientSocket, dis, dos);
            // log the SSL connection exception which is caused by socket exceptions as warning.
            String formattedException = NetworkUtils.formatException(e);
            logger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "{} failed to connect to {} {}",
                    selfId,
                    otherId,
                    formattedException);
        } catch (final RuntimeException e) {
            NetworkUtils.close(clientSocket, dis, dos);
            logger.debug(EXCEPTION.getMarker(), "{} failed to connect to {}", selfId, otherId, e);
        }

        return NotConnectedConnection.getSingleton();
    }

    /**
     * Find the best way to connect <code>from</code> address <code>to</code> address
     *
     * @param from the address that needs to connect
     * @param to   the address to connect to
     * @return the IP address to connect to
     */
    private byte[] getConnectAddressIpv4(final Address from, final Address to) {
        if (from.isOwnHost() && to.isOwnHost() && settings.useLoopbackIp()) {
            return LOCALHOST;
        } else if (to.isLocalTo(from)) {
            return to.getAddressInternalIpv4();
        } else {
            return to.getAddressExternalIpv4();
        }
    }
}
