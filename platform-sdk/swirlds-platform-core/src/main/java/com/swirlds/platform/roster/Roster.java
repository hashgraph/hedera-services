/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
     * @return a collection of all unique nodeIds in the roster.
     */
    @NonNull
    Collection<NodeId> getNodeIds();

    /**
     * @param nodeId the nodeId of the {@link RosterEntry} to get
     * @return the RosterEntry with the given nodeId
     * @throws java.util.NoSuchElementException if the nodeId is not in the roster
     */
    @NonNull
    RosterEntry getEntry(@NonNull NodeId nodeId);

    /**
     * @param nodeId the nodeId to check for membership in the roster
     * @return true if there is a rosterEntry with the given nodeId, false otherwise
     */
    default boolean contains(@NonNull NodeId nodeId) {
        return getNodeIds().contains(nodeId);
    }

    /**
     * @return the total number of nodes in the roster
     */
    default int getSize() {
        return getNodeIds().size();
    }

    /**
     * @return the total weight of all nodes in the roster
     */
    default long getTotalWeight() {
        long totalWeight = 0;
        for (final RosterEntry entry : this) {
            totalWeight += entry.getWeight();
        }
        return totalWeight;
    }
}
