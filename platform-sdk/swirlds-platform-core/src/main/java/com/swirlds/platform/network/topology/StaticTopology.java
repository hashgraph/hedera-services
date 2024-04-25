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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

/**
 * A bidirectional topology that never changes.
 */
public class StaticTopology implements NetworkTopology {
    private static final long SEED = 0;

    private final List<NodeId> peerNodes;

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
        this.peerNodes =
                Objects.requireNonNull(peers.stream().map(PeerInfo::nodeId).toList(), "peers must not be null");
        this.selfIndex = selfIndex;
        this.connectionGraph = new RandomGraph(random, peers.size() + 1, numberOfNeighbors, SEED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NodeId> getNeighbors() {
        return getNeighbors((nodeId -> true));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NodeId> getNeighbors(final Predicate<NodeId> filter) {
        final List<NodeId> peers = peerNodes.stream().toList();
        return Arrays.stream(connectionGraph.getNeighbors(selfIndex))
                .mapToObj(peers::get)
                .filter(filter)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectToMe(final NodeId nodeId) {
        return isNeighbor(nodeId) && peerNodes.indexOf(nodeId) < selfIndex;
    }

    /**
     * Queries the topology on whether this node is my neighbor
     *
     * @param nodeId the ID of the node being queried
     * @return true if this node is my neighbor, false if not
     */
    private boolean isNeighbor(final NodeId nodeId) {
        if (!peerNodes.contains(nodeId)) {
            return false;
        }
        final int nodeIndex = peerNodes.indexOf(nodeId);
        return connectionGraph.isAdjacent(selfIndex, nodeIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectTo(final NodeId nodeId) {
        return isNeighbor(nodeId) && peerNodes.indexOf(nodeId) > selfIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomGraph getConnectionGraph() {
        return connectionGraph;
    }
}
