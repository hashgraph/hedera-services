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

package com.swirlds.platform.network.topology;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.NETWORK;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.InboundConnectionManager;
import com.swirlds.platform.network.OutboundConnectionManager;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Pre-builds connection managers for the supplied topology, does not allow changes at runtime
 */
public class ConnectionManagerFactory {
    private static final Logger logger = LogManager.getLogger(ConnectionManagerFactory.class);
    private final NetworkTopology topology;
    private final Map<ConnectionMapping, ConnectionManager> connectionManagers;
    private final OutboundConnectionCreator connectionCreator;

    /**
     * Create a new connectivity manager with the given topology and connection creator
     *
     * @param topology          the network topology
     * @param connectionCreator the connection creator
     */
    public ConnectionManagerFactory(
            @NonNull final NetworkTopology topology, @NonNull final OutboundConnectionCreator connectionCreator) {
        this.topology = Objects.requireNonNull(topology);
        this.connectionCreator = Objects.requireNonNull(connectionCreator);
        // is thread safe because it never changes
        this.connectionManagers = new HashMap<>();
        for (final NodeId neighbor : topology.getNeighbors()) {
            createConnectionManager(neighbor);
        }
    }

    /**
     * returns an updated list of connection managers based on the provided list of peers
     * This is not expected to be a frequently travelled path, so locking is ok
     *
     * @param peers the list of peers to update the connection managers with
     */
    @NonNull
    public synchronized List<ConnectionManager> updatePeers(@NonNull final List<PeerInfo> peers) {
        // Get all nodeIds from the peers list
        final Set<NodeId> peerNodeIds = peers.stream().map(PeerInfo::nodeId).collect(Collectors.toSet());

        // Get all nodeIds from the connectionManagers map
        final Set<NodeId> managerNodeIds =
                connectionManagers.keySet().stream().map(ConnectionMapping::id).collect(Collectors.toSet());

        // create managers for all nodeIds in peers list not in connectionManagers
        final List<NodeId> managersToCreate = peerNodeIds.stream()
                .filter(nodeId -> !managerNodeIds.contains(nodeId))
                .toList();
        managersToCreate.forEach(this::createConnectionManager);

        // Remove managers for all nodeIds in connectionManagers not in peers list
        final List<ConnectionMapping> managersToRemove = connectionManagers.keySet().stream()
                .filter(connectionMapping -> !peerNodeIds.contains(connectionMapping.id()))
                .toList();
        managersToRemove.forEach(connectionManagers::remove);

        return List.copyOf(connectionManagers.values());
    }

    @NonNull
    public ConnectionManager getManager(@NonNull final NodeId id, final boolean outbound) {
        final ConnectionMapping key = new ConnectionMapping(id, outbound);
        return connectionManagers.get(key);
    }

    /**
     * Called when a new connection is established by a peer. After startup, we don't expect this to be called unless
     * there are networking issues. The connection is passed on to the appropriate connection manager if valid.
     *
     * @param newConn
     * 		a new connection that has been established
     */
    public void newConnection(@NonNull final Connection newConn) throws InterruptedException {
        if (!topology.shouldConnectToMe(newConn.getOtherId())) {
            logger.error(EXCEPTION.getMarker(), "Unexpected new connection {}", newConn.getDescription());
            newConn.disconnect();
        }

        final ConnectionMapping key = new ConnectionMapping(newConn.getOtherId(), false);
        final ConnectionManager cs = connectionManagers.get(key);
        if (cs == null) {
            logger.error(EXCEPTION.getMarker(), "Unexpected new connection {}", newConn.getDescription());
            newConn.disconnect();
            return;
        }
        logger.debug(NETWORK.getMarker(), "{} accepted connection from {}", newConn.getSelfId(), newConn.getOtherId());
        try {
            cs.newConnection(newConn);
        } catch (final InterruptedException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Interrupted while handling over new connection {}",
                    newConn.getDescription(),
                    e);
            newConn.disconnect();
            throw e;
        }
    }

    /**
     * Create a new connection manager for the given neighbor
     *
     * @param neighbor the neighbor to create a connection manager for
     */
    private void createConnectionManager(@NonNull final NodeId neighbor) {
        if (topology.shouldConnectToMe(neighbor)) {
            connectionManagers.put(new ConnectionMapping(neighbor, false), new InboundConnectionManager(neighbor));
        }
        if (topology.shouldConnectTo(neighbor)) {
            connectionManagers.put(
                    new ConnectionMapping(neighbor, true), new OutboundConnectionManager(neighbor, connectionCreator));
        }
    }

    private record ConnectionMapping(NodeId id, boolean outbound) {}
}
