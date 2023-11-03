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

import com.swirlds.base.time.Time;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
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

/**
 * This class is responsible for adding events to {@link Consensus} and notifying event observers, including
 * {@link ConsensusRoundHandler} and {@link com.swirlds.platform.eventhandling.PreConsensusEventHandler}.
 * <p>
 * This class differs from {@link EventIntake} in that it accepts events that have already been linked with their
 * parents. This version of event intake was written to be compatible with the new intake pipeline, whereas
 * {@link EventIntake} works with the legacy intake monolith.
 */
public class LinkedEventIntake {
    /**
     * A functor that provides access to a {@code Consensus} instance.
     */
    private final Supplier<Consensus> consensusSupplier;

    /**
     * An {@link EventObserverDispatcher} instance
     */
    private final EventObserverDispatcher dispatcher;

    /**
     * Stores events, expires them, provides event lookup methods
     */
    private final ShadowGraph shadowGraph;

    private final ExecutorService prehandlePool;
    private final Consumer<EventImpl> prehandleEvent;

    private final EventIntakeMetrics metrics;
    private final Time time;

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
     * @param consensusSupplier  provides the current consensus instance
     * @param dispatcher         invokes event related callbacks
     * @param shadowGraph        tracks events in the hashgraph
     * @param prehandleEvent     prehandles transactions in an event
     * @param intakeEventCounter tracks the number of events from each peer that are currently in the intake pipeline
     */
    public LinkedEventIntake(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final Supplier<Consensus> consensusSupplier,
            @NonNull final EventObserverDispatcher dispatcher,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final Consumer<EventImpl> prehandleEvent,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        this.time = Objects.requireNonNull(time);
        this.consensusSupplier = Objects.requireNonNull(consensusSupplier);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
        this.prehandleEvent = Objects.requireNonNull(prehandleEvent);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        final EventConfig eventConfig = platformContext.getConfiguration().getConfigData(EventConfig.class);

        final BlockingQueue<Runnable> prehandlePoolQueue = new LinkedBlockingQueue<>();

        prehandlePool = new ThreadPoolExecutor(
                eventConfig.prehandlePoolSize(),
                eventConfig.prehandlePoolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                prehandlePoolQueue,
                threadManager.createThreadFactory("platform", "txn-prehandle"));

        metrics = new EventIntakeMetrics(platformContext, prehandlePoolQueue::size);
    }

    /**
     * Add an event to the hashgraph
     *
     * @param event an event to be added
     */
    public void addEvent(@NonNull final EventImpl event) {
        Objects.requireNonNull(event);

        try {
            if (event.getGeneration() < consensusSupplier.get().getMinGenerationNonAncient()) {
                // ancient events *may* be discarded, and stale events *must* be discarded
                return;
            }

            dispatcher.preConsensusEvent(event);

            final long minGenNonAncientBeforeAdding = consensusSupplier.get().getMinGenerationNonAncient();

            // Prehandle transactions on the thread pool.
            prehandlePool.submit(buildPrehandleTask(event));

            // record the event in the hashgraph, which results in the events in consEvent reaching consensus
            final List<ConsensusRound> consensusRounds = consensusSupplier.get().addEvent(event);

            dispatcher.eventAdded(event);

            if (consensusRounds != null) {
                consensusRounds.forEach(this::handleConsensus);
            }

            if (consensusSupplier.get().getMinGenerationNonAncient() > minGenNonAncientBeforeAdding) {
                // consensus rounds can be null and the minNonAncient might change, this is probably because of a round
                // with no consensus events, so we check the diff in generations to look for stale events
                handleStale(minGenNonAncientBeforeAdding);
            }
        } finally {
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
        }
    }

    /**
     * Build a task that will prehandle transactions in an event. Executed on a thread pool.
     *
     * @param event the event to prehandle
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
     *
     * @param previousGenerationNonAncient the previous minimum generation of non-ancient events
     */
    private void handleStale(final long previousGenerationNonAncient) {
        // find all events that just became ancient and did not reach consensus, these events will be considered stale
        final Collection<EventImpl> staleEvents = shadowGraph.findByGeneration(
                previousGenerationNonAncient,
                consensusSupplier.get().getMinGenerationNonAncient(),
                LinkedEventIntake::isNotConsensus);

        for (final EventImpl staleEvent : staleEvents) {
            staleEvent.setStale(true);
            dispatcher.staleEvent(staleEvent);
        }
    }

    /**
     * Returns true if the event has not reached consensus
     *
     * @param event the event to check
     * @return true if the event has not reached consensus
     */
    private static boolean isNotConsensus(@NonNull final EventImpl event) {
        return !event.isConsensus();
    }

    /**
     * Notify observers that an event has reach consensus.
     *
     * @param consensusRound the new consensus round
     */
    private void handleConsensus(final @NonNull ConsensusRound consensusRound) {
        // We need to wait for prehandles to finish before proceeding.
        // It is critically important that prehandle is always called prior to handleConsensusRound().

        final long start = time.nanoTime();
        consensusRound.forEach(event -> ((EventImpl) event).awaitPrehandleCompletion());
        final long end = time.nanoTime();
        metrics.reportTimeWaitedForPrehandlingTransaction(end - start);

        dispatcher.consensusRound(consensusRound);
    }
}
