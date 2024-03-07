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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.AddedEventMetrics;
import com.swirlds.platform.metrics.StaleMetrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is responsible for adding events to {@link Consensus}.
 */
public class ConsensusEngine {
    /**
     * A functor that provides access to a {@code Consensus} instance.
     */
    private final Supplier<Consensus> consensusSupplier;

    /**
     * Stores events, expires them, provides event lookup methods
     */
    private final Shadowgraph shadowGraph;

    /**
     * Tracks the number of events from each peer have been received, but aren't yet through the intake pipeline
     */
    private final IntakeEventCounter intakeEventCounter;

    private final AddedEventMetrics eventAddedMetrics;

    private final StaleMetrics staleMetrics;
    /** Consumes stale events */
    private final Consumer<EventImpl> staleEventConsumer;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param selfId             the ID of the node
     * @param consensusSupplier  provides the current consensus instance
     * @param shadowGraph        tracks events in the hashgraph
     * @param intakeEventCounter tracks the number of events from each peer that have been received, but
     *                           aren't yet through the intake pipeline
     * @param staleEventConsumer a consumer of stale events
     */
    public ConsensusEngine(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Supplier<Consensus> consensusSupplier,
            @NonNull final Shadowgraph shadowGraph,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final Consumer<EventImpl> staleEventConsumer) {

        this.consensusSupplier = Objects.requireNonNull(consensusSupplier);
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.staleEventConsumer = Objects.requireNonNull(staleEventConsumer);

        this.eventAddedMetrics = new AddedEventMetrics(selfId, platformContext.getMetrics());
        this.staleMetrics = new StaleMetrics(platformContext, selfId);
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

        try {
            if (event.getGeneration() < consensusSupplier.get().getMinGenerationNonAncient()) {
                // ancient events *may* be discarded, and stale events *must* be discarded
                return List.of();
            }

            final long minimumGenerationNonAncientBeforeAdding =
                    consensusSupplier.get().getMinGenerationNonAncient();

            // record the event in the hashgraph, which results in the events in consEvent reaching consensus
            final List<ConsensusRound> consensusRounds = consensusSupplier.get().addEvent(event);

            eventAddedMetrics.eventAdded(event);

            final long minimumGenerationNonAncient = consensusSupplier.get().getMinGenerationNonAncient();

            if (minimumGenerationNonAncient > minimumGenerationNonAncientBeforeAdding) {
                // consensus rounds can be null and the minNonAncient might change, this is probably because of a round
                // with no consensus events, so we check the diff in generations to look for stale events
                handleStale(minimumGenerationNonAncientBeforeAdding);
            }

            return Objects.requireNonNullElseGet(consensusRounds, List::of);
        } finally {
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
        }
    }

    /**
     * Notify observer of stale events, of all event in the consensus stale event queue.
     *
     * @param previousGenerationNonAncient the previous minimum generation of non-ancient events
     */
    private void handleStale(final long previousGenerationNonAncient) {
        // find all events that just became ancient and did not reach consensus, these events will be considered stale
        final Collection<EventImpl> staleEvents = shadowGraph.findByAncientIndicator(
                previousGenerationNonAncient,
                consensusSupplier.get().getMinGenerationNonAncient(),
                ConsensusEngine::isNotConsensus);

        for (final EventImpl staleEvent : staleEvents) {
            staleEvent.setStale(true);
            staleMetrics.staleEvent(staleEvent);
            staleEventConsumer.accept(staleEvent);
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
}
