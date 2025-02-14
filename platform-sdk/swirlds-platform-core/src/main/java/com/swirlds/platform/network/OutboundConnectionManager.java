// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.network;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.locks.AutoClosableResourceLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.platform.network.connection.NotConnectedConnection;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;

/**
 * Manages a connection that is initiated by this node. If the connection in use is broken, it will try to establish a
 * new one.
 */
public class OutboundConnectionManager implements ConnectionManager {
    private final NodeId peerId;
    private final OutboundConnectionCreator connectionCreator;
    /** the current connection in use, initially not connected. there is no synchronization on this variable */
    private Connection currentConn = NotConnectedConnection.getSingleton();
    /** locks the connection managed by this instance */
    private final AutoClosableResourceLock<Connection> lock = Locks.createResourceLock(currentConn);

    public OutboundConnectionManager(final NodeId peerId, final OutboundConnectionCreator connectionCreator) {
        this.peerId = peerId;
        this.connectionCreator = connectionCreator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection waitForConnection() {
        try (final LockedResource<Connection> resource = lock.lock()) {
            while (!resource.getResource().connected()) {
                resource.getResource().disconnect();
                resource.setResource(connectionCreator.createConnection(peerId));
            }
            currentConn = resource.getResource();
        }
        return currentConn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() {
        return currentConn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newConnection(final Connection connection) {
        throw new UnsupportedOperationException("Does not accept connections");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOutbound() {
        return true;
    }
}
