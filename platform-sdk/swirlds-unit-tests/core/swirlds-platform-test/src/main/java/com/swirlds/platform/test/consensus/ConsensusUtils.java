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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.components.ConsensusWrapper;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.test.NoOpConsensusMetrics;
import com.swirlds.platform.test.NoOpIntakeCycleStats;
import com.swirlds.platform.test.event.EventUtils;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.TestSequence;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A class containing utilities for consensus tests.
 */
public abstract class ConsensusUtils {

    public static final ConsensusMetrics NOOP_CONSENSUS_METRICS = new NoOpConsensusMetrics();
    public static final IntakeCycleStats NOOP_INTAKE_CYCLE_STATS = new NoOpIntakeCycleStats();
    public static final BiConsumer<Long, Long> NOOP_MINGEN = (l1, l2) -> {};

    private static final Random SEED_GENERATOR = new Random();

    /**
     * Apply a number of events from this emitter to a hashgraph. Return a list of all consensus events.
     *
     * @param emitter
     * 		an event emitter that will supply events
     * @param consensus
     * 		the consensus object
     * @param numberOfEvents
     * 		the number of events to apply
     * @return a list of all consensus events
     */
    public static List<IndexedEvent> applyEventsToConsensus(
            final EventEmitter<?> emitter, final Consensus consensus, final long numberOfEvents) {
        return applyEventsToConsensus(emitter, consensus, numberOfEvents, e -> {});
    }

    /**
     * Apply a number of events from this emitter to a hashgraph. Return a list of all consensus events, each of which
     * knows which sequence it was generated in.
     *
     * @param emitter
     * 		an event emitter that will supply events
     * @param consensus
     * 		the consensus object
     * @param numberOfEvents
     * 		the number of events to apply
     * @param sequenceNum
     * 		the current test sequence number
     * @return a list of all consensus events
     */
    public static List<IndexedEvent> applyEventsToConsensus(
            final EventEmitter<?> emitter,
            final Consensus consensus,
            final long numberOfEvents,
            final int sequenceNum) {
        return applyEventsToConsensus(emitter, consensus, numberOfEvents, e -> e.setSequenceNum(sequenceNum));
    }

    /**
     * Apply a number of events from this emitter to a hashgraph. Return a list of all consensus events.
     *
     * @param emitter
     * 		an event emitter that will supply events
     * @param consensus
     * 		the consensus object
     * @param numberOfEvents
     * 		the number of events to apply
     * @param preConsConsumer
     * 		a consumer of each event post generation but before being added to consensus
     * @return a list of all consensus events
     */
    public static List<IndexedEvent> applyEventsToConsensus(
            final EventEmitter<?> emitter,
            final Consensus consensus,
            final long numberOfEvents,
            final Consumer<IndexedEvent> preConsConsumer) {

        final List<IndexedEvent> consensusEvents = new LinkedList<>();

        for (int i = 0; i < numberOfEvents; i++) {
            final IndexedEvent genEvent = emitter.emitEvent();
            preConsConsumer.accept(genEvent);
            final List<EventImpl> events =
                    consensus.addEvent(genEvent, emitter.getGraphGenerator().getAddressBook());
            if (events != null) {
                for (final EventImpl event : events) {
                    consensusEvents.add((IndexedEvent) event);
                }
            }
        }

        return consensusEvents;
    }

    public static List<ConsensusRound> applyEventsToConsensusUsingWrapper(
            final EventEmitter<?> emitter, final Consensus consensus, final long numberOfEvents) {

        final List<ConsensusRound> allConsensusRounds = new LinkedList<>();

        final ConsensusWrapper wrapper = new ConsensusWrapper(() -> consensus);

        for (int i = 0; i < numberOfEvents; i++) {
            final IndexedEvent genEvent = emitter.emitEvent();
            final List<ConsensusRound> rounds =
                    wrapper.addEvent(genEvent, emitter.getGraphGenerator().getAddressBook());
            if (rounds != null) {
                allConsensusRounds.addAll(rounds);
            }
        }

        return allConsensusRounds;
    }

