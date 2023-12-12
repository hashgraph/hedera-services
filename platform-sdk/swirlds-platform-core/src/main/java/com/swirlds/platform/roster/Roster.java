/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.roster;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * A roster is the set of nodes that are creating events and contributing to consensus. The data in a Roster object is
 * immutable must not change over time.
 */
public interface Roster extends Iterable<RosterEntry>, SelfSerializable {

    /**
     * @return the a collection of unique nodeIds in the roster.
     */
    @NonNull
    Collection<NodeId> getNodeIds();

    /**
     * @return the RosterEntry with the given nodeId
     * @throws java.util.NoSuchElementException if the nodeId is not in the roster
     */
    @NonNull
    RosterEntry getEntry(NodeId nodeId);

    /**
     * @return the total weight of all nodes in the roster
     */
    default long getTotalWeight() {
        long totalWeight = 0;
        for (RosterEntry entry : this) {
            totalWeight += entry.getWeight();
        }
        return totalWeight;
    }
}
