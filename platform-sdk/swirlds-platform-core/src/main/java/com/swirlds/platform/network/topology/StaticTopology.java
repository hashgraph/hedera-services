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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A bidirectional topology that never changes.
 */
public class StaticTopology implements NetworkTopology {
    private static final long SEED = 0;

    private final List<NodeId> peers = new ArrayList<>();

    /** nodes are mapped so lookups are efficient. **/
    private Map<NodeId, Integer> peerNodeToIdMap = new HashMap<>();

    /**
     * Two nodes are neighbors if their node indexes are neighbors in the connection graph.
     */
    private final RandomGraph connectionGraph;

    private final int selfIndex;

    /**
     * Constructor.
     *
     * @param random            a source of randomness, used to chose random neighbors, does not need to be
     *                          cryptographically secure
     * @param peers             the set of peers in the network
     * @param selfIndex         the index of this node in the address book
     * @param numberOfNeighbors the number of neighbors each node should have
     */
    public StaticTopology(
            @NonNull final Random random,
            @NonNull final List<PeerInfo> peers,
            final int selfIndex,
            final int numberOfNeighbors) {
        this.peerNodeToIdMap = map(peers);
        this.selfIndex = selfIndex;
        this.connectionGraph = new RandomGraph(random, peers.size() + 1, numberOfNeighbors, SEED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NodeId> getNeighbors() {
        return peers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectToMe(final NodeId nodeId) {
        final int nodeIndex = getIndexOfNodeId(nodeId);
        return isNeighbor(nodeId) && nodeIndex < selfIndex;
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
        final int nodeIndex = getIndexOfNodeId(nodeId);
        return isNeighbor(nodeId) && nodeIndex > selfIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomGraph getConnectionGraph() {
        return connectionGraph;
    }

    /**
     * Returns the index of the given node, which must not be the self index
     * or -1 if the node is not in the peer list
     *
     * @param nodeId the node ID
     * @return the index of the node in the peer list
     */
    private int getIndexOfNodeId(@NonNull final NodeId nodeId) {
        final Integer index = peerNodeToIdMap.get(nodeId);
        if (index == null) {
            return -1;
        }
        return selfIndex == index ? index + 1 : index;
    }

    /**
     * Maps the list of peers to a map of node IDs to their index in the peer list
     * and populates the peerNodesList with the node IDs
     *
     * @param peers the list of peers
     * @return the map of node IDs to their index in the peer list
     */
    @NonNull
    private Map<NodeId, Integer> map(@NonNull final List<PeerInfo> peers) {
        for (int i = 0; i < peers.size(); i++) {
            final PeerInfo peer = peers.get(i);
            peerNodeToIdMap.put(peer.nodeId(), i);
            this.peers.add(peer.nodeId());
        }
        return peerNodeToIdMap;
    }
}
