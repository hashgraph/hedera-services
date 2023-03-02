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

package com.swirlds.platform.network.topology;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.network.RandomGraph;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A topology that never changes. Can be either unidirectional or bidirectional.
 */
public class StaticTopology implements NetworkTopology {
    private static final long SEED = 0;

    private final NodeId selfId;
    private final int networkSize;
    private final RandomGraph connectionGraph;
    private final boolean unidirectional;

    public StaticTopology(final NodeId selfId, final int networkSize, final int numberOfNeighbors) {
        this(selfId, networkSize, numberOfNeighbors, true);
    }

    public StaticTopology(
            final NodeId selfId, final int networkSize, final int numberOfNeighbors, final boolean unidirectional) {
        this.selfId = selfId;
        this.networkSize = networkSize;
        this.unidirectional = unidirectional;
        this.connectionGraph = new RandomGraph(networkSize, numberOfNeighbors, SEED);
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
        return Arrays.stream(connectionGraph.getNeighbors(selfId.getIdAsInt()))
                .mapToLong(i -> (long) i)
                .mapToObj(NodeId::createMain)
                .filter(filter)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectToMe(final NodeId nodeId) {
        return isNeighbor(nodeId) && (unidirectional || nodeId.getId() < selfId.getId());
    }

    /**
     * Queries the topology on whether this node is my neighbor
     *
     * @param nodeId
     * 		the ID of the node being queried
     * @return true if this node is my neighbor, false if not
     */
    private boolean isNeighbor(final NodeId nodeId) {
        return selfId.sameNetwork(nodeId)
                && nodeId.getId() >= 0
                && nodeId.getId() < networkSize
                && connectionGraph.isAdjacent(selfId.getIdAsInt(), nodeId.getIdAsInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectTo(final NodeId nodeId) {
        return isNeighbor(nodeId) && (unidirectional || nodeId.getId() > selfId.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomGraph getConnectionGraph() {
        return connectionGraph;
    }
}
