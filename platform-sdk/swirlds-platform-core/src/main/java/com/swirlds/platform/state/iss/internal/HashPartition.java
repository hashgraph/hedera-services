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

package com.swirlds.platform.state.iss.internal;

import com.swirlds.common.crypto.Hash;
import java.util.HashSet;
import java.util.Set;

/**
 * A group of nodes in agreement about the hash for a particular round. If the network is ISS free then there should
 * be exactly one hash partition per round. If there has been an ISS then there will be multiple hash partitions.
 */
public class HashPartition {

    /**
     * The hash that this group of nodes agrees upon.
     */
    private final Hash hash;

    /**
     * The total stake of the nodes known to agree with a given hash.
     */
    private long totalStake = 0;

    /**
     * The node IDs that are known to agree with this hash.
     */
    private final Set<Long> nodes = new HashSet<>();

    /**
     * Create an object that tracks a group of nodes that agree about the hash on the state.
     *
     * @param hash
     * 		the agreed upon hash
     */
    public HashPartition(final Hash hash) {
        this.hash = hash;
    }

    /**
     * Add hash information for a node.
     *
     * @param nodeId
     * 		the ID of the node
     * @param stake
     * 		the stake held by the node
     */
    public void addNodeHash(final long nodeId, final long stake) {
        final boolean added = nodes.add(nodeId);
        if (!added) {
            throw new IllegalStateException("node " + nodeId + " is already in the partition");
        }
        totalStake += stake;
    }

    /**
     * Get the agreed upon hash.
     */
    public Hash getHash() {
        return hash;
    }

    /**
     * Get the total stake known to agree with this hash.
     */
    public long getTotalStake() {
        return totalStake;
    }

    /**
     * Get a set of nodes that are in agreement with this hash. This set should not be modified externally.
     */
    public Set<Long> getNodes() {
        return nodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Partition stake =  ")
                .append(totalStake)
                .append(", hash = ")
                .append(hash)
                .append(", nodes = ");
        for (final long node : nodes) {
            sb.append(node).append(" ");
        }

        return sb.toString();
    }
}
