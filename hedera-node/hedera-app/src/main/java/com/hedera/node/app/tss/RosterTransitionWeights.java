/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * Represents the weights of the nodes in a roster transition.
 * @param sourceNodeWeights the weights of the nodes in the source roster
 * @param targetNodeWeights the weights of the nodes in the target roster
 * @param sourceWeightThreshold the weight required for a strong minority in the source roster
 * @param targetWeightThreshold the weight required for a strong minority in the target roster
 */
public record RosterTransitionWeights(
        @NonNull Map<Long, Long> sourceNodeWeights,
        @NonNull Map<Long, Long> targetNodeWeights,
        long sourceWeightThreshold,
        long targetWeightThreshold) {

    public RosterTransitionWeights(
            @NonNull final Map<Long, Long> sourceNodeWeights, @NonNull final Map<Long, Long> targetNodeWeights) {
        this(
                requireNonNull(sourceNodeWeights),
                requireNonNull(targetNodeWeights),
                strongMinorityWeightFor(sourceNodeWeights),
                strongMinorityWeightFor(targetNodeWeights));
    }

    /**
     * Returns the weight of a node in the source roster.
     * @param nodeId the ID of the node
     * @return the weight of the node
     */
    public long sourceWeightOf(final long nodeId) {
        return sourceNodeWeights.getOrDefault(nodeId, 0L);
    }

    /**
     * Returns whether given node has an explicit weight in the target roster.
     * @param nodeId the ID of the node
     * @return whether the node has an explicit weight
     */
    public boolean hasTargetWeightOf(final long nodeId) {
        return targetNodeWeights.containsKey(nodeId);
    }

    /**
     * Returns the weight of a node in the target roster.
     * @param nodeId the ID of the node
     * @return the weight of the node
     */
    public long targetWeightOf(final long nodeId) {
        return targetNodeWeights.getOrDefault(nodeId, 0L);
    }

    /**
     * Returns the size of the target roster.
     */
    public int targetRosterSize() {
        return targetNodeWeights.size();
    }

    /**
     * Returns the weight that would constitute a strong minority of the network weight for a roster.
     *
     * @param weights the weights of the nodes in the roster
     * @return the weight required for a strong minority
     */
    public static long strongMinorityWeightFor(@NonNull final Map<Long, Long> weights) {
        return strongMinorityWeightFor(
                weights.values().stream().mapToLong(Long::longValue).sum());
    }

    /**
     * Returns the weight that would constitute a strong minority of the network weight for a given total weight.
     * @param totalWeight the total weight of the network
     * @return the weight required for a strong minority
     */
    public static long strongMinorityWeightFor(final long totalWeight) {
        // Since aBFT is unachievable with n/3 malicious weight, using the conclusion of n/3 weight
        // ensures it the conclusion overlaps with the weight held by at least one honest node
        return (totalWeight + 2) / 3;
    }
}
