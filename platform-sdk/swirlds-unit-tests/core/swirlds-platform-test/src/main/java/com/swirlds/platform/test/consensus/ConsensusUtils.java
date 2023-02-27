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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.NoOpConsensusMetrics;
import com.swirlds.platform.test.NoOpIntakeCycleStats;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import com.swirlds.platform.test.event.source.EventSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

/** A class containing utilities for consensus tests. */
public abstract class ConsensusUtils {

    public static final ConsensusMetrics NOOP_CONSENSUS_METRICS = new NoOpConsensusMetrics();
    public static final IntakeCycleStats NOOP_INTAKE_CYCLE_STATS = new NoOpIntakeCycleStats();
    public static final BiConsumer<Long, Long> NOOP_MINGEN = (l1, l2) -> {};

    public static List<ConsensusRound> applyEventsToConsensus(
            final EventEmitter<?> emitter, final Consensus consensus, final long numberOfEvents) {

        final List<ConsensusRound> allConsensusRounds = new LinkedList<>();

        for (int i = 0; i < numberOfEvents; i++) {
            final IndexedEvent genEvent = emitter.emitEvent();
            final List<ConsensusRound> rounds =
                    consensus.addEvent(genEvent, emitter.getGraphGenerator().getAddressBook());
            if (rounds != null) {
                allConsensusRounds.addAll(rounds);
            }
        }

        return allConsensusRounds;
    }

    /** Construct a simple Consensus object. */
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

    /**
     * Load events from a signed state into a generator, so that they can be used as other parents
     *
     * @param signedState the source of events
     * @param generator the generator to load into to
     * @param random a source of randomness
     */
    public static void loadEventsIntoGenerator(
            final SignedState signedState, final GraphGenerator<?> generator, final Random random) {
        Instant lastTimestamp = Instant.MIN;
        for (final Address address : generator.getAddressBook()) {
            final EventSource<?> source = generator.getSource((int) address.getId());
            final List<IndexedEvent> eventsByCreator = Arrays.stream(signedState.getEvents())
                    .map(IndexedEvent.class::cast)
                    .filter(e -> e.getCreatorId() == address.getId())
                    .toList();
            eventsByCreator.forEach(e -> source.setLatestEvent(random, e));
            final Instant creatorMax = eventsByCreator.stream()
                    .max(Comparator.naturalOrder())
                    .map(IndexedEvent::getTimeCreated)
                    .orElse(Instant.MIN);
            lastTimestamp = Collections.max(Arrays.asList(lastTimestamp, creatorMax));
        }
        generator.setPreviousTimestamp(lastTimestamp);
    }
}
