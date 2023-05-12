/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
import java.util.List;
import java.util.function.Predicate;

/**
 * Holds information about the topology of the network
 */
public interface NetworkTopology {
    /**
     * Should this node be connecting to this peer?
     *
     * @param nodeId
     * 		the peer ID
     * @return true if this connection is in line with the network topology
     */
    boolean shouldConnectTo(NodeId nodeId);

    /**
     * Should this peer be connecting to this node?
     *
     * @param nodeId
     * 		the peer ID
     * @return true if this connection is in line with the network topology
     */
    boolean shouldConnectToMe(NodeId nodeId);

    /**
     * @return a list of all peers this node should be connected to
     */
    List<NodeId> getNeighbors();

    /**
     * @return a list of peers this node should be connected to with the applied filter
     */
    List<NodeId> getNeighbors(final Predicate<NodeId> filter);

    /**
     * @return the underlying graph on which this topology is based on
     */
    RandomGraph getConnectionGraph();
}
