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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A fully connected topology that never changes.
 */
public class StaticTopology implements NetworkTopology {

    /** nodes are mapped so lookups are efficient. **/
    private final Set<NodeId> nodeIdToIndexSet = new HashSet<>();

    private final NodeId selfId;

    /**
     * Constructor.
     * @param peers             the set of peers in the network
     * @param selfId            the ID of this node
     */
    public StaticTopology(@NonNull final List<PeerInfo> peers, @NonNull final NodeId selfId) {
        Objects.requireNonNull(peers);
        Objects.requireNonNull(selfId);
        peers.forEach(peer -> nodeIdToIndexSet.add(peer.nodeId()));
        this.selfId = selfId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<NodeId> getNeighbors() {
        return nodeIdToIndexSet;
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
        return nodeIdToIndexSet.contains(nodeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldConnectTo(final NodeId nodeId) {
        return isNeighbor(nodeId) && nodeId.id() > selfId.id();
    }
}
