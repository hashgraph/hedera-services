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

package com.swirlds.platform.test.consensus;

import static com.swirlds.common.test.fixtures.WeightGenerators.RANDOM;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.RANDOM_WEIGHT_DESC;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;

import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Consensus Tests")
@Tag(TIME_CONSUMING)
class ConsensusTests {
    /**
     * Number of iterations in each test. An iteration is to create one graph, and feed it in twice
     * in different topological orders, and check if they match.
     */
    private final int NUM_ITER = 1;

    @BeforeAll
    public static void initConfig() {
        new TestConfigBuilder().getOrCreateConfig();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#orderInvarianceTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Order Invariance Tests")
    void orderInvarianceTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::orderInvarianceTests)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#reconnectSimulation")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Reconnect Simulation")
    @ParameterizedTest
    void reconnectSimulation(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::reconnect)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#staleEvent")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Stale Events Tests")
    @ParameterizedTest
    void staleEvent(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::stale)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#forkingTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Forking Tests")
    void forkingTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::forkingTests)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#partitionTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Partition Tests")
    void partitionTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::partitionTests)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#subQuorumPartitionTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Sub Quorum Partition Tests")
    void subQuorumPartitionTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::subQuorumPartitionTests)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#cliqueTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Clique Tests")
    void cliqueTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::cliqueTests)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#variableRateTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Variable Rate Tests")
    void variableRateTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::variableRateTests)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#nodeUsesStaleOtherParents")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Node Uses Stale Other Parents")
    void nodeUsesStaleOtherParents(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::usesStaleOtherParents)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#nodeProvidesStaleOtherParents")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Node Provides Stale Other Parents")
    void nodeProvidesStaleOtherParents(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::providesStaleOtherParents)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#quorumOfNodesGoDownTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Quorum Of Nodes Go Down Tests")
    void quorumOfNodesGoDownTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::quorumOfNodesGoDown)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#subQuorumOfNodesGoDownTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Sub Quorum Of Nodes Go Down Tests")
    void subQuorumOfNodesGoDownTests(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::subQuorumOfNodesGoDown)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#orderInvarianceTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Repeated Timestamp Test")
    void repeatedTimestampTest(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::repeatedTimestampTest)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#ancientEventTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Consensus Receives Ancient Event")
    void ancientEventTest(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::ancient)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#restartWithEventsParams")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Node restart with events")
    void fastRestartWithEvents(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::restart)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#migrationTestParams")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Migration from a state with events to new consensus")
    void migrationTest(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::migrationTest)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#nodeRemoveTestParams")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Remove a node from the address book at restart")
    void nodeRemoveTest(final ConsensusTestParams params) {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::removeNode)
                .setParams(params)
                .setIterations(NUM_ITER)
                .run();
    }

    @Test
    void syntheticSnapshotTest() {
        ConsensusTestRunner.create()
                .setTest(ConsensusTestDefinitions::syntheticSnapshot)
                .setParams(new ConsensusTestParams(4, RANDOM, RANDOM_WEIGHT_DESC))
                .setIterations(NUM_ITER)
                .run();
    }
}
