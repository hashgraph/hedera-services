/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.event.generator;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.EventConstants;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A base graph generator class that provides most functionality of a graph generator except for determining how to
 * generate the next event.
 */
public abstract class AbstractGraphGenerator<T extends AbstractGraphGenerator<T>> implements GraphGenerator<T> {

    /**
     * The total number of events that have been emitted by this generator.
     */
    private long numEventsGenerated;

    /**
     * The initial seed of this generator.
     */
    private final long initialSeed;

    /**
     * The source of all randomness for this class.
     */
    private Random random;

    /** A map that holds the maximum event generation for each creator */
    private final Map<NodeId, Long> maxGenerationPerCreator;

    /** The highest birth round of created events for each creator */
    private final Map<NodeId, Long> maxBirthRoundPerCreator;

    protected AbstractGraphGenerator(final long initialSeed) {
        this.initialSeed = initialSeed;
        random = new Random(initialSeed);
        maxGenerationPerCreator = new HashMap<>();
        maxBirthRoundPerCreator = new HashMap<>();
    }

    /**
     * Child classes should reset internal metadata in this method.
     */
    protected abstract void resetInternalData();

    /**
     * {@inheritDoc}
     * <p>
     * Child classes must call super.reset() if they override this method.
     */
    @Override
    public final void reset() {
        numEventsGenerated = 0;
        random = new Random(initialSeed);
        maxGenerationPerCreator.clear();
        maxBirthRoundPerCreator.clear();
        resetInternalData();
    }

    /**
     * Build the event that will be returned by getNextEvent.
     *
     * @param eventIndex the index of the event to build
     */
    protected abstract EventImpl buildNextEvent(long eventIndex);

    /**
     * {@inheritDoc}
     */
    public final EventImpl generateEvent() {
        final EventImpl next = generateEventWithoutIndex();

        next.getBaseEvent().setStreamSequenceNumber(numEventsGenerated);
        return next;
    }

    /**
     * The same as {@link #generateEvent()}, but does not set the stream sequence number.
     */
    public final EventImpl generateEventWithoutIndex() {
        final EventImpl next = buildNextEvent(numEventsGenerated);
        next.getBaseEvent().signalPrehandleCompletion();
        numEventsGenerated++;
        updateMaxGeneration(next);
        updateMaxBirthRound(next);
        return next;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getNumEventsGenerated() {
        return numEventsGenerated;
    }

    /**
     * Get the Random object to be used by this class.
     */
    protected final Random getRandom() {
        return random;
    }

    /**
     * The seed used at the start of this generator.
     */
    public final long getInitialSeed() {
        return initialSeed;
    }

    /**
     * Updates the max generation based on the latest event
     */
    private void updateMaxGeneration(final EventImpl event) {
        maxGenerationPerCreator.merge(event.getCreatorId(), event.getGeneration(), Math::max);
    }

    private void updateMaxBirthRound(@NonNull final EventImpl event) {
        maxBirthRoundPerCreator.merge(event.getCreatorId(), event.getBirthRound(), Math::max);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxGeneration(@Nullable final NodeId creatorId) {
        return maxGenerationPerCreator.getOrDefault(creatorId, EventConstants.GENERATION_UNDEFINED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxBirthRound(@Nullable final NodeId creatorId) {
        return maxBirthRoundPerCreator.getOrDefault(creatorId, ConsensusConstants.ROUND_NEGATIVE_INFINITY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxGeneration() {
        return maxGenerationPerCreator.values().stream().max(Long::compareTo).orElse(GraphGenerations.FIRST_GENERATION);
    }
}
