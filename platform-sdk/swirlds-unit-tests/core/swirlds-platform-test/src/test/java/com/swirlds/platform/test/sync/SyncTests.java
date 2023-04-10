/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static com.swirlds.common.threading.manager.internal.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.test.event.EventUtils.integerPowerDistribution;
import static com.swirlds.test.framework.ResourceLoader.loadLog4jContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.test.threading.ReplaceSyncPhaseParallelExecutor;
import com.swirlds.common.test.threading.SyncPhaseParallelExecutor;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.sync.ShadowEvent;
import com.swirlds.platform.test.event.emitter.EventEmitterFactory;
import com.swirlds.platform.test.event.emitter.StandardEventEmitter;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import com.swirlds.platform.test.event.source.EventSourceFactory;
import com.swirlds.platform.test.event.source.StandardEventSource;
import com.swirlds.platform.test.graph.OtherParentMatrixFactory;
import com.swirlds.platform.test.graph.PartitionedGraphCreator;
import com.swirlds.platform.test.graph.SplitForkGraphCreator;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.FileNotFoundException;
import java.net.SocketException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Sync Integration/Unit Tests")
public class SyncTests {

    private static final boolean platformLoggingEnabled = true;

    private static Stream<Arguments> fourNodeGraphParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(4, 100, 20, 0)),
                Arguments.of(new SyncTestParams(4, 100, 0, 20)),
                Arguments.of(new SyncTestParams(4, 100, 20, 20)));
    }

    private static Stream<Arguments> tenNodeGraphParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(10, 100, 50, 0)),
                Arguments.of(new SyncTestParams(10, 100, 0, 50)),
                Arguments.of(new SyncTestParams(10, 100, 50, 50)));
    }

    private static Stream<Arguments> tenNodeBigGraphParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(10, 1000, 2000, 0)),
                Arguments.of(new SyncTestParams(10, 1000, 0, 2000)),
                Arguments.of(new SyncTestParams(10, 1000, 2000, 2000)));
    }

    /**
     * Supplies test values for {@link #simpleGraph(SyncTestParams)}
     */
    private static Stream<Arguments> simpleFourNodeGraphParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(4, 10, 0, 0)),
                Arguments.of(new SyncTestParams(4, 10, 1, 0)),
                Arguments.of(new SyncTestParams(4, 10, 0, 1)),
                Arguments.of(new SyncTestParams(4, 10, 1, 1)),
                Arguments.of(new SyncTestParams(4, 10, 8, 1)));
    }

    /**
     * Supplies edge case test values for {@link #simpleGraph(SyncTestParams)}
     */
    private static Stream<Arguments> edgeCaseGraphParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(10, 0, 20, 0)),
                Arguments.of(new SyncTestParams(10, 0, 0, 20)),
                Arguments.of(new SyncTestParams(10, 0, 0, 0)),
                Arguments.of(new SyncTestParams(10, 20, 0, 0)));
    }

    /**
     * Supplies test values for {@link #partitionedGraph(SyncTestParams)}
     */
    private static Stream<Arguments> partitionedGraphParams() {
        return Stream.of(
                // Partitioned graphs with common events
                Arguments.of(new SyncTestParams(10, 30, 5, 5)),
                Arguments.of(new SyncTestParams(10, 30, 5, 5)),
                Arguments.of(new SyncTestParams(20, 200, 100, 0)),
                Arguments.of(new SyncTestParams(20, 200, 0, 100)),
                Arguments.of(new SyncTestParams(20, 200, 100, 100)),
                // Partitioned graphs with no common events
                Arguments.of(new SyncTestParams(20, 0, 100, 0)),
                Arguments.of(new SyncTestParams(20, 0, 0, 100)),
                Arguments.of(new SyncTestParams(20, 0, 100, 100)));
    }

    /**
     * Supplies test values for {@link #testCallerExceptionDuringSyncPhase(SyncTestParams, int, int)} and {@link
     * #testListenerExceptionDuringSyncPhase(SyncTestParams, int, int)}
     */
    private static Stream<Arguments> exceptionParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(10, 100, 50, 50), 1, 1),
                Arguments.of(new SyncTestParams(10, 100, 50, 50), 1, 2),
                Arguments.of(new SyncTestParams(10, 100, 50, 50), 2, 1),
                Arguments.of(new SyncTestParams(10, 100, 50, 50), 2, 2),
                Arguments.of(new SyncTestParams(10, 100, 50, 50), 3, 1),
                Arguments.of(new SyncTestParams(10, 100, 50, 50), 3, 2));
    }

    private static Stream<Arguments> splitForkParams() {
        return Stream.of(
                // This seed makes the caller send the whole graph, should not be the case once we change the tip
                // definition
                Arguments.of(new SyncTestParams(4, 100, 20, 1, 4956163591276672768L)),
                Arguments.of(new SyncTestParams(4, 100, 20, 1)),
                Arguments.of(new SyncTestParams(4, 100, 1, 20)),
                Arguments.of(new SyncTestParams(4, 100, 20, 20)),
                Arguments.of(new SyncTestParams(10, 100, 50, 50)),
                Arguments.of(new SyncTestParams(10, 100, 100, 50)),
                Arguments.of(new SyncTestParams(10, 100, 50, 100)));
    }

    private static Stream<Arguments> splitForkParamsBreakingSeed() {
        return Stream.of(
                // This seed used to make the caller send the whole graph back when the definition of a tip was an
                // event with no children (self or other). Now that the definition of a tip is an event with no
                // self-child, this seed passes.
                Arguments.of(new SyncTestParams(4, 100, 20, 1, 4956163591276672768L)));
    }

    private static Stream<Arguments> largeGraphParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(10, 1000, 500, 200)),
                Arguments.of(new SyncTestParams(10, 1000, 200, 500)),
                Arguments.of(new SyncTestParams(10, 1000, 500, 500)));
    }

    private static Stream<Arguments> noCommonEventsParams() {
        return Stream.of(
                Arguments.of(new SyncTestParams(4, 0, 50, 20)),
                Arguments.of(new SyncTestParams(4, 0, 20, 50)),
                Arguments.of(new SyncTestParams(4, 0, 50, 50)),
                Arguments.of(new SyncTestParams(10, 0, 500, 200)),
                Arguments.of(new SyncTestParams(10, 0, 200, 500)),
                Arguments.of(new SyncTestParams(10, 0, 500, 500)));
    }

    private static Stream<Arguments> tipsChangeBreakingSeed() {
        return Stream.of(
                Arguments.of(new SyncTestParams(10, 0, 20, 0, 6238590233436833292L)),
                Arguments.of(new SyncTestParams(4, 10, 8, 1, 8824331216639179768L)),
                Arguments.of(new SyncTestParams(10, 0, 0, 20, -909134053413382981L)),
                Arguments.of(new SyncTestParams(10, 0, 0, 20, 5236225801504915258L)),
                Arguments.of(new SyncTestParams(4, 10, 1, 1, -3204404663467002969L)),
                Arguments.of(new SyncTestParams(10, 0, 0, 20, -4776092416980912346L)));
    }

    private static Stream<Arguments> simpleGraphBreakingSeed() {
        return Stream.of(
                Arguments.of(new SyncTestParams(4, 100, 20, 20, -5979073137457357235L)),
                Arguments.of(new SyncTestParams(10, 100, 50, 50, 1861589538493329478L)));
    }

    private static Stream<Arguments> tipExpiresBreakingSeed() {
        return Stream.of(
                Arguments.of(new SyncTestParams(10, 100, 0, 50, 1152284535185134815L)),
                Arguments.of(new SyncTestParams(10, 100, 0, 50, -8664085824668001150L)));
    }

    private static Stream<Arguments> requiredEventsExpire() {
        return Stream.of(
                Arguments.of(1, new SyncTestParams(10, 100, 0, 1000)),
                Arguments.of(1, new SyncTestParams(10, 200, 100, 1000)),
                Arguments.of(1, new SyncTestParams(10, 200, 200, 1000)),
                Arguments.of(2, new SyncTestParams(10, 100, 0, 1000)),
                Arguments.of(2, new SyncTestParams(10, 200, 100, 1000)),
                Arguments.of(2, new SyncTestParams(10, 200, 200, 1000)));
    }

    @BeforeAll
    public static void setup() throws FileNotFoundException, ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        SettingsCommon.maxTransactionCountPerEvent = 1000;
        SettingsCommon.transactionMaxBytes = 10000;

        if (platformLoggingEnabled) {
            loadLog4jContext();
        }
    }

    /**
     * Tests small, simple graphs using a standard generator and no forking sources.
     */
    @ParameterizedTest
    @MethodSource({
        "simpleGraphBreakingSeed",
        "simpleFourNodeGraphParams",
        "fourNodeGraphParams",
        "tenNodeGraphParams",
        "edgeCaseGraphParams"
    })
    void simpleGraph(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests skipping sync initialization bytes
     */
    @ParameterizedTest
    @MethodSource({"simpleFourNodeGraphParams"})
    void skipInit(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
            caller.setSendRecInitBytes(false);
            listener.setSendRecInitBytes(false);
        });

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests syncing graphs with forking event sources.
     */
    @ParameterizedTest
    @MethodSource({"fourNodeGraphParams", "tenNodeGraphParams"})
    void forkingGraph(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setCallerSupplier(
                (factory) -> new SyncNode(params.getNumNetworkNodes(), 0, factory.newForkingShuffledGenerator()));
        executor.setListenerSupplier((factory) -> new SyncNode(
                params.getNumNetworkNodes(), params.getNumNetworkNodes() - 1, factory.newForkingShuffledGenerator()));

        executor.execute();

        // Some extra events could be transferred in the case of a split fork graph. This is explicitly tested in
        // splitForkGraph()
        SyncValidator.assertRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests cases where each node has one branch of a fork. Neither node knows there is a fork until after the sync.
     */
    @ParameterizedTest
    @MethodSource({"splitForkParams", "splitForkParamsBreakingSeed"})
    void splitForkGraph(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        final int creatorToFork = 0;
        final int callerOtherParent = 1;
        final int listenerOtherParent = 2;

        executor.setCallerSupplier(
                (factory) -> new SyncNode(params.getNumNetworkNodes(), 0, factory.newStandardEmitter()));
        executor.setListenerSupplier((factory) -> new SyncNode(
                params.getNumNetworkNodes(), params.getNumNetworkNodes() - 1, factory.newStandardEmitter()));

        executor.setInitialGraphCreation((caller, listener) -> {
            caller.generateAndAdd(params.getNumCommonEvents());
            listener.generateAndAdd(params.getNumCommonEvents());

            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        executor.setCustomInitialization((caller, listener) -> {
            SplitForkGraphCreator.createSplitForkConditions(
                    params, (StandardEventEmitter) caller.getEmitter(), creatorToFork, callerOtherParent);
            SplitForkGraphCreator.createSplitForkConditions(
                    params, (StandardEventEmitter) listener.getEmitter(), creatorToFork, listenerOtherParent);
        });

        executor.execute();

        // In split fork graphs, some extra events will be sent because each node has a different tip for the same
        // creator, causing each to think the other does not have any ancestors of that creator's event when they in
        // fact do.
        SyncValidator.assertRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests syncing partitioned graphs where the caller and listener are in different partitions.
     */
    @ParameterizedTest
    @MethodSource({"partitionedGraphParams"})
    void partitionedGraph(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        final int numPartitionedNodes = (int) Math.ceil(((double) params.getNumNetworkNodes()) / 3.0);

        final List<Integer> callerPartitionNodes =
                IntStream.range(0, numPartitionedNodes).boxed().collect(Collectors.toList());
        final List<Integer> listenerPartitionNodes = IntStream.range(numPartitionedNodes, params.getNumNetworkNodes())
                .boxed()
                .collect(Collectors.toList());

        executor.setCallerSupplier(
                (factory) -> new SyncNode(params.getNumNetworkNodes(), 0, factory.newStandardEmitter()));
        executor.setListenerSupplier((factory) -> new SyncNode(
                params.getNumNetworkNodes(), params.getNumNetworkNodes() - 1, factory.newStandardEmitter()));

        executor.setInitialGraphCreation((caller, listener) -> {
            caller.generateAndAdd(params.getNumCommonEvents());
            listener.generateAndAdd(params.getNumCommonEvents());

            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        executor.setCustomInitialization((caller, listener) -> {
            PartitionedGraphCreator.setupPartitionForNode(params, caller, callerPartitionNodes);
            PartitionedGraphCreator.setupPartitionForNode(params, listener, listenerPartitionNodes);
        });

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Test a sync where the listener rejects the sync.
     */
    @ParameterizedTest
    @MethodSource({"simpleFourNodeGraphParams", "fourNodeGraphParams", "tenNodeGraphParams", "edgeCaseGraphParams"})
    void testSyncRejected(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setCustomInitialization((caller, listener) -> listener.setCanAcceptSync(false));

        executor.execute();

        SyncValidator.assertNoEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * This test verifies that no events are transferred when no booleans are received from the other node.
     */
    @ParameterizedTest
    @MethodSource("tenNodeGraphParams")
    void testNoBooleansReceived(final SyncTestParams params) {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        final ParallelExecutor callerExecutor =
                new ReplaceSyncPhaseParallelExecutor(getStaticThreadManager(), 2, 1, () -> null);
        final ParallelExecutor listenerExecutor = new CachedPoolParallelExecutor(getStaticThreadManager(), "sync-node");
        listenerExecutor.start();

        // Setup parallel executors
        executor.setCallerSupplier((factory) ->
                new SyncNode(params.getNumNetworkNodes(), 0, factory.newShuffledEmitter(), callerExecutor));
        executor.setListenerSupplier((factory) -> new SyncNode(
                params.getNumNetworkNodes(),
                params.getNumNetworkNodes() - 1,
                factory.newShuffledEmitter(),
                listenerExecutor));

        assertThrows(ParallelExecutionException.class, executor::execute, "Unexpected exception during sync.");

        SyncValidator.assertExceptionThrown(executor.getCaller(), executor.getListener());
        SyncValidator.assertNoEventsTransferred(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests that when an exception is thrown by the caller in each phase and task, an exception is propagated up for
     * both nodes and that no events are transferred.
     */
    @ParameterizedTest
    @MethodSource("exceptionParams")
    void testCallerExceptionDuringSyncPhase(final SyncTestParams params, final int phaseToThrowIn, final int taskNum)
            throws Exception {

        final SyncTestExecutor executor = new SyncTestExecutor(params);

        final ParallelExecutor callerExecutor =
                new ReplaceSyncPhaseParallelExecutor(getStaticThreadManager(), phaseToThrowIn, taskNum, () -> {
                    executor.getCaller().getConnection().disconnect();
                    throw new SocketException();
                });

        executor.setCallerSupplier((factory) ->
                new SyncNode(params.getNumNetworkNodes(), 0, factory.newShuffledEmitter(), callerExecutor));

        runExceptionDuringSyncPhase(phaseToThrowIn, taskNum, executor);
    }

    /**
     * Tests that when an exception is thrown by the listener in each phase and task, an exception is propagated up for
     * both nodes and that no events are transferred.
     */
    @ParameterizedTest
    @MethodSource("exceptionParams")
    void testListenerExceptionDuringSyncPhase(final SyncTestParams params, final int phaseToThrowIn, final int taskNum)
            throws Exception {

        final SyncTestExecutor executor = new SyncTestExecutor(params);

        final ParallelExecutor listenerExecutor =
                new ReplaceSyncPhaseParallelExecutor(getStaticThreadManager(), phaseToThrowIn, taskNum, () -> {
                    executor.getListener().getConnection().disconnect();
                    throw new SocketException();
                });

        executor.setListenerSupplier((factory) ->
                new SyncNode(params.getNumNetworkNodes(), 0, factory.newShuffledEmitter(), listenerExecutor));

        runExceptionDuringSyncPhase(phaseToThrowIn, taskNum, executor);
    }

    private void runExceptionDuringSyncPhase(
            final int phaseToThrowIn, final int taskNum, final SyncTestExecutor executor) throws Exception {

        try {
            executor.execute();
        } catch (final ParallelExecutionException e) {
            // expected, ignore
        }

        final SyncNode caller = executor.getCaller();
        final SyncNode listener = executor.getListener();

        if (!(phaseToThrowIn == 3 && taskNum == 1)) {
            SyncValidator.assertNoEventsTransferred(caller, listener);
        }
    }

    /**
     * Test a sync where, after the common events, the caller has many events one a node, and the listener has no events
     * for that node.
     */
    @ParameterizedTest
    @MethodSource({"fourNodeGraphParams", "tenNodeGraphParams", "noCommonEventsParams"})
    void testUnknownCreator(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        // The node id that is unknown to the caller after the common events are generated
        final int unknownCallerCreator = 0;

        // Fully connected, evenly balanced other parent matrix
        final List<List<Double>> normalOtherMatrix =
                OtherParentMatrixFactory.createBalancedOtherParentMatrix(params.getNumNetworkNodes());

        // Matrix that does not allow the unknown creator to be selected as an other parent
        final List<List<Double>> unknownCreatorOtherMatrix =
                OtherParentMatrixFactory.createShunnedNodeOtherParentAffinityMatrix(
                        params.getNumNetworkNodes(), unknownCallerCreator);

        executor.setCustomInitialization((caller, listener) -> {
            for (final SyncNode node : List.of(caller, listener)) {

                final GraphGenerator<?> generator = node.getEmitter().getGraphGenerator();
                generator.setOtherParentAffinity(((random, eventIndex, previousValue) -> {
                    if (eventIndex < params.getNumCommonEvents()) {
                        // Use the normal matrix for common events
                        return normalOtherMatrix;
                    } else {
                        // after common events, do not use the unknown creator as an other parent to prevent
                        // forking between the caller and listener
                        return unknownCreatorOtherMatrix;
                    }
                }));
            }
        });

        // Do not add events created by the unknown creator to the caller graph to fulfill the premise of this test
        executor.setCallerAddToGraphTest((indexedEvent -> indexedEvent.getCreatorId() != unknownCallerCreator));

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests fallen behind detection works
     */
    @Test
    void fallenBehind() throws Exception {
        final SyncTestParams params = new SyncTestParams(4, 100, 20, 20);

        final long callerMinGen = 100;
        final long callerMaxGen = 200;
        final long listenerMinGen = 300;
        final long listenerMaxGen = 400;

        runFallenBehindTest(params, callerMinGen, callerMaxGen, listenerMinGen, listenerMaxGen);
    }

    /**
     * Tests fallen behind detection works with one node at genesis
     */
    @Test
    void fallenBehindAtGenesis() throws Exception {
        final SyncTestParams params = new SyncTestParams(4, 0, 1, 100);

        final long callerMinGen = GraphGenerations.FIRST_GENERATION;
        final long callerMaxGen = GraphGenerations.FIRST_GENERATION;
        final long listenerMinGen = 200;
        final long listenerMaxGen = 300;

        runFallenBehindTest(params, callerMinGen, callerMaxGen, listenerMinGen, listenerMaxGen);
    }

    private void runFallenBehindTest(
            final SyncTestParams params,
            final long callerMinGen,
            final long callerMaxGen,
            final long listenerMinGen,
            final long listenerMaxGen)
            throws Exception {
        assertTrue(callerMinGen <= callerMaxGen, "Caller generations provided do not represent a fallen behind node.");
        assertTrue(
                listenerMinGen <= listenerMaxGen,
                "Listener generations provided do not represent a fallen behind node.");
        assertTrue(
                callerMaxGen < listenerMinGen || listenerMaxGen < callerMinGen,
                "Generations provided do not represent a fallen behind node.");

        final boolean callerFallenBehind = callerMaxGen < listenerMinGen;

        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setCallerSupplier(
                (factory) -> new SyncNode(params.getNumNetworkNodes(), 0, factory.newStandardEmitter()));
        executor.setListenerSupplier((factory) -> new SyncNode(
                params.getNumNetworkNodes(), params.getNumNetworkNodes() - 1, factory.newStandardEmitter()));

        executor.setInitialGraphCreation((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        executor.setCustomPreSyncConfiguration((c, l) -> {
            when(c.getConsensus().getMinGenerationNonAncient()).thenReturn(callerMinGen);
            when(c.getConsensus().getMaxRoundGeneration()).thenReturn(callerMaxGen);
            c.getShadowGraph().expireBelow(callerMinGen);
            when(l.getConsensus().getMinGenerationNonAncient()).thenReturn(listenerMinGen);
            when(l.getConsensus().getMaxRoundGeneration()).thenReturn(listenerMaxGen);
            l.getShadowGraph().expireBelow(listenerMinGen);
        });

        executor.execute();

        SyncValidator.assertFallenBehindDetection(callerFallenBehind, executor.getCaller());
        SyncValidator.assertFallenBehindDetection(!callerFallenBehind, executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());

        SyncValidator.assertNoEventsReceived("listener", executor.getListener());
        SyncValidator.assertNoEventsReceived("caller", executor.getCaller());
    }

    @Test
    void testBarelyNotFallenBehind() throws Exception {
        final SyncTestParams params = new SyncTestParams(4, 200, 200, 0);
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setGenerationDefinitions((caller, listener) -> {
            long listenerMaxGen = SyncUtils.getMaxGen(listener.getShadowGraph().getTips());
            // make the min non-ancient gen slightly below the max gen
            long listenerMinNonAncient = listenerMaxGen - (listenerMaxGen/10);
            long listenerMinGen = SyncUtils.getMinGen(listener.getShadowGraph()
                    .findAncestors(listener.getShadowGraph().getTips(), (e) -> true));

            // Expire everything below the listener's min non-ancient gen on the caller
            // so that the listener's maxGen == caller's min non-ancient gen
            caller.expireBelow(listenerMinNonAncient);

            long callerMaxGen = SyncUtils.getMaxGen(caller.getShadowGraph().getTips());
            // make the min non-ancient gen slightly below the max gen
            long callerMinNonAncient = callerMaxGen - (callerMaxGen/10);
            long callerMinGen = SyncUtils.getMinGen(caller.getShadowGraph()
                    .findAncestors(caller.getShadowGraph().getTips(), (e) -> true));

            assertEquals(listenerMinNonAncient, callerMinGen, "listener max gen and caller min gen should be equal.");

            when(listener.getConsensus().getMaxRoundGeneration()).thenReturn(listenerMaxGen);
            when(listener.getConsensus().getMinGenerationNonAncient()).thenReturn(listenerMinNonAncient);
            when(listener.getConsensus().getMinRoundGeneration()).thenReturn(listenerMinGen);
            when(caller.getConsensus().getMaxRoundGeneration()).thenReturn(callerMaxGen);
            when(caller.getConsensus().getMinGenerationNonAncient()).thenReturn(callerMinNonAncient);
            when(caller.getConsensus().getMinRoundGeneration()).thenReturn(callerMinGen);
        });

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Verifies that even if events are expired right before sending, they are still sent.
     */
    @Test
    void testSendExpiredEvents() throws Exception {
        final SyncTestParams params = new SyncTestParams(4, 20, 10, 0);
        final SyncTestExecutor executor = new SyncTestExecutor(params);
        final AtomicLong genToExpire = new AtomicLong(EventConstants.GENERATION_UNDEFINED);

        // before phase 3, expire all events so that expired events are sent
        executor.setCallerExecutorSupplier(() -> new SyncPhaseParallelExecutor(
                getStaticThreadManager(),
                null,
                () -> {
                    // Expire the events from the hash graph
                    List<ShadowEvent> callerTips =
                            executor.getCaller().getShadowGraph().getTips();
                    Set<ShadowEvent> allCallerEvents =
                            executor.getCaller().getShadowGraph().findAncestors(callerTips, e -> true);
                    allCallerEvents.forEach(e -> e.getEvent().clear());

                    // Expire the events from the shadow graph
                    executor.getCaller().getShadowGraph().expireBelow(genToExpire.get() + 1);
                },
                false));

        // we save the max generation of node 0, so we know what we need to expire to remove a tip
        executor.setCustomPreSyncConfiguration((caller, listener) ->
                genToExpire.set(SyncUtils.getMaxGen(caller.getShadowGraph().getTips())));

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests a situation where a tip is requested to be expired after it is used in phase 1, issue #3856
     */
    @ParameterizedTest
    @MethodSource({"tenNodeGraphParams", "tenNodeBigGraphParams", "tipExpiresBreakingSeed"})
    void tipExpiresAfterPhase1(final SyncTestParams params) throws Exception {
        final long creatorIdToExpire = 0;
        final SyncTestExecutor executor = new SyncTestExecutor(params);
        final AtomicLong maxGen = new AtomicLong(EventConstants.GENERATION_UNDEFINED);

        // node 0 should not create any events after CommonEvents
        executor.setFactoryConfig(
                (factory) -> factory.getSourceFactory().addCustomSource((index) -> index == creatorIdToExpire, () -> {
                    final StandardEventSource source0 = new StandardEventSource(false);
                    source0.setNewEventWeight((r, index, prev) -> {
                        if (index <= params.getNumCommonEvents() / 2) {
                            return 1.0;
                        } else {
                            return 0.0;
                        }
                    });
                    return source0;
                }));

        // we save the max generation of node 0, so we know what we need to expire to remove a tip
        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);

            maxGen.set(caller.getEmitter().getGraphGenerator().getMaxGeneration(creatorIdToExpire));
        });

        // before the sync, expire the tip on the listener
        executor.setCustomPreSyncConfiguration((c, l) -> {
            l.getShadowGraph().expireBelow(maxGen.get() + 1);
            when(l.getConsensus().getMinGenerationNonAncient()).thenReturn(maxGen.get() + 2);
            when(l.getConsensus().getMaxRoundGeneration()).thenReturn(maxGen.get() + 3);

            c.getShadowGraph().expireBelow(maxGen.get() - 1);
            when(c.getConsensus().getMinGenerationNonAncient()).thenReturn(maxGen.get() + 2);
            when(c.getConsensus().getMaxRoundGeneration()).thenReturn(maxGen.get() + 3);
        });

        // after phase 1, expire the tip on the caller
        final SyncPhaseParallelExecutor parallelExecutor = new SyncPhaseParallelExecutor(
                getStaticThreadManager(),
                () -> executor.getCaller().getShadowGraph().expireBelow(maxGen.get() + 1),
                null,
                true);
        executor.setExecutorSupplier(() -> parallelExecutor);

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests a situation where tips change during a sync, after phase 1
     */
    @ParameterizedTest
    @MethodSource({
        "tipsChangeBreakingSeed",
        "simpleFourNodeGraphParams",
        "fourNodeGraphParams",
        "tenNodeGraphParams",
        "edgeCaseGraphParams"
    })
    void tipsChangeAfterPhase1(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);
        final int numToAddInPhase = 10;

        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        Runnable addEvents = () -> {
            for (SyncNode node : List.of(executor.getCaller(), executor.getListener())) {
                node.generateAndAdd(numToAddInPhase);
            }
        };
        final SyncPhaseParallelExecutor parallelExecutor =
                new SyncPhaseParallelExecutor(getStaticThreadManager(), addEvents, null, true);
        executor.setExecutorSupplier(() -> parallelExecutor);

        executor.execute();

        // since we get a new set of tips before phase 3, it is possible to transfer some duplicate events that were
        // added after the initial tips were exchanged
        SyncValidator.assertRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests a situation where tips change during a sync, after phase 2
     */
    @ParameterizedTest
    @MethodSource({
        "tipsChangeBreakingSeed",
        "simpleFourNodeGraphParams",
        "fourNodeGraphParams",
        "tenNodeGraphParams",
        "edgeCaseGraphParams"
    })
    void tipsChangeAfterPhase2(final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);
        final int numToAddInPhase = 10;

        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        Runnable addEvents = () -> {
            for (SyncNode node : List.of(executor.getCaller(), executor.getListener())) {
                node.generateAndAdd(numToAddInPhase);
            }
        };
        final SyncPhaseParallelExecutor parallelExecutor =
                new SyncPhaseParallelExecutor(getStaticThreadManager(), null, addEvents, true);
        executor.setExecutorSupplier(() -> parallelExecutor);

        executor.execute();

        // since we get a new set of tips before phase 3, it is possible to transfer some duplicate events that were
        // added after the initial tips were exchanged
        SyncValidator.assertRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests scenarios in which events that need to be sent to the peer are requested to be expired before they are
     * sent. Because generations are reserved in a sync, the events should not be expired while a sync is in progress.
     *
     * @param expireAfterPhase
     * 		the phase after which events that need to be sent should be requested to be expired
     * @param params
     * 		Sync parameters
     */
    @ParameterizedTest
    @MethodSource("requiredEventsExpire")
    void requiredEventsExpire(final int expireAfterPhase, final SyncTestParams params) throws Exception {
        final SyncTestExecutor executor = new SyncTestExecutor(params);
        final AtomicLong genToExpire = new AtomicLong(0);

        // Set the generation to expire such that half the listener's graph, and therefore some events that need
        // to be sent to the caller, will be expired
        executor.setCustomPreSyncConfiguration(
                (c, l) -> genToExpire.set(l.getEmitter().getGraphGenerator().getMaxGeneration(0) / 2));

        // Expire events from the listener's graph after the supplied phase
        final Runnable expireEvents =
                () -> executor.getListener().getShadowGraph().expireBelow(genToExpire.get());
        final SyncPhaseParallelExecutor parallelExecutor = new SyncPhaseParallelExecutor(
                getStaticThreadManager(),
                expireAfterPhase == 1 ? expireEvents : null,
                expireAfterPhase == 2 ? expireEvents : null,
                true);
        executor.setExecutorSupplier(() -> parallelExecutor);

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());
    }

    /**
     * Tests sync with graphs where one node provides old events as other parents
     */
    @ParameterizedTest
    @MethodSource({"largeGraphParams"})
    void testNodeProvidingOldParents(final SyncTestParams params) throws Exception {
        runOldParentGraphTest(
                params,
                (factory) ->
                        // Set up the factory to create a custom event source for creator 0
                        factory.getSourceFactory()
                                .addCustomSource(
                                        (index) -> index == 0, () -> EventSourceFactory.newStandardEventSource()
                                                .setProvidedOtherParentAgeDistribution(integerPowerDistribution(0.2, 3))
                                                .setRecentEventRetentionSize(300)));
    }

    /**
     * Tests sync with graphs where one node provides old events as other parents
     */
    @ParameterizedTest
    @MethodSource({"largeGraphParams"})
    void testNodeAskingForOldParents(final SyncTestParams params) throws Exception {
        runOldParentGraphTest(
                params,
                (factory) ->
                        // Set up the factory to create a custom event source for creator 0
                        factory.getSourceFactory()
                                .addCustomSource(
                                        (index) -> index == 0, () -> EventSourceFactory.newStandardEventSource()
                                                .setRequestedOtherParentAgeDistribution(
                                                        integerPowerDistribution(0.02, 100))
                                                .setRecentEventRetentionSize(300)));
    }

    private void runOldParentGraphTest(final SyncTestParams params, final Consumer<EventEmitterFactory> factoryConfig)
            throws Exception {

        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setFactoryConfig(factoryConfig);

        executor.setCallerSupplier(
                (factory) -> new SyncNode(params.getNumNetworkNodes(), 0, factory.newStandardFromSourceFactory()));
        executor.setListenerSupplier((factory) -> new SyncNode(
                params.getNumNetworkNodes(), params.getNumNetworkNodes() - 1, factory.newStandardFromSourceFactory()));

        executor.setInitialGraphCreation((caller, listener) -> {
            for (final SyncNode node : List.of(caller, listener)) {
                node.generateAndAdd(params.getNumCommonEvents());
                node.setSaveGeneratedEvents(true);
            }
        });

        executor.execute();

        SyncValidator.assertOnlyRequiredEventsTransferred(executor.getCaller(), executor.getListener());
    }

    @ParameterizedTest
    @MethodSource("fourNodeGraphParams")
    void testConnectionClosedBehavior(final SyncTestParams params) {
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        // after phase 2, close the connection
        final SyncPhaseParallelExecutor parallelExecutor = new SyncPhaseParallelExecutor(
                getStaticThreadManager(),
                null,
                () -> executor.getCaller().getConnection().disconnect(),
                false);

        executor.setCallerSupplier((factory) ->
                new SyncNode(params.getNumNetworkNodes(), 0, factory.newShuffledFromSourceFactory(), parallelExecutor));

        try {
            executor.execute();
        } catch (Exception e) {
            final Exception syncException = executor.getCaller().getSyncException();
            Throwable wrappedException = (syncException != null) ? syncException.getCause() : null;
            while (wrappedException != null) {
                assertFalse(
                        NullPointerException.class.isAssignableFrom(wrappedException.getClass()),
                        "Should not have received a NullPointerException for a closed connection error.");
                wrappedException = wrappedException.getCause();
            }
            return;
        }
        fail("Expected an exception to be thrown when the connection broke.");
    }

    /**
     * Tests if a sync is interrupted because it takes too long. The caller has 300 events to send, but the listener
     * sleeps after reading every event. Eventually, the sync will time out and will be aborted.
     */
    @Test
    @Disabled("Test takes a minute to finish, so disabled by default")
    void testSyncTimeExceeded() {
        final SyncTestParams params = new SyncTestParams(10, 1000, 500, 200);
        final SyncTestExecutor executor = new SyncTestExecutor(params);
        final int sleep = 1000;

        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        executor.setCustomInitialization((c, l) -> l.setSleepAfterEventReadMillis(sleep));

        assertThrows(Exception.class, executor::execute, "the sync should time out and an exception should be thrown");
    }

    /**
     * Tests if a sync is aborted when there is any issue on the readers side (in this case a socket timeout), while the
     * listener is blocked while writing to the socket, because the buffer is full. The only was to unlock the writer is
     * to close the connection, which is what the synchronizer does.
     */
    @Test
    @Disabled("Not reliable enough for CCI")
    void testTimoutWriterStuck() {
        final SyncTestParams params = new SyncTestParams(10, 1000, 5000, 200);
        final SyncTestExecutor executor = new SyncTestExecutor(params);
        final int sleep = 5000;

        executor.setConnectionFactory(ConnectionFactory::createSocketConnections);

        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });

        executor.setCustomInitialization((c, l) -> l.setSleepAfterEventReadMillis(sleep));

        assertThrows(
                Exception.class,
                executor::execute,
                "the socket read should time out and an exception should be thrown");
    }

    /**
     * Tests that events from a signed state are not gossiped and that such a sync is properly aborted
     */
    @Test
    void signedStateEvents() throws Exception {
        final SyncTestParams params = new SyncTestParams(10, 50, 2, 1);
        final SyncTestExecutor executor = new SyncTestExecutor(params);

        executor.setGraphCustomization((caller, listener) -> {
            caller.setSaveGeneratedEvents(true);
            listener.setSaveGeneratedEvents(true);
        });
        // the caller will have only signed state events
        executor.setCustomPreSyncConfiguration((caller, listener) -> {
            caller.getGeneratedEvents().forEach(EventImpl::markAsSignedStateEvent);
        });

        executor.execute();

        // the caller should not have sent any events to the listener
        SyncValidator.assertNoEventsReceived(executor.getListener());
        SyncValidator.assertStreamsEmpty(executor.getCaller(), executor.getListener());

        // both should be aware that the sync was aborted
        assertFalse(executor.getCaller().getSynchronizerReturn());
        assertFalse(executor.getListener().getSynchronizerReturn());
    }
}