    /**
     * Apply a number of events from this emitter to a hashgraph. Return a list of all consensus events.
     *
     * @param emitter
     * 		an event emitter that will supply events
     * @param consensus
     * 		the consensus object
     * @param numberOfEvents
     * 		the number of events to apply
     * @return a list of all consensus events
     */
    public static List<IndexedEvent> applyEventsToRestartConsensus(
            final EventEmitter<?> emitter, final ConsensusWithShadowGraph consensus, final long numberOfEvents) {

        final List<IndexedEvent> consensusEvents = new LinkedList<>();

        for (int i = 0; i < numberOfEvents; i++) {
            final IndexedEvent genEvent = emitter.emitEvent();
            fixReconnectParentLinks(genEvent, consensus.getShadowGraph());

            final List<EventImpl> events =
                    consensus.addEvent(genEvent, emitter.getGraphGenerator().getAddressBook());
            if (events != null) {
                for (final EventImpl event : events) {
                    consensusEvents.add((IndexedEvent) event);
                }
            }
        }

        return consensusEvents;
    }

    /**
     * Construct a simple Consensus object.
     */
    public static ConsensusImpl buildSimpleConsensus(final AddressBook addressBook) {
        return buildSimpleConsensus(addressBook, NOOP_MINGEN);
    }

    public static ConsensusImpl buildSimpleConsensus(
            final AddressBook addressBook, final BiConsumer<Long, Long> minGenConsumer) {
        return new ConsensusImpl(
                ConfigurationHolder.getConfigData(ConsensusConfig.class),
                NOOP_CONSENSUS_METRICS,
                minGenConsumer,
                addressBook);
    }

    /**
     * Runs one or more iterations of a consensus test definition. If one or more seeds are provided, each seed is used
     * once resulting in {@code seeds.length} number of iterations. If no seeds are provided, {@code iterations}
     * iterations are run where each iteration generates a new seed. The seed is used for weight calculation and event
     * generation.
     *
     * @param testDefinition
     * 		the consensus test definition to use
     * @param iterations
     * 		the number of iterations to run if no seeds are provided
     * @param seeds
     * 		specific seed(s) to use
     */
    public static void testConsensus(
            final ConsensusTestDefinition testDefinition, final int iterations, final long... seeds) {
        if (seeds != null && seeds.length > 0) {
            for (final long seed : seeds) {

                System.out.printf(
                        "%s: %s nodes, seed = %sL%n",
                        testDefinition.getTestName(), testDefinition.getNumberOfNodes(), seed);

                testConsensusWithSeed(testDefinition, seed);
            }
        }

        for (int i = 1; i <= iterations; i++) {
            final long seed = SEED_GENERATOR.nextLong();
            System.out.printf(
                    "%s: %s nodes, Iteration %s / %s, seed = %sL%n",
                    testDefinition.getTestName(), testDefinition.getNumberOfNodes(), i, iterations, seed);

            testConsensusWithSeed(testDefinition, seed);
        }
    }

    /**
     * Runs a single iteration of a consensus test definition using the provided {@code seed}.
     *
     * @param testDefinition
     * 		the test to execute
     * @param seed
     * 		the seed to use for randomization
     */
    private static void testConsensusWithSeed(final ConsensusTestDefinition testDefinition, final long seed) {
        testDefinition.setSeed(seed);

        testConsensusSingleIteration(
                testDefinition.getNode1EventEmitter(),
                testDefinition.getNode2EventEmitter(),
                seed,
                testDefinition.getTestSequences());
    }

    /**
     * Test consensus using an event emitter, single iteration. This is intended to help reproduce bugs for particular
     * seeds.
     *
     * @param node1Emitter
     * 		a graph emitter that provides a stream of events from a graph
     * @param node2Emitter
     * 		a graph emitter that provides a stream of events from a graph
     * @param seed
     * 		the seed that deterministically defines the test
     * @param sequences
     * 		one or more test sequences
     */
    public static void testConsensusSingleIteration(
            final EventEmitter<?> node1Emitter,
            final EventEmitter<?> node2Emitter,
            final long seed,
            final Collection<TestSequence> sequences) {
        assertFalse(
                sequences.isEmpty(), "Improper consensus test definition: at least one test sequence must be defined.");

        final ConsensusTestContext context = new ConsensusTestContext(seed, node1Emitter, node2Emitter);
        for (final TestSequence sequence : sequences) {
            context.runSequence(sequence);
        }
    }

