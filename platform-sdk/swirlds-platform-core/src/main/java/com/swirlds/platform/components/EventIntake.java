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

package com.swirlds.platform.components;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndLogIfInterrupted;
import static com.swirlds.logging.LogMarker.INTAKE_EVENT;
import static com.swirlds.logging.LogMarker.STALE_EVENTS;
import static com.swirlds.logging.LogMarker.SYNC;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamSequencer;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.sync.ShadowGraph;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for adding events to {@link Consensus} and notifying event observers, including
 * {@link ConsensusRoundHandler} and {@link com.swirlds.platform.eventhandling.PreConsensusEventHandler}.
 */
public class EventIntake {
    private static final Logger logger = LogManager.getLogger(EventIntake.class);
    /** The ID of this node */
    private final NodeId selfId;

    private final EventLinker eventLinker;
    /** A functor that provides access to a {@code Consensus} instance. */
    private final Supplier<Consensus> consensusSupplier;

    private final ConsensusWrapper consensusWrapper;
    /** A reference to the initial address book for this node. */
    private final AddressBook addressBook;
    /** An {@link EventObserverDispatcher} instance */
    private final EventObserverDispatcher dispatcher;
    /** Collects statistics */
    private final IntakeCycleStats stats;
    /** Stores events, expires them, provides event lookup methods */
    private final ShadowGraph shadowGraph;

    private final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();

    /**
     * Writes preconsensus events to disk.
     */
    private final PreConsensusEventWriter preConsensusEventWriter;

    /**
     * Constructor
     *
     * @param selfId                  the ID of this node
     * @param consensusSupplier       a functor which provides access to the {@code Consensus} interface
     * @param addressBook             the current address book
     * @param dispatcher              an event observer dispatcher
     * @param preConsensusEventWriter a writer for preconsensus events
     */
    public EventIntake(
            final NodeId selfId,
            final EventLinker eventLinker,
            final Supplier<Consensus> consensusSupplier,
            final AddressBook addressBook,
            final EventObserverDispatcher dispatcher,
            final IntakeCycleStats stats,
            final ShadowGraph shadowGraph,
            final PreConsensusEventWriter preConsensusEventWriter) {
        this.selfId = selfId;
        this.eventLinker = eventLinker;
        this.consensusSupplier = consensusSupplier;
        this.consensusWrapper = new ConsensusWrapper(consensusSupplier);
        this.addressBook = addressBook;
        this.dispatcher = dispatcher;
        this.stats = stats;
        this.shadowGraph = shadowGraph;
        this.preConsensusEventWriter = preConsensusEventWriter;
    }

    /**
     * Adds an event received from gossip that has been validated without its parents. It must be linked to its parents
     * before being added to consensus. The linking is done by the {@link EventLinker} provided.
     *
     * @param event the event
     */
    public void addUnlinkedEvent(final GossipEvent event) {
        stats.receivedUnlinkedEvent();
        dispatcher.receivedEvent(event);
        stats.dispatchedReceived();
        eventLinker.linkEvent(event);
        stats.doneLinking();
        while (eventLinker.hasLinkedEvents()) {
            addEvent(eventLinker.pollLinkedEvent());
        }
    }

