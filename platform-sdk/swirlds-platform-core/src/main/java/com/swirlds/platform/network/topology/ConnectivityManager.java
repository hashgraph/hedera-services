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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Pre-builds connection managers for the supplied topology, does not allow changes at runtime
 */
public class ConnectivityManager {
    private static final Logger logger = LogManager.getLogger(ConnectivityManager.class);
    private final NetworkTopology topology;
    private final OutboundConnectionCreator connectionCreator;
    private Map<ConnectionMapping, ConnectionManager> connectionManagers;

    /** holds the nodeIds of connection managers for fast lookup*/
    private final Set<NodeId> nodeIdsOfConnectionManagers = new HashSet<>();

    /**
     * Create a new connectivity manager with the given topology and connection creator
     *
     * @param topology          the network topology
     * @param connectionCreator the connection creator
     */
    public ConnectivityManager(
            @NonNull final NetworkTopology topology, @NonNull final OutboundConnectionCreator connectionCreator) {
        this.topology = Objects.requireNonNull(topology);
        this.connectionCreator = Objects.requireNonNull(connectionCreator);

        this.connectionManagers = createConnectionManagers();
    }

    /**
     * returns an updated list of connection managers based on the provided list of peers
     * This is not expected to be a frequently travelled path, so locking is ok
     *
     * @param peers the list of peers to update the connection managers with
     */
    @NonNull
    public synchronized List<ConnectionManager> updatePeers(@NonNull final List<PeerInfo> peers) {
        Objects.requireNonNull(peers, "peers cannot be null");

        final Set<NodeId> nodeIds = new HashSet<>();
        // Get all nodeIds from the peers list
        peers.forEach(peer -> {
            // create managers for all incoming nodeIds we're not already aware of
            if (!nodeIdsOfConnectionManagers.contains(peer.nodeId())) {
                createConnectionMapping(peer.nodeId());
            }
            nodeIds.add(peer.nodeId());
        });

        // Remove any connection managers that are no longer needed
        connectionManagers.keySet().removeIf(connectionMapping -> !nodeIds.contains(connectionMapping.id()));
        nodeIdsOfConnectionManagers.removeIf(nodeId -> !nodeIds.contains(nodeId));

        return List.copyOf(connectionManagers.values());
    }

    /**
     * Get the connection manager for the given node id
     *
     * @param id       the node id
     * @param outbound whether the connection manager is for outbound connections
     * @return the connection manager for the given node id
     */
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
     * creates connection managers for all neighbors in the topology
     * @return a map of connection managers
     */
    @NonNull
    private Map<ConnectionMapping, ConnectionManager> createConnectionManagers() {
        final Set<NodeId> neighbors = topology.getNeighbors();
        this.connectionManagers = HashMap.newHashMap(neighbors.size());
        neighbors.forEach(this::createConnectionMapping);

        return connectionManagers;
    }

    /**
     * Create a new connection manager for the given neighbor
     *
     * @param neighbor the neighbor to create a connection manager for
     */
    private void createConnectionMapping(@NonNull final NodeId neighbor) {
        if (topology.shouldConnectToMe(neighbor)) {
            connectionManagers.put(new ConnectionMapping(neighbor, false), new InboundConnectionManager(neighbor));
            nodeIdsOfConnectionManagers.add(neighbor);
        }
        if (topology.shouldConnectTo(neighbor)) {
            connectionManagers.put(
                    new ConnectionMapping(neighbor, true), new OutboundConnectionManager(neighbor, connectionCreator));
            nodeIdsOfConnectionManagers.add(neighbor);
        }
    }

    /**
     * A simple record to hold a node id and whether the connection manager is for outbound connections
     */
    private record ConnectionMapping(NodeId id, boolean outbound) {}
}
