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

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.RandomGraph;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A bidirectional topology that never changes.
 */
public class StaticTopology implements NetworkTopology {
    private static final long SEED = 0;

    /** nodes are mapped so lookups are efficient. **/
    private Map<NodeId, Long> peerNodeToIdMap = new HashMap<>();

    /**
     * Two nodes are neighbors if their node indexes are neighbors in the connection graph.
     */
    private final RandomGraph connectionGraph;

    private final NodeId selfId;

    /**
     * Constructor.
     *
     * @param random            a source of randomness, used to chose random neighbors, does not need to be
     *                          cryptographically secure
     * @param peers             the set of peers in the network
     * @param selfId            the ID of this node
     * @param numberOfNeighbors the number of neighbors each node should have
     */
    public StaticTopology(
            @NonNull final Random random,
            @NonNull final List<PeerInfo> peers,
            @NonNull final NodeId selfId,
            final int numberOfNeighbors) {
        this.peerNodeToIdMap = map(peers);
        this.selfId = selfId;
        this.connectionGraph = new RandomGraph(random, peers.size() + 1, numberOfNeighbors, SEED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<NodeId> getNeighbors() {
        return peerNodeToIdMap.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectToMe(final NodeId nodeId) {
        return isNeighbor(nodeId) && nodeId.id() < selfId.id();
    }

    /**
     * Queries the topology on whether this node is my neighbor
     *
     * @param nodeId the ID of the node being queried
     * @return true if this node is my neighbor, false if not
     */
    private boolean isNeighbor(final NodeId nodeId) {
        return peerNodeToIdMap.containsKey(nodeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectTo(final NodeId nodeId) {
        return isNeighbor(nodeId) && nodeId.id() > selfId.id();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomGraph getConnectionGraph() {
        return connectionGraph;
    }

    /**
     * Maps the list of peers to a map of node IDs to their index in the peer list
     * and populates the peerNodesList with the node IDs
     *
     * @param peers the list of peers
     * @return the map of node IDs to their peer Id
     */
    @NonNull
    private Map<NodeId, Long> map(@NonNull final List<PeerInfo> peers) {
        for (final PeerInfo peer : peers) {
            peerNodeToIdMap.put(peer.nodeId(), peer.nodeId().id());
        }
        return peerNodeToIdMap;
    }
}
