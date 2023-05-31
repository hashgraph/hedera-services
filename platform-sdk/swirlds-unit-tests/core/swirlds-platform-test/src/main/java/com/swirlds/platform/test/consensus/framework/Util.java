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

package com.swirlds.platform.test.consensus.framework;

import com.swirlds.platform.Utilities;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Util {
    /**
     * Get a set of node ids such that their stake is at least a strong minority but not a super
     * majority. Each group of nodes (the partitioned node and non-partitions nodes) has a strong
     * minority.
     *
     * @param nodeStakes the stakes of each node in the network
     * @return the list of node ids
     */
    public static Set<Integer> getStrongMinorityNodes(final List<Long> nodeStakes) {
        final Set<Integer> partitionedNodes = new HashSet<>();
        final long totalStake = nodeStakes.stream().reduce(0L, Long::sum);
        long partitionedStake = 0L;
        for (int i = 0; i < nodeStakes.size(); i++) {
            // If we have enough partitioned nodes to make a strong minority, stop and return
            if (Utilities.isStrongMinority(partitionedStake, totalStake)) {
                break;
            }
            // If adding this node to the partition would give the partition a super majority, skip
            // this node because
            // the remaining group of nodes would not have a strong minority
            if (Utilities.isSuperMajority(partitionedStake + nodeStakes.get(i), totalStake)) {
                continue;
            }
            partitionedNodes.add(i);
            partitionedStake += nodeStakes.get(i);
        }
        System.out.println("Partitioned nodes: " + partitionedNodes);
        System.out.printf(
                "\nPartition has %s (%s%%) of %s total stake.%n",
                partitionedStake, (((double) partitionedStake) / totalStake) * 100, totalStake);
        return partitionedNodes;
    }

    /**
     * Get a set of node ids such that their stake is less than a strong minority. Nodes not in the
     * returned set will have a super majority and can continue to reach consensus.
     *
     * @param nodeStakes the stakes of each node in the network
     * @return the list of node ids
     */
    public static Set<Integer> getSubStrongMinorityNodes(final List<Long> nodeStakes) {
        final Set<Integer> partitionedNodes = new HashSet<>();
        final long totalStake = nodeStakes.stream().reduce(0L, Long::sum);
        long partitionedStake = 0L;
        for (int i = 0; i < nodeStakes.size(); i++) {
            // Leave at least two nodes not in the partition set so that gossip can continue in both
            // the partitioned nodes and non-partitioned nodes
            if (partitionedNodes.size() + 2 == nodeStakes.size()) {
                break;
            }
            // If adding this node to the partition would give the partition a strong minority, skip
            // this node because
            // the remaining group of nodes would not have a super majority
            if (Utilities.isStrongMinority(partitionedStake + nodeStakes.get(i), totalStake)) {
                continue;
            }
            partitionedNodes.add(i);
            partitionedStake += nodeStakes.get(i);
        }
        System.out.println("Partitioned nodes: " + partitionedNodes);
        System.out.printf(
                "\nPartition has %s (%s%%) of %s total stake.%n",
                partitionedStake, (((double) partitionedStake) / totalStake) * 100, totalStake);
        return partitionedNodes;
    }
}
