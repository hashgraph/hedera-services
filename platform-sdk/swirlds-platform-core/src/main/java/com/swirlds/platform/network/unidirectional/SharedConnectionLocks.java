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

package com.swirlds.platform.network.unidirectional;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.locks.AutoClosableResourceLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.common.threading.locks.locked.MaybeLockedResource;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for all outbound node connections. Establishes outbound connections to nodes. Ensures only a single
 * thread uses a connection at any given time.
 */
public class SharedConnectionLocks {
    private static final MaybeLockedResource<ConnectionManager> NOT_ACQUIRED = new MaybeLockedResource<>() {

        @Override
        public void close() {
            // do nothing
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ConnectionManager getResource() {
            throw new IllegalStateException("Cannot get resource if the lock is not obtained");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setResource(ConnectionManager resource) {
            throw new IllegalStateException("Cannot set resource if the lock is not obtained");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isLockAcquired() {
            return false;
        }
    };

    private final NetworkTopology topology;
    private final Map<Long, AutoClosableResourceLock<ConnectionManager>> locks;

    public SharedConnectionLocks(final NetworkTopology topology, final StaticConnectionManagers suppliers) {
        this.topology = topology;
        this.locks = new ConcurrentHashMap<>();
        for (final NodeId peerId : topology.getNeighbors()) {
            final ConnectionManager supplier = suppliers.getManager(peerId, true);
            Objects.requireNonNull(supplier);
            locks.put(peerId.getId(), Locks.createResourceLock(supplier));
        }
    }

    /**
     * Returns a {@link MaybeLockedResource} for the {@link Connection} to the member with the given ID. If the lock
     * is
     * currently being held, the lock and connection will not be acquired. If the connection isn't currently
     * working,
     * then it will NOT try to connect to it.
     *
     * @param nodeId
     * 		the ID of the member whose connection we need
     * @return the connection {@link MaybeLockedResource}, which may or may not be acquired
     */
    public MaybeLockedResource<ConnectionManager> tryLockConnection(final NodeId nodeId) {
        if (!topology.shouldConnectTo(nodeId)) {
            return NOT_ACQUIRED;
        }
        return locks.get(nodeId.getId()).tryLock();
    }

    /**
     * Blocks forever until a lock is acquired for this connection, the lock ensures only one thread uses the
     * connection
     * at a time. Once it is acquired, it will check the connection. If the connection isn't currently working,
     * then it
     * will try just once to connect to it. The lock won't be acquired if there shouldn't be a connection to that
     * member.
     *
     * @param nodeId
     * 		the connection ID of the member to get the connection to
     * @return the locked connection
     */
    public LockedResource<ConnectionManager> lockConnectIfNeeded(final NodeId nodeId) {
        if (!topology.shouldConnectTo(nodeId)) {
            return NOT_ACQUIRED;
        }
        return locks.get(nodeId.getId()).lock();
    }
}
