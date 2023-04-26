/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.WeightGenerators.INCREMENTING;
import static com.swirlds.common.test.WeightGenerators.RANDOM;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.INCREMENTAL_NODE_WEIGHT_DESC;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.RANDOM_WEIGHT_DESC;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.areAllEventsReturned;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.cliqueTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.fewNodesTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.forkingTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.manyNodeTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.nodeProvidesStaleOtherParents;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.nodeUsesStaleOtherParents;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.orderInvarianceTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.partitionTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.quorumOfNodesGoDownTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.reconnectSimulation;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.subQuorumOfNodesGoDownTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.subQuorumPartitionTests;
import static com.swirlds.platform.test.consensus.ConsensusTestDefinitions.variableRateTests;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;

import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Fast Consensus Tests")
@Tag(TIME_CONSUMING)
class FastConsensusTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#reconnectSimulation")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Reconnect Simulation")
    void fastReconnectSimulation(final ConsensusTestParams params) {
        reconnectSimulation(testDirectory, params.numNodes(), params.weightGenerator(), 1);
    }

    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#staleEvent")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Stale Events Tests")
    @ParameterizedTest
    void staleEvent(final ConsensusTestParams params) {
        ConsensusTestDefinitions.staleEvent(params.numNodes(), params.weightGenerator(), 1, params.seeds());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#areAllEventsReturned")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("All Events Returned Tests")
    void fastAreAllEventsReturned(final ConsensusTestParams params) {
        areAllEventsReturned(params.numNodes(), params.weightGenerator());
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#orderInvarianceTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Fast Order Invariance Tests")
    void FastOrderInvarianceTests(final ConsensusTestParams params) {
        orderInvarianceTests(params.numNodes(), params.weightGenerator(), 1);
    }

    @Disabled("Failing - documented in swirlds/swirlds-platform/issues/4995")
    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#forkingTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Fast Forking Tests")
    void fastForkingTests(final ConsensusTestParams params) {
        forkingTests(params.numNodes(), params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#partitionTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Fast Partition Tests")
    void fastPartitionTests(final ConsensusTestParams params) {
        partitionTests(params.numNodes(), params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#subQuorumPartitionTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Fast Sub Quorum Partition Tests")
    void fastSubQuorumPartitionTests(final ConsensusTestParams params) {
        subQuorumPartitionTests(params.numNodes(), params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#cliqueTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Fast Clique Tests")
    void fastCliqueTests(final ConsensusTestParams params) {
        cliqueTests(params.numNodes(), params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#variableRateTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Fast Variable Rate Tests")
    void fastVariableRateTests(final ConsensusTestParams params) {
        variableRateTests(params.numNodes(), params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#nodeUsesStaleOtherParents")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Fast Node Uses Stale Other Parents")
    void fastNodeUsesStaleOtherParents(final ConsensusTestParams params) {
        nodeUsesStaleOtherParents(params.numNodes(), params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#nodeProvidesStaleOtherParents")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Fast Node Provides Stale Other Parents")
    void fastNodeProvidesStaleOtherParents(final ConsensusTestParams params) {
        nodeProvidesStaleOtherParents(params.numNodes(), params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#quorumOfNodesGoDownTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Fast Quorum Of Nodes Go Down Tests")
    void fastQuorumOfNodesGoDownTests(final ConsensusTestParams params) {
        quorumOfNodesGoDownTests(params.numNodes(), params.weightGenerator(), 1);
    }

    private static Stream<Arguments> manyNodeTestParams() {
        return Stream.of(Arguments.of(new ConsensusTestParams(0, RANDOM, RANDOM_WEIGHT_DESC)));
    }

    @ParameterizedTest
    @MethodSource("manyNodeTestParams")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Fast Many Node Tests")
    void fastManyNodeTests(final ConsensusTestParams params) {
        manyNodeTests(params.weightGenerator(), 1);
    }

    private static Stream<Arguments> fewNodesTestParams() {
        return Stream.of(Arguments.of(new ConsensusTestParams(0, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)));
    }

    @ParameterizedTest
    @MethodSource("fewNodesTestParams")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Fast Few Node Tests")
    void fastFewNodesTests(final ConsensusTestParams params) {
        fewNodesTests(params.weightGenerator(), 1);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.platform.test.consensus.ConsensusTestArgs#subQuorumOfNodesGoDownTests")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @Tag(TIME_CONSUMING)
    @DisplayName("Fast Sub-Quorum Of Nodes Go Down Tests")
    void fastSubQuorumOfNodesGoDownTests(final ConsensusTestParams params) {
        subQuorumOfNodesGoDownTests(params.numNodes(), params.weightGenerator(), 1);
    }

    private static Stream<Arguments> restartWithEventsParams() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(0, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(0, RANDOM, RANDOM_WEIGHT_DESC)));
    }

    @Disabled("Seed 8711987114711702791L fails. Ticket is 5156")
    @ParameterizedTest
    @MethodSource("restartWithEventsParams")
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestComponentTags.CONSENSUS)
    @DisplayName("Node restart with events")
    void fastRestartWithEvents(final ConsensusTestParams params) throws ConstructableRegistryException, IOException {
        final Long seed = null;
        final int minNodes = 2;
        final int maxNodes = 30;
        final int minPerSeq = 100;
        final int maxPerSeq = 5_000;

        ConsensusTestDefinitions.restart(
                seed, testDirectory, params.weightGenerator(), minNodes, maxNodes, minPerSeq, maxPerSeq);
    }
}