    public static List<SingleConsensusChecker> getSingleConsensusCheckers() {
        final List<SingleConsensusChecker> list = new LinkedList<>();
        list.add(EventUtils::areEventsConsensusEvents);
        list.add(new TimestampChecker());
        return list;
    }

    public static void checkGenerations(final GraphGenerations gg1, final GraphGenerations gg2) {
        checkGenerations(gg1, gg2, false);
    }

    public static void checkGenerations(
            final GraphGenerations gg1, final GraphGenerations gg2, final boolean skipMinRound) {
        assertTrue(
                gg1.getMinRoundGeneration() >= GraphGenerations.FIRST_GENERATION,
                "minRoundGeneration cannot be smaller than " + GraphGenerations.FIRST_GENERATION);
        assertTrue(
                gg1.getMinGenerationNonAncient() >= gg1.getMinRoundGeneration(),
                "minGenNonAncient cannot be smaller than minRoundGeneration");
        assertTrue(
                gg1.getMaxRoundGeneration() >= gg1.getMinGenerationNonAncient(),
                "maxRoundGeneration cannot be smaller than minGenNonAncient");

        if (!skipMinRound) { // after a restart, min round will not be equal
            assertEquals(
                    gg1.getMinRoundGeneration(), gg2.getMinRoundGeneration(), "minRoundGeneration should be equal");
        }
        assertEquals(
                gg1.getMinGenerationNonAncient(), gg2.getMinGenerationNonAncient(), "minGenNonAncient should be equal");
        assertEquals(gg1.getMaxRoundGeneration(), gg2.getMaxRoundGeneration(), "maxRoundGeneration should be equal");
    }

    @Deprecated
    public static boolean isConsensusEquivalent(
            final EventEmitter<?> emitter1,
            final EventEmitter<?> emitter2,
            final Consensus consensus1,
            final Consensus consensus2,
            final int numberOfEvents,
            final Iterable<SingleConsensusChecker> singleConsensusCheckers) {

        final List<IndexedEvent> consensusEvents1 = applyEventsToConsensus(emitter1, consensus1, numberOfEvents);

        final List<IndexedEvent> consensusEvents2 = applyEventsToConsensus(emitter2, consensus2, numberOfEvents);

        boolean checksPassed = consensusEvents1.equals(consensusEvents2);
        for (final SingleConsensusChecker checker : singleConsensusCheckers) {
            checksPassed &= checker.check(consensusEvents1);
        }
        ConsensusUtils.checkGenerations(consensus1, consensus2);

        return checksPassed;
    }

    @Deprecated
    public static boolean isRestartConsensusEquivalent(
            final EventEmitter<?> emitter1,
            final EventEmitter<?> emitter2,
            final Consensus consensus1,
            final ConsensusWithShadowGraph consensus2,
            final int numberOfEvents,
            final Iterable<SingleConsensusChecker> singleConsensusCheckers) {

        final List<IndexedEvent> consensusEvents1 = applyEventsToConsensus(emitter1, consensus1, numberOfEvents);

        final List<IndexedEvent> consensusEvents2 = applyEventsToRestartConsensus(emitter2, consensus2, numberOfEvents);

        boolean checksPassed = consensusEvents1.equals(consensusEvents2);
        for (final SingleConsensusChecker checker : singleConsensusCheckers) {
            checksPassed &= checker.check(consensusEvents1);
        }
        ConsensusUtils.checkGenerations(consensus1, consensus2, true);

        return checksPassed;
    }

    /**
     * During a reconnect test, separate copies of events are created for the signed state to reconnect from. The events
     * created by the emitter will not link to them, so those links need to be fixed before the event can be added to
     * Consensus.
     */
    private static void fixReconnectParentLinks(final EventImpl e, final ShadowGraph shadowGraph) {
        fixReconnectParentLink(e.getSelfParent(), e::setSelfParent, shadowGraph);
        fixReconnectParentLink(e.getOtherParent(), e::setOtherParent, shadowGraph);
    }

    private static void fixReconnectParentLink(
            final EventImpl p, final Consumer<EventImpl> setter, final ShadowGraph shadowGraph) {
        if (p != null) {
            final EventImpl consP = shadowGraph.hashgraphEvent(p.getBaseHash());
            if (p != consP) {
                setter.accept(consP);
            }
        }
    }
}
