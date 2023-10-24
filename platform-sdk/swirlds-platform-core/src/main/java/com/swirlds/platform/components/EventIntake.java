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

import static com.swirlds.logging.LogMarker.INTAKE_EVENT;
import static com.swirlds.logging.LogMarker.STALE_EVENTS;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.intake.EventIntakePhase;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventObserverDispatcher;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
    /** A reference to the initial address book for this node. */
    private final AddressBook addressBook;
    /** An {@link EventObserverDispatcher} instance */
    private final EventObserverDispatcher dispatcher;
    /** Stores events, expires them, provides event lookup methods */
    private final ShadowGraph shadowGraph;

    private final ExecutorService prehandlePool;
    private final Consumer<EventImpl> prehandleEvent;

    private final EventIntakeMetrics metrics;
    private final Time time;

    /**
     * Measures the time spent in each phase of event intake
     */
    private final PhaseTimer<EventIntakePhase> phaseTimer;

    /**
     * Tracks the number of events from each peer have been received, but aren't yet through the intake pipeline
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param threadManager      creates new threading resources
     * @param time               provides the wall clock time
     * @param selfId             the ID of this node
     * @param eventLinker        links events together, holding orphaned events until their parents are found (if
     *                           operating with the orphan buffer enabled)
     * @param consensusSupplier  provides the current consensus instance
     * @param addressBook        the current address book
     * @param dispatcher         invokes event related callbacks
     * @param phaseTimer         measures the time spent in each phase of intake
     * @param shadowGraph        tracks events in the hashgraph
     * @param prehandleEvent     prehandles transactions in an event
     * @param intakeEventCounter tracks the number of events from each peer that are currently in the intake pipeline
     */
    public EventIntake(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final NodeId selfId,
            @NonNull final EventLinker eventLinker,
            @NonNull final Supplier<Consensus> consensusSupplier,
            @NonNull final AddressBook addressBook,
            @NonNull final EventObserverDispatcher dispatcher,
            @NonNull final PhaseTimer<EventIntakePhase> phaseTimer,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final Consumer<EventImpl> prehandleEvent,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        this.time = Objects.requireNonNull(time);
        this.selfId = Objects.requireNonNull(selfId);
        this.eventLinker = Objects.requireNonNull(eventLinker);
        this.consensusSupplier = Objects.requireNonNull(consensusSupplier);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.phaseTimer = Objects.requireNonNull(phaseTimer);
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
        this.prehandleEvent = Objects.requireNonNull(prehandleEvent);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);
        final Supplier<Integer> prehandlePoolSize;
        if (eventConfig.asyncPrehandle()) {
            final BlockingQueue<Runnable> prehandlePoolQueue = new LinkedBlockingQueue<>();
            prehandlePoolSize = prehandlePoolQueue::size;
            prehandlePool = new ThreadPoolExecutor(
                    eventConfig.prehandlePoolSize(),
                    eventConfig.prehandlePoolSize(),
                    0L,
                    TimeUnit.MILLISECONDS,
                    prehandlePoolQueue,
                    threadManager.createThreadFactory("platform", "txn-prehandle"));
        } else {
            prehandlePool = null;
            prehandlePoolSize = () -> 0;
        }

        metrics = new EventIntakeMetrics(platformContext, prehandlePoolSize);
    }

    /**
     * Adds an event received from gossip that has been validated without its parents. It must be linked to its parents
     * before being added to consensus. The linking is done by the {@link EventLinker} provided.
     *
     * @param event the event
     */
    public void addUnlinkedEvent(final GossipEvent event) {
        phaseTimer.activatePhase(EventIntakePhase.EVENT_RECEIVED_DISPATCH);
        dispatcher.receivedEvent(event);

        phaseTimer.activatePhase(EventIntakePhase.LINKING);
        eventLinker.linkEvent(event);

        while (eventLinker.hasLinkedEvents()) {
            addEvent(eventLinker.pollLinkedEvent());
        }

        phaseTimer.activatePhase(EventIntakePhase.IDLE);
    }

    /**
     * Add an event to the hashgraph
     *
     * @param event an event to be added
     */
    public void addEvent(final EventImpl event) {
        try {
            // an expired event will cause ShadowGraph to throw an exception, so we just to discard it
            if (consensus().isExpired(event)) {
                return;
            }

            if (!StaticValidators.isValidTimeCreated(event)) {
                event.clear();
                return;
            }

            phaseTimer.activatePhase(EventIntakePhase.PRECONSENSUS_DISPATCH);
            dispatcher.preConsensusEvent(event);

            logger.debug(INTAKE_EVENT.getMarker(), "Adding {} ", event::toShortString);
            final long minGenNonAncientBeforeAdding = consensus().getMinGenerationNonAncient();

            if (prehandlePool == null) {
                // Prehandle transactions on the intake thread (i.e. this thread).
                phaseTimer.activatePhase(EventIntakePhase.PREHANDLING);
                prehandleEvent.accept(event);
            } else {
                // Prehandle transactions on the thread pool.
                prehandlePool.submit(buildPrehandleTask(event));
            }

            // record the event in the hashgraph, which results in the events in consEvent reaching consensus
            phaseTimer.activatePhase(EventIntakePhase.ADDING_TO_HASHGRAPH);
            final List<ConsensusRound> consRounds = consensus().addEvent(event);

            phaseTimer.activatePhase(EventIntakePhase.EVENT_ADDED_DISPATCH);
            dispatcher.eventAdded(event);

            if (consRounds != null) {
                phaseTimer.activatePhase(EventIntakePhase.HANDLING_CONSENSUS_ROUNDS);
                consRounds.forEach(this::handleConsensus);
            }

            if (consensus().getMinGenerationNonAncient() > minGenNonAncientBeforeAdding) {
                // consensus rounds can be null and the minNonAncient might change, this is probably because of a round
                // with no consensus events, so we check the diff in generations to look for stale events
                phaseTimer.activatePhase(EventIntakePhase.HANDLING_STALE_EVENTS);
                handleStale(minGenNonAncientBeforeAdding);
            }
        } finally {
            phaseTimer.activatePhase(EventIntakePhase.IDLE);
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
        }
    }

    /**
     * Build a task that will prehandle transactions in an event. Executed on a thread pool.
     */
    @NonNull
    private Runnable buildPrehandleTask(@NonNull final EventImpl event) {
        return () -> {
            prehandleEvent.accept(event);
            event.signalPrehandleCompletion();
        };
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

            // If we are asynchronously prehandling transactions, we need to
            // wait for prehandles to finish before proceeding. It is critically
            // important that prehandle is always called prior to handleConsensusRound().
            if (prehandlePool != null) {
                final long start = time.nanoTime();
                consensusRound.forEach(e -> ((EventImpl) e).awaitPrehandleCompletion());
                final long end = time.nanoTime();
                metrics.reportTimeWaitedForPrehandlingTransaction(end - start);
            }

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
