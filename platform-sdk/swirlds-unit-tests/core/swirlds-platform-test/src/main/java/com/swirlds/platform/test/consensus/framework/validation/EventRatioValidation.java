/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.swirlds.platform.test.consensus.framework.validation;

import static com.swirlds.platform.test.event.EventUtils.countConsensusAndStaleEvents;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class EventRatioValidation implements ConsensusOutputValidation {
    /**
     * The minimum fraction of events (out of 1.0) that are expected to have reached consensus at
     * the end of the sequence.
     */
    private double minimumConsensusRatio;
    /**
     * The Maximum fraction of events (out of 1.0) that are expected to have reached consensus at
     * the end of the sequence.
     *
     * <p>The actual ratio of events reaching consensus may be greater than 1.0. This can happen if
     * events from a previous sequence reach consensus during this sequence.
     */
    private double maximumConsensusRatio;
    /** Get the minimum ratio of expected stale events. */
    private double minimumStaleRatio;
    /** Get the maximum ratio of expected stale events. */
    private double maximumStaleRatio;

    private EventRatioValidation(
            final double minimumConsensusRatio,
            final double maximumConsensusRatio,
            final double minimumStaleRatio,
            final double maximumStaleRatio) {
        this.minimumConsensusRatio = minimumConsensusRatio;
        this.maximumConsensusRatio = maximumConsensusRatio;
        this.minimumStaleRatio = minimumStaleRatio;
        this.maximumStaleRatio = maximumStaleRatio;
    }

    public static EventRatioValidation blank() {
        return new EventRatioValidation(0d, Double.MAX_VALUE, 0d, Double.MAX_VALUE);
    }

    public static EventRatioValidation standard() {
        return new EventRatioValidation(0.8, 1.0, 0.0, 0.01);
    }

    /**
     * Set the minimum fraction of events (out of 1.0) that are expected to have reached consensus
     * at the end of the sequence. Default 0.8.
     */
    public EventRatioValidation setMinimumConsensusRatio(final double expectedConsensusRatio) {
        this.minimumConsensusRatio = expectedConsensusRatio;
        return this;
    }

    /**
     * Set the maximum fraction of events (out of 1.0) that are expected to have reached consensus
     * at the end of the sequence. Default 1.0.
     */
    public EventRatioValidation setMaximumConsensusRatio(final double maximumConsensusRatio) {
        this.maximumConsensusRatio = maximumConsensusRatio;
        return this;
    }

    /**
     * Set the minimum ratio of expected stale events. Default 0.0.
     *
     * @return this
     */
    public EventRatioValidation setMinimumStaleRatio(final double minimumStaleRatio) {
        this.minimumStaleRatio = minimumStaleRatio;
        return this;
    }

    /** Set the maximum ratio of expected stale events. Default 0.01. */
    public EventRatioValidation setMaximumStaleRatio(final double maximumStaleRatio) {
        this.maximumStaleRatio = maximumStaleRatio;
        return this;
    }

    public void validate(final ConsensusOutput output1, final ConsensusOutput ignored) {
        // For each statistic we only need to check one list since other validators can verify them
        // to be identical.
        final List<EventImpl> allEvents1 = output1.getAddedEvents();
        final Pair<Integer, Integer> ratios = countConsensusAndStaleEvents(allEvents1);

        // Validate consensus ratio
        final double consensusRatio = ((double) ratios.getLeft()) / allEvents1.size();

        assertTrue(
                consensusRatio >= minimumConsensusRatio,
                String.format(
                        "Consensus ratio %s is less than the expected minimum %s",
                        consensusRatio, minimumConsensusRatio));
        assertTrue(
                consensusRatio <= maximumConsensusRatio,
                String.format(
                        "Consensus ratio %s is more than the expected maximum %s",
                        consensusRatio, maximumConsensusRatio));

        // Validate stale ratio
        final double staleRatio = ((double) ratios.getRight()) / allEvents1.size();

        assertTrue(
                staleRatio >= minimumStaleRatio,
                String.format(
                        "Stale ratio %s is less than the expected minimum %s",
                        staleRatio, minimumStaleRatio));
        assertTrue(
                staleRatio <= maximumStaleRatio,
                String.format(
                        "Stale ratio %s is more than the expected maximum %s",
                        staleRatio, maximumStaleRatio));
    }
}
