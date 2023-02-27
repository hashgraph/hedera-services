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

import static com.swirlds.common.test.StakeGenerators.BALANCED;

import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Consensus Tests")
class ConsensusTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    /**
     * Number of iterations in each test. An iteration is to create one graph, and feed it in twice in different
     * topological orders, and check if they match. 10 iterations takes about 13 minutes.
     */
    private final int NUM_ITER = 10;

    @BeforeAll
    public static void initConfig() {
        new TestConfigBuilder().getOrCreateConfig();
    }

    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#reconnectSimulation")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Reconnect Simulation")
    @ParameterizedTest
    void reconnectSimulation(final ConsensusTestParams params) {
        ConsensusTestDefinitions.reconnectSimulation(
                testDirectory, params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#reconnectSimulationWithShadowGraph")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Reconnect Simulation with Shadow Graph")
    @ParameterizedTest
    void reconnectSimulationWithShadowGraph(final ConsensusTestParams params) {
        ConsensusTestDefinitions.reconnectSimulation(
                testDirectory, params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#staleEvent")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Stale Events Tests")
    @ParameterizedTest
    void staleEvent(final ConsensusTestParams params) {
        ConsensusTestDefinitions.staleEvent(params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#areAllEventsReturned")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("All Events Returned Tests")
    void areAllEventsReturned(final ConsensusTestParams params) {
        ConsensusTestDefinitions.areAllEventsReturned(params.numNodes(), params.stakeGenerator());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#orderInvarianceTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Order Invariance Tests")
    void orderInvarianceTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.orderInvarianceTests(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @Disabled("Failing - documented in swirlds/swirlds-platform/issues/4995")
    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#forkingTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Forking Tests")
    void forkingTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.forkingTests(params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @Disabled("Failing - documented in swirlds/swirlds-platform/issues/4995")
    @ParameterizedTest
    @ValueSource(ints = {2, 4, 9})
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Forking Tests")
    void forkingTestsOldVersion(final int numNodes) {
        // This seed used to cause a bug in the consensus algorithm. It was supposedly fixed by branch
        // 02617-D-reconnect-connect-recalculation but it still fails. Ticket swirlds/swirlds-platform/issues/4995
        // documents this failure.
        ConsensusTestDefinitions.forkingTests(numNodes, BALANCED, 1, -6764715924816914095L);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#partitionTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Partition Tests")
    void partitionTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.partitionTests(params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#subQuorumPartitionTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Sub Quorum Partition Tests")
    void subQuorumPartitionTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.subQuorumPartitionTests(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#cliqueTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Clique Tests")
    void cliqueTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.cliqueTests(params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#variableRateTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Variable Rate Tests")
    void variableRateTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.variableRateTests(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#nodeUsesStaleOtherParents")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Node Uses Stale Other Parents")
    void nodeUsesStaleOtherParents(final ConsensusTestParams params) {
        ConsensusTestDefinitions.nodeUsesStaleOtherParents(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#nodeProvidesStaleOtherParents")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Node Provides Stale Other Parents")
    void nodeProvidesStaleOtherParents(final ConsensusTestParams params) {
        ConsensusTestDefinitions.nodeProvidesStaleOtherParents(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#quorumOfNodesGoDownTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Quorum Of Nodes Go Down Tests")
    void quorumOfNodesGoDownTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.quorumOfNodesGoDownTests(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#subQuorumOfNodesGoDownTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TestQualifierTags.AT_SCALE)
    @DisplayName("Sub Quorum Of Nodes Go Down Tests")
    void subQuorumOfNodesGoDownTests(final ConsensusTestParams params) {
        ConsensusTestDefinitions.subQuorumOfNodesGoDownTests(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#orderInvarianceTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Repeated Timestamp Test")
    void repeatedTimestampTest(final ConsensusTestParams params) {
        ConsensusTestDefinitions.repeatedTimestampTest(
                params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#ancientEventTests")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Consensus Receives Ancient Event")
    void ancientEventTest(final ConsensusTestParams params) {
        ConsensusTestDefinitions.ancientEventTest(params.numNodes(), params.stakeGenerator(), NUM_ITER, params.seeds());
    }
}
