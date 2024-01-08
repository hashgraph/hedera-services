/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventObserverDispatcher;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * This class is responsible for adding events to {@link Consensus} and notifying event observers.
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

    private final LatestEventTipsetTracker latestEventTipsetTracker;

    private final EventIntakeMetrics metrics;
    private final Time time;
    /**
     * FUTURE WORK: If nothing else is using it, delete platformContext when we switch to permanently using birthRound
     * for determining Ancient.
     */
    private final PlatformContext platformContext;

    /**
     * Tracks the number of events from each peer have been received, but aren't yet through the intake pipeline
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * Whether or not the linked event intake is paused.
     * <p>
     * When paused, all received events will be tossed into the void
     */
    private boolean paused;

    /**
     * Constructor
     *
     * @param platformContext          the platform context
     * @param time                     provides the wall clock time
     * @param consensusSupplier        provides the current consensus instance
     * @param dispatcher               invokes event related callbacks
     * @param shadowGraph              tracks events in the hashgraph
     * @param latestEventTipsetTracker tracks the tipset of the latest self event, null if feature is not enabled
     * @param intakeEventCounter       tracks the number of events from each peer that are currently in the intake
     *                                 pipeline
     */
    public LinkedEventIntake(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final Supplier<Consensus> consensusSupplier,
            @NonNull final EventObserverDispatcher dispatcher,
            @NonNull final ShadowGraph shadowGraph,
            @Nullable final LatestEventTipsetTracker latestEventTipsetTracker,
            @NonNull final IntakeEventCounter intakeEventCounter) {
        this.platformContext = Objects.requireNonNull(platformContext);
        this.time = Objects.requireNonNull(time);
        this.consensusSupplier = Objects.requireNonNull(consensusSupplier);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.latestEventTipsetTracker = latestEventTipsetTracker;

        this.paused = false;
        metrics = new EventIntakeMetrics(platformContext, () -> -1);
    }

    /**
     * Add an event to the hashgraph
     *
     * @param event an event to be added
     * @return a list of rounds that came to consensus as a result of adding the event
     */
    @NonNull
    public List<ConsensusRound> addEvent(@NonNull final EventImpl event) {
        Objects.requireNonNull(event);

        if (paused) {
            // If paused, throw everything into the void
            event.clear();
            return List.of();
        }

        try {
            if (event.getGeneration() < consensusSupplier.get().getMinGenerationNonAncient()) {
                // ancient events *may* be discarded, and stale events *must* be discarded
                event.clear();
                return List.of();
            }

            dispatcher.preConsensusEvent(event);

            final long minimumGenerationNonAncientBeforeAdding =
                    consensusSupplier.get().getMinGenerationNonAncient();

            // record the event in the hashgraph, which results in the events in consEvent reaching consensus
            final List<ConsensusRound> consensusRounds = consensusSupplier.get().addEvent(event);

            dispatcher.eventAdded(event);

            if (consensusRounds != null) {
                consensusRounds.forEach(this::handleConsensus);
            }

            final long minimumGenerationNonAncient = consensusSupplier.get().getMinGenerationNonAncient();

            if (minimumGenerationNonAncient > minimumGenerationNonAncientBeforeAdding) {
                // consensus rounds can be null and the minNonAncient might change, this is probably because of a round
                // with no consensus events, so we check the diff in generations to look for stale events
                handleStale(minimumGenerationNonAncientBeforeAdding);
                if (latestEventTipsetTracker != null) {
                    // FUTURE WORK: When this class is refactored, it should not be constructing the
                    // NonAncientEventWindow, but receiving it through the PlatformWiring instead.
                    latestEventTipsetTracker.setNonAncientEventWindow(NonAncientEventWindow.createUsingPlatformContext(
                            consensusSupplier.get().getLastRoundDecided(),
                            minimumGenerationNonAncient,
                            platformContext));
                }
            }

            return Objects.requireNonNullElseGet(consensusRounds, List::of);
        } finally {
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
        }
    }

    /**
     * Pause or unpause this object.
     *
     * @param paused whether or not this object should be paused
     */
    public void setPaused(final boolean paused) {
        this.paused = paused;
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
        consensusRound.forEach(event -> ((EventImpl) event).getBaseEvent().awaitPrehandleCompletion());
        final long end = time.nanoTime();
        metrics.reportTimeWaitedForPrehandlingTransaction(end - start);

        dispatcher.consensusRound(consensusRound);
    }
}
