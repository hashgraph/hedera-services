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

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.NoOpConsensusMetrics;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import com.swirlds.platform.test.fixtures.event.IndexedEvent;
import com.swirlds.platform.test.fixtures.event.generator.GraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/** A class containing utilities for consensus tests. */
public abstract class ConsensusUtils {

    public static final ConsensusMetrics NOOP_CONSENSUS_METRICS = new NoOpConsensusMetrics();

    public static List<ConsensusRound> applyEventsToConsensus(
            final EventEmitter<?> emitter, final Consensus consensus, final long numberOfEvents) {

        final List<ConsensusRound> allConsensusRounds = new LinkedList<>();

        for (int i = 0; i < numberOfEvents; i++) {
            final IndexedEvent genEvent = emitter.emitEvent();
            final List<ConsensusRound> rounds = consensus.addEvent(genEvent);
            if (rounds != null) {
                allConsensusRounds.addAll(rounds);
            }
        }

        return allConsensusRounds;
    }

    public static ConsensusImpl buildSimpleConsensus(final AddressBook addressBook) {
        return new ConsensusImpl(
                ConfigurationHolder.getConfigData(ConsensusConfig.class), NOOP_CONSENSUS_METRICS, addressBook);
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
        loadEventsIntoGenerator(signedState.getEvents(), generator, random);
    }

    public static void loadEventsIntoGenerator(
            final EventImpl[] events, final GraphGenerator<?> generator, final Random random) {
        Instant lastTimestamp = Instant.MIN;
        for (final Address address : generator.getAddressBook()) {
            final EventSource<?> source = generator.getSource(address.getNodeId());
            final List<IndexedEvent> eventsByCreator = Arrays.stream(events)
                    .map(IndexedEvent.class::cast)
                    .filter(e -> e.getCreatorId().id() == address.getNodeId().id())
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
