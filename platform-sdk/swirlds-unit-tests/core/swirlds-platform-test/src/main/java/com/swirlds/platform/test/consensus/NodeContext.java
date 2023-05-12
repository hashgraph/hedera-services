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

package com.swirlds.platform.test.consensus;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.system.address.Address;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.eventhandling.SignedStateEventsAndGenerations;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.event.EventUtils;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.emitter.CollectingEventEmitter;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import com.swirlds.platform.test.event.source.EventSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

public class NodeContext {

    /** The event emitter that produces events. */
    private final EventEmitter<?> eventEmitter;

    /** The collection event emitter that wraps {@link #eventEmitter}. */
    private final CollectingEventEmitter collectingEmitter;

    /** The instance to apply events to. */
    private final TestIntake intake;

    /** A map from test sequence number (1 based) to the list of events generated in that sequence */
    private final Map<Integer, List<IndexedEvent>> allEventsBySeq;

    /**
     * A map from test sequence number (1 based) to the list of events generated in that sequence that reached
     * consensus
     */
    private final Map<Integer, List<IndexedEvent>> consensusEventsBySeq;

    private final SignedStateEventsAndGenerations eventsAndGenerations;
    private final Random random;
    private boolean debug;

    public NodeContext(
            final EventEmitter<?> eventEmitter,
            final CollectingEventEmitter collectingEmitter,
            final SignedStateEventsAndGenerations eventsAndGenerations,
            final TestIntake intake) {
        this.eventEmitter = eventEmitter;
        this.collectingEmitter = collectingEmitter;
        this.allEventsBySeq = new HashMap<>();
        this.consensusEventsBySeq = new HashMap<>();
        this.eventsAndGenerations = eventsAndGenerations;
        this.intake = intake;
        this.random = new Random();
    }

    /**
     * Creates a new instance with a freshly seeded {@link EventEmitter}.
     *
     * @param random
     * 		source of randomness for the {@link EventEmitter}
     * @param eventEmitter
     * 		the emitter of events
     */
    public static NodeContext genesisContext(final RandomGenerator random, final EventEmitter<?> eventEmitter) {
        final EventEmitter<?> eventEmitterCopy = eventEmitter.cleanCopy(random.nextLong());
        final SignedStateEventsAndGenerations eventsAndGenerations =
                new SignedStateEventsAndGenerations(ConfigurationHolder.getConfigData(ConsensusConfig.class));
        return new NodeContext(
                eventEmitterCopy,
                new CollectingEventEmitter(eventEmitterCopy),
                eventsAndGenerations,
                new TestIntake(
                        eventEmitterCopy.getGraphGenerator().getAddressBook(),
                        eventsAndGenerations::addRoundGeneration));
    }

    /**
     * Simulates a restart on a node by creating a new ConsensusImpl with events and generations stored in
     * SignedStateEventsAndGenerations
     */
    public void restart(final Path stateDir) throws ConstructableRegistryException, IOException {
        // clear all generators
        collectingEmitter.reset();

        // create a signed state from current consensus
        // We create a copy of the signed state like in a restart or reconnect to closely mimic a restart or reconnect
        final SignedState signedState = EventUtils.serializeDeserialize(
                stateDir,
                EventUtils.createSignedState(
                        List.of(),
                        eventsAndGenerations,
                        collectingEmitter.getGraphGenerator().getAddressBook()));

        // we need to convert the events to IndexedEvents because of the generator
        final IndexedEvent[] indexedEvents = EventUtils.convertEvents(signedState);

        // now that we have used the data we need to create a signed state, we can clear events and generations
        eventsAndGenerations.clear();
        // and load the signed state data in
        eventsAndGenerations.loadDataFromSignedState(signedState);

        // load events from signed state into the sources
        for (final Address address : collectingEmitter.getGraphGenerator().getAddressBook()) {
            final EventSource<?> source = collectingEmitter.getGraphGenerator().getSource((int) address.getId());
            final List<IndexedEvent> eventsByCreator = Arrays.stream(indexedEvents)
                    .filter(e -> e.getCreatorId() == address.getId())
                    .toList();
            eventsByCreator.forEach(e -> source.setLatestEvent(random, e));
        }

        // intake loads in the new signed state
        intake.loadFromSignedState(signedState);
    }

    /**
     * Applies a number of events to this {@link NodeContext}'s consensus and keeps track of which sequence each event
     * was created in and reached consensus in.
     *
     * @param sequenceNum
     * 		the 1 based sequence number these events are applied in
     * @param numberOfEvents
     * 		the number of events to generate and apply to consensus
     */
    public void applyEventsToConsensus(final int sequenceNum, final long numberOfEvents) {
        for (int i = 0; i < numberOfEvents; i++) {
            final IndexedEvent genEvent = collectingEmitter.emitEvent();
            genEvent.setSequenceNum(sequenceNum);
            intake.addLinkedEvent(genEvent);
        }

        if (debug) {
            intake.printRoundSizes();
        }

        final List<IndexedEvent> newConsensus = intake.getConsensusRounds().stream()
                .flatMap(r -> r.getConsensusEvents().stream())
                .map(IndexedEvent.class::cast)
                .toList();
        intake.getConsensusRounds().clear();

        final List<IndexedEvent> events = collectingEmitter.getCollectedEvents();
        if (sequenceNum > 1) {
            final List<IndexedEvent> prevSeqEvents = allEventsBySeq.get(sequenceNum - 1);
            events.removeAll(prevSeqEvents);
        }
        allEventsBySeq.put(sequenceNum, events);

        // Add each consensus event to the map of sequence consensus events based
        // on the sequence they were created in. Some events created in the previous
        // sequence are likely to reach consensus in the current sequence.
        consensusEventsBySeq.put(sequenceNum, new LinkedList<>());
        for (final IndexedEvent consEvent : newConsensus) {
            consensusEventsBySeq.get(consEvent.getSequenceNum()).add(consEvent);
        }

        eventsAndGenerations.addEvents((List<EventImpl>) (List<?>) newConsensus);
        eventsAndGenerations.expire();
    }

    public EventEmitter<?> getEventEmitter() {
        return eventEmitter;
    }

    public Consensus getConsensus() {
        return intake.getConsensus();
    }

    public List<IndexedEvent> getConsensusEventsInSequence(final int sequenceNum) {
        return consensusEventsBySeq.get(sequenceNum);
    }

    public List<IndexedEvent> getAllEventsInSequence(final int sequenceNum) {
        return allEventsBySeq.get(sequenceNum);
    }

    public void setDebug(final boolean debug) {
        this.debug = debug;
    }
}
