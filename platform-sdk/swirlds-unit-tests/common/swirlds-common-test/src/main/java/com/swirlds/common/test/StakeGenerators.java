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

package com.swirlds.common.test;

import static com.swirlds.common.test.RandomUtils.initRandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class StakeGenerators {

    public static final StakeGenerator BALANCED = (l, i) -> StakeGenerators.balancedNodeStakes(i);
    public static final StakeGenerator BALANCED_REAL_STAKE = (l, i) -> StakeGenerators.balancedNodeStakes(i, true);
    public static final StakeGenerator INCREMENTING = (l, i) -> StakeGenerators.incrementingStake(i);
    public static final StakeGenerator SINGLE_NODE_STRONG_MINORITY =
            (l, i) -> StakeGenerators.singleNodeWithStrongMinority(i);
    public static final StakeGenerator ONE_THIRD_ZERO_STAKE = StakeGenerators::oneThirdNodesZeroStake;
    public static final StakeGenerator RANDOM = StakeGenerators::randomNodeStakes;
    public static final StakeGenerator RANDOM_REAL_STAKE = (l, i) -> StakeGenerators.randomNodeStakes(l, i, true);

    /**
     * total stakes are the same as the number of the number of tinybars in existence
     * (50 billion)*(times 100 million)
     */
    public static final long TOTAL_STAKES = 50L * 1_000_000_000L * 100L * 1_000_000L;

    private static final long MINIMUM_NON_ZERO_STAKE = 1L;

    /** A stake value that is easy to read and makes math calculations easy. */
    private static final int EASY_TO_READ_STAKE = 90;

    private StakeGenerators() {}

    /**
     * Generates balanced stake values for each node, where each node has 1 stake.
     *
     * @param numberOfNodes
     * 		the number of nodes to generate stake for
     * @return a list of stake values
     */
    public static List<Long> balancedNodeStakes(final int numberOfNodes) {
        return balancedNodeStakes(numberOfNodes, numberOfNodes);
    }

    /**
     * Generates balanced stake values for each node.
     *
     * @param numberOfNodes
     * 		the number of nodes to generate stake for
     * @param useRealTotalStake
     * 		true if the real amount of total stake should be evenly distributed amongst each node
     * @return a list of stake values
     */
    public static List<Long> balancedNodeStakes(final int numberOfNodes, final boolean useRealTotalStake) {
        if (useRealTotalStake) {
            return balancedNodeStakes(numberOfNodes, TOTAL_STAKES);
        }
        return balancedNodeStakes(numberOfNodes);
    }

    /**
     * Generates balanced stake values for each node, where each node is assigned an equal part of the {@code total
     * stake}. If {@code totalStake} is not evenly divisible by {@code numberOfNodes}, not all stake will be
     * distributed to maintain stake balance.
     *
     * @param numberOfNodes
     * 		the number of nodes to generate stake for
     * @param totalStake
     * 		the total amount of stake to distribute. Note that not all stake is assigned if not evenly divisible among
     * 		the nodes.
     * @return a list of stake values
     */
    private static List<Long> balancedNodeStakes(final int numberOfNodes, final long totalStake) {
        final long stakePerNode = totalStake / numberOfNodes;
        return Collections.nCopies(numberOfNodes, stakePerNode);
    }

    /**
     * Generates random node stakes.
     *
     * @param stakeSeed
     * 		the seed to use for the random number generator
     * @param numberOfNodes
     * 		the number of nodes to generate stake for
     * @param useRealTotalStake
     * 		if true, the real amount of total stake should be distributed. If false, a smaller, easier to read value
     * 		will be used as the maximum for each node.
     * @return a list of stake values
     */
    public static List<Long> randomNodeStakes(
            final Long stakeSeed, final int numberOfNodes, final boolean useRealTotalStake) {
        if (useRealTotalStake) {
            return randomNodeStakes(stakeSeed, numberOfNodes, TOTAL_STAKES);
        }
        return randomNodeStakes(stakeSeed, numberOfNodes);
    }

    /**
     * Generates a list of node stakes that are pseudo-random and sum to exactly {@code totalStake}. No node is assigned
     * more than 1/2 of the total stake. Nodes may be assigned zero stake.
     *
     * @param stakeSeed
     * 		the seed to use for the random number generator
     * @param numberOfNodes
     * 		the number of nodes to generate stake for
     * @param totalStake
     * 		the total amount of stake to distribute
     * @return a list of stake values
     */
    public static List<Long> randomNodeStakes(final Long stakeSeed, final int numberOfNodes, final long totalStake) {
        final Random r = initRandom(stakeSeed, false);
        final List<Long> stakes = new ArrayList<>(numberOfNodes);
        final long halfTotalStake = totalStake / 2;
        final long firstNodeStake = r.nextLong(halfTotalStake);
        long remainingStake = totalStake - firstNodeStake;
        stakes.add(firstNodeStake);
        for (int i = 1; i < numberOfNodes - 1; i++) {
            final long stake = r.nextLong(remainingStake);
            remainingStake -= stake;
            stakes.add(stake);
        }
        stakes.add(remainingStake);
        return stakes;
    }

    /**
     * Generates random stake values for each node between 1 (inclusive) and 90 (exclusive).
     *
     * @param stakeSeed
     * 		the seed to use for the random number generator
     * @param numberOfNodes
     * 		the number of nodes to generate stake for
     * @return a list of stake values
     */
    public static List<Long> randomNodeStakes(final Long stakeSeed, final int numberOfNodes) {
        final RandomGenerator r = initRandom(stakeSeed);
        final List<Long> nodeStakes = new ArrayList<>(numberOfNodes);
        for (int i = 0; i < numberOfNodes; i++) {
            nodeStakes.add(r.nextLong(MINIMUM_NON_ZERO_STAKE, EASY_TO_READ_STAKE));
        }
        return nodeStakes;
    }

    /**
     * Creates a list of node stakes where 1/3 of the nodes are zero-stake and the
     * remaining nodes are assigned a random stake value between 1 and 90, inclusive.
     *
     * @return test arguments
     */
    public static List<Long> oneThirdNodesZeroStake(final Long stakeSeed, final int numberOfNodes) {
        final RandomGenerator r = initRandom(stakeSeed);
        final List<Long> nodeStakes = new ArrayList<>(numberOfNodes);

        for (int nodeId = 0; nodeId < numberOfNodes; nodeId++) {
            long stake = r.nextLong(MINIMUM_NON_ZERO_STAKE, EASY_TO_READ_STAKE);
            if (nodeId < (numberOfNodes / 3)) {
                stake = 0;
            }
            nodeStakes.add(stake);
        }
        return nodeStakes;
    }

    /**
     * Creates a list of node stakes where three nodes have a strong minority of stake
     * (evenly distributed) and the remaining stake is split evently among the remaining nodes.
     *
     * @param numberOfNodes
     * 		the number of nodes to generate stake for. Minimum of 11 nodes is required to ensure that only one unique
     * 		set of three nodes has a super minority based on the total stake of 90.
     * @return test arguments
     */
    public static List<Long> threeNodesWithStrongMinority(final int numberOfNodes) {
        if (numberOfNodes < 11 || numberOfNodes > 63) {
            throw new IllegalArgumentException(String.format(
                    "Invalid number of nodes: %s. Valid range is 11 - 63 for this stake distribution.", numberOfNodes));
        }
        final List<Long> nodeStakes = new ArrayList<>(numberOfNodes);

        final int totalStake = EASY_TO_READ_STAKE;
        final int strongMinorityStake = totalStake / 3;

        for (int nodeId = 0; nodeId < numberOfNodes; nodeId++) {
            long stake = (totalStake - strongMinorityStake) / (numberOfNodes - 3);
            if (nodeId < 3) {
                // three nodes have a strong minority of stake
                stake = strongMinorityStake / 3;
            }
            nodeStakes.add(stake);
        }
        return nodeStakes;
    }

    /**
     * Creates a list of node stakes where a single node has a strong minority of stake and
     * the remaining stake is evenly distributed among the remaining nodes.
     *
     * @param numberOfNodes
     * 		the number of nodes to generate stake for. Minimum of 3 nodes is required.
     * @return test arguments
     */
    public static List<Long> singleNodeWithStrongMinority(final int numberOfNodes) {
        if (numberOfNodes < 3 || numberOfNodes > 61) {
            throw new IllegalArgumentException(String.format(
                    "Invalid number of nodes: %s. Valid range is 3 - 61 for this stake distribution.", numberOfNodes));
        }
        final int totalStake = EASY_TO_READ_STAKE;
        final int strongMinorityStake = totalStake / 3;

        final List<Long> nodeStakes = new ArrayList<>(numberOfNodes);

        for (int nodeId = 0; nodeId < numberOfNodes; nodeId++) {
            long stake = (totalStake - strongMinorityStake) / (numberOfNodes - 1);
            if (nodeId == 0) {
                // a single node has a strong minority of stake
                stake = strongMinorityStake;
            }
            nodeStakes.add(stake);
        }
        return nodeStakes;
    }

    /**
     * Creates a list of node stakes where nodes are assigned an incrementing amount of stake.
     *
     * @return test arguments
     */
    public static List<Long> incrementingStake(final int numberOfNodes) {
        final List<Long> nodeStakes = new ArrayList<>(numberOfNodes);

        for (int nodeId = 0; nodeId < numberOfNodes; nodeId++) {
            final long stake = nodeId + 1L;
            nodeStakes.add(stake);
        }
        return nodeStakes;
    }

    /**
     * Creates a list of node stakes where a single node has zero stake and the remaining
     * nodes are assigned an incrementing amount of stake.
     *
     * @return test arguments
     */
    public static List<Long> incrementingStakeWithOneZeroStake(final int numberOfNodes) {
        final List<Long> nodeStakes = new ArrayList<>(numberOfNodes);

        for (int nodeId = 0; nodeId < numberOfNodes; nodeId++) {
            long stake = nodeId + 1L;
            if (nodeId == 0 && numberOfNodes > 1) {
                // Add a zero stake node
                stake = 0;
            }
            nodeStakes.add(stake);
        }
        return nodeStakes;
    }
}