    /**
     * Add an event to the hashgraph
     *
     * @param event an event to be added
     */
    public void addEvent(final EventImpl event) {
        // an expired event will cause ShadowGraph to throw an exception, so we just to discard it
        if (consensus().isExpired(event)) {
            return;
        }
        stats.startIntakeAddEvent();
        if (!StaticValidators.isValidTimeCreated(event)) {
            event.clear();
            return;
        }

        // TODO we should be able to disable this via a setting
        // Enqueue the event to be written to disk as early as possible in this process to minimize latency
        // when it comes to handling transactions that may be gated by this event's durability.
        sequencer.assignStreamSequenceNumber(event);
        abortAndLogIfInterrupted(
                preConsensusEventWriter::writeEvent,
                event,
                "Interrupted while attempting to enqueue preconsensus event for writing");

        stats.doneValidation();
        logger.debug(SYNC.getMarker(), "{} sees {}", selfId, event);
        dispatcher.preConsensusEvent(event);
        logger.debug(INTAKE_EVENT.getMarker(), "Adding {} ", event::toShortString);
        stats.dispatchedPreConsensus();
        final long minGenNonAncientBeforeAdding = consensus().getMinGenerationNonAncient();
        // #5762 if we cannot calculate its roundCreated, then we use the one that was sent to us
        final boolean hasAtLeastOneParent = event.getSelfParentHash() != null || event.getOtherParentHash() != null;
        final boolean noParentsFound = event.getSelfParent() == null && event.getOtherParent() == null;
        if (hasAtLeastOneParent && noParentsFound) {
            if (event.getBaseEvent().isRoundCreatedSet()) {
                // we then use the round created sent to us
                event.setRoundCreated(event.getBaseEvent().getRoundCreated());
            } else {
                logger.error(
                        LogMarker.EXCEPTION.getMarker(),
                        "cannot determine round created for event {}",
                        event::toMediumString);
            }
        }
        // record the event in the hashgraph, which results in the events in consEvent reaching consensus
        final List<ConsensusRound> consRounds = consensusWrapper.addEvent(event, addressBook);
        // #5762 after we calculate roundCreated, se set its value in GossipEvent so that it can be shared with other
        // nodes
        event.getBaseEvent().setRoundCreated(event.getRoundCreated());
        stats.addedToConsensus();
        dispatcher.eventAdded(event);
        stats.dispatchedAdded();
        if (consRounds != null) {

            abortAndLogIfInterrupted(
                    preConsensusEventWriter::setMinimumGenerationNonAncient,
                    consensus().getMinGenerationNonAncient(),
                    "Interrupted while attempting to enqueue change in minimum generation non-ancient");

            // TODO this needs a metric
            // All rounds that reach consensus at the same time will have the same keystone event,
            // so we only need to wait for it to become flushed once.
            final EventImpl keystoneEvent = consRounds.get(0).getKeystoneEvent();
            preConsensusEventWriter.requestFlush(keystoneEvent);
            abortAndLogIfInterrupted(
                    preConsensusEventWriter::waitUntilDurable,
                    keystoneEvent,
                    "Interrupted while waiting for keystone event to become durable");

            consRounds.forEach(this::handleConsensus);
            stats.dispatchedRound();
        }
        if (consensus().getMinGenerationNonAncient() > minGenNonAncientBeforeAdding) {
            // consensus rounds can be null and the minNonAncient might change, this is probably because of a round
            // with no consensus events, so we check the diff in generations to look for stale events
            handleStale(minGenNonAncientBeforeAdding);
            stats.dispatchedStale();
        }
        stats.doneIntakeAddEvent();
    }

    /**
     * Notify observer of stale events, of all event in the consensus stale event queue.
     */
    private void handleStale(final long previousNonAncient) {
        // find all events that just became ancient and did not reach consensus, these events will be considered stale
        final Collection<EventImpl> staleEvents = shadowGraph.findByGeneration(
                previousNonAncient, consensus().getMinGenerationNonAncient(), EventIntake::isNotConsensus);
        for (final EventImpl e : staleEvents) {
            e.setStale(true);
            dispatcher.staleEvent(e);
            logger.warn(STALE_EVENTS.getMarker(), "Stale event {}", e::toShortString);
        }
    }

    private static boolean isNotConsensus(final EventImpl event) {
        return !event.isConsensus();
    }

    /**
     * Notify observers that an event has reach consensus. Called on a list of events returned from
     * {@code Consensus.addEvent}.
     *
     * @param consensusRound the (new) consensus round to be observed
     */
    private void handleConsensus(final ConsensusRound consensusRound) {
        if (consensusRound != null) {
            eventLinker.updateGenerations(consensusRound.getGenerations());
            dispatcher.consensusRound(consensusRound);
        }
    }

    /**
     * Get a reference to the consensus instance to use
     *
     * @return a reference to the consensus instance
     */
    private Consensus consensus() {
        return consensusSupplier.get();
    }
}
