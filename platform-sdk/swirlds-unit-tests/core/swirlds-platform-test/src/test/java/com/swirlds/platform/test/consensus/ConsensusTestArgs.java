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

package com.swirlds.platform.test.consensus;

import static com.swirlds.common.test.StakeGenerators.BALANCED;
import static com.swirlds.common.test.StakeGenerators.BALANCED_REAL_STAKE;
import static com.swirlds.common.test.StakeGenerators.INCREMENTING;
import static com.swirlds.common.test.StakeGenerators.ONE_THIRD_ZERO_STAKE;
import static com.swirlds.common.test.StakeGenerators.RANDOM;
import static com.swirlds.common.test.StakeGenerators.RANDOM_REAL_STAKE;
import static com.swirlds.common.test.StakeGenerators.SINGLE_NODE_STRONG_MINORITY;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class ConsensusTestArgs {

    public static final String BALANCED_STAKE_DESC = "Balanced Stake";
    public static final String BALANCED_REAL_STAKE_DESC = "Balanced Stake, Real Total Stake Value";
    public static final String INCREMENTAL_NODE_STAKE_DESC = "Incremental Node Stake";
    public static final String SINGLE_NODE_STRONG_MINORITY_DESC = "Single Node With Strong Minority Stake";
    public static final String ONE_THIRD_NODES_ZERO_STAKE_DESC = "One Third of Nodes Have Zero Stake";
    public static final String RANDOM_STAKE_DESC = "Random Stake, Real Total Stake Value";

    static Stream<Arguments> orderInvarianceTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(2, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, ONE_THIRD_ZERO_STAKE, ONE_THIRD_NODES_ZERO_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(50, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(50, RANDOM, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> reconnectSimulation() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(4, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, ONE_THIRD_ZERO_STAKE, ONE_THIRD_NODES_ZERO_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)),
                Arguments.of(
                        new ConsensusTestParams(10, SINGLE_NODE_STRONG_MINORITY, SINGLE_NODE_STRONG_MINORITY_DESC)),
                Arguments.of(new ConsensusTestParams(10, ONE_THIRD_ZERO_STAKE, ONE_THIRD_NODES_ZERO_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(10, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> staleEvent() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(6, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(6, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(6, ONE_THIRD_ZERO_STAKE, ONE_THIRD_NODES_ZERO_STAKE_DESC)));
    }

    static Stream<Arguments> forkingTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> partitionTests() {
        return Stream.of(
                // Uses balanced stakes for 4 so that each partition can continue to create events.
                // This limitation if one of the test, not the consensus algorithm.
                // Arguments.of(new ConsensusTestParams(4, BALANCED_REAL_STAKE,
                // BALANCED_REAL_STAKE_DESC)),

                // Use uneven stake such that no single node has a strong minority and could be
                // put in a partition by itself and no longer generate events. This limitation if
                // one
                // of the test, not the consensus algorithm.
                // Arguments.of(new ConsensusTestParams(5, INCREMENTING,
                // INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)));
    }

    static Stream<Arguments> subQuorumPartitionTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(7, BALANCED_REAL_STAKE, BALANCED_REAL_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(
                        9,
                        ONE_THIRD_ZERO_STAKE,
                        ONE_THIRD_NODES_ZERO_STAKE_DESC,
                        // used to cause a stale mismatch, documented in
                        // swirlds/swirlds-platform/issues/5007
                        3101029514312517274L,
                        -4115810541946354865L)));
    }

    static Stream<Arguments> cliqueTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(4, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> variableRateTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> nodeUsesStaleOtherParents() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(
                        4,
                        INCREMENTING,
                        INCREMENTAL_NODE_STAKE_DESC,
                        // seed was failing because Consensus ratio is 0.6611, which is less
                        // than what was previously
                        // set
                        458078453642476240L)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> nodeProvidesStaleOtherParents() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(
                        4,
                        INCREMENTING,
                        INCREMENTAL_NODE_STAKE_DESC,
                        // seed was previously failing because Consensus ratio is 0.2539
                        -6816700673806876476L)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> quorumOfNodesGoDownTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> subQuorumOfNodesGoDownTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_STAKE, RANDOM_STAKE_DESC)));
    }

    static Stream<Arguments> ancientEventTests() {
        return Stream.of(Arguments.of(new ConsensusTestParams(4, BALANCED, BALANCED_STAKE_DESC)));
    }

    public static Stream<Arguments> restartWithEventsParams() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(5, INCREMENTING, INCREMENTAL_NODE_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(10, RANDOM, RANDOM_STAKE_DESC)),
                Arguments.of(new ConsensusTestParams(20, RANDOM, RANDOM_STAKE_DESC)));
    }
}
