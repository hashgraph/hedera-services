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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A bidirectional topology that never changes.
 */
public class StaticTopology implements NetworkTopology {
    private static final long SEED = 0;

    private final NodeId selfId;
    /**
     * Two nodes are neighbors if their node indexes are neighbors in the connection graph.
     */
    private final Set<NodeId> peerNodes;

    private final RandomGraph connectionGraph;

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
            @NonNull final Set<PeerInfo> peers,
            @NonNull final NodeId selfId,
            final int numberOfNeighbors) {
        this.peerNodes = Objects.requireNonNull(
                peers.stream().map(PeerInfo::nodeId).collect(Collectors.toUnmodifiableSet()), "peers must not be null");
        this.selfId = Objects.requireNonNull(selfId, "selfId must not be null");
        this.connectionGraph = new RandomGraph(random, peers.size() + 1, numberOfNeighbors, SEED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<NodeId> getNeighbors() {
        return getNeighbors((nodeId -> true));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<NodeId> getNeighbors(final Predicate<NodeId> filter) {
        return peerNodes;
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
        if (!peerNodes.contains(nodeId)) {
            return false;
        }
        final long selfIndex = selfId.id();
        final long nodeIndex = nodeId.id();
        return connectionGraph.isAdjacent((int) selfIndex, (int) nodeIndex);
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
}
