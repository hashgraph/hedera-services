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
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.network.RandomGraph;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A topology that never changes. Can be either unidirectional or bidirectional.
 */
public class StaticTopology implements NetworkTopology {
    private static final long SEED = 0;

    private final NodeId selfId;
    /**
     * Two nodes are neighbors if their indexes in the address book are neighbors in the connection graph.
     */
    private final AddressBook addressBook;

    private final RandomGraph connectionGraph;
    private final boolean unidirectional;

    public StaticTopology(
            @NonNull final AddressBook addressBook, @NonNull final NodeId selfId, final int numberOfNeighbors) {
        this(addressBook, selfId, numberOfNeighbors, true);
    }

    public StaticTopology(
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            final int numberOfNeighbors,
            final boolean unidirectional) {
        this.addressBook = Objects.requireNonNull(addressBook, "addressBook must not be null");
        this.selfId = Objects.requireNonNull(selfId, "selfId must not be null");
        this.unidirectional = unidirectional;
        this.connectionGraph = new RandomGraph(addressBook.getSize(), numberOfNeighbors, SEED);

        if (!addressBook.contains(selfId)) {
            throw new IllegalArgumentException("Address book does not contain selfId");
        }
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
        final int selfIndex = addressBook.getIndexOfNodeId(selfId);
        return Arrays.stream(connectionGraph.getNeighbors(selfIndex))
                .mapToObj(addressBook::getNodeId)
                .filter(filter)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectToMe(final NodeId nodeId) {
        return isNeighbor(nodeId)
                && (unidirectional || addressBook.getIndexOfNodeId(nodeId) < addressBook.getIndexOfNodeId(selfId));
    }

    /**
     * Queries the topology on whether this node is my neighbor
     *
     * @param nodeId
     * 		the ID of the node being queried
     * @return true if this node is my neighbor, false if not
     */
    private boolean isNeighbor(final NodeId nodeId) {
        if (!addressBook.contains(nodeId)) {
            return false;
        }
        final int selfIndex = addressBook.getIndexOfNodeId(selfId);
        final int otherIndex = addressBook.getIndexOfNodeId(nodeId);
        return connectionGraph.isAdjacent(selfIndex, otherIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectTo(final NodeId nodeId) {
        return isNeighbor(nodeId)
                && (unidirectional || addressBook.getIndexOfNodeId(nodeId) > addressBook.getIndexOfNodeId(selfId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomGraph getConnectionGraph() {
        return connectionGraph;
    }
}
