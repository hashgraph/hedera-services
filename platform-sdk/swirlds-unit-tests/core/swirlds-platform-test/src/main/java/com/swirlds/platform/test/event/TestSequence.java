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

package com.swirlds.platform.test.event;

import static com.swirlds.platform.test.event.EventUtils.assertBaseEventLists;
import static com.swirlds.platform.test.event.EventUtils.assertEventListsAreEqual;
import static com.swirlds.platform.test.event.EventUtils.countConsensusAndStaleEvents;
import static com.swirlds.platform.test.event.EventUtils.printGranularEventListComparison;
import static com.swirlds.platform.test.event.EventUtils.sortEventList;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Describes the expected behavior of a consensus test sequence. A consensus test may be composed of one or more
 * sequences. After each sequence a large number of sanity checks are preformed. Each sequence leads into the next,
 * so if the first sequence generates 1000 events, the next sequence will start with event index 1001.
 */
public class TestSequence {

    private double minimumConsensusRatio;
    private double maximumConsensusRatio;
    private double minimumStaleRatio;
    private double maximumStaleRatio;

    private int length;
    private boolean debug;

    /** An optional custom validator to execute on this {@link TestSequence} after standard validation is executed. */
    private BiConsumer<List<IndexedEvent>, List<IndexedEvent>> customValidator;

    public TestSequence(final int length) {
        this.length = length;
        minimumConsensusRatio = 0.9;
        maximumConsensusRatio = 1.0;
        minimumStaleRatio = 0.0;
        maximumStaleRatio = 0.01;
        customValidator = (l1, l2) -> {};
    }

    /**
     * The minimum fraction of events (out of 1.0) that are expected to have reached consensus
     * at the end of the sequence.
     */
    public double getMinimumConsensusRatio() {
        return minimumConsensusRatio;
    }

    /**
     * Set the minimum fraction of events (out of 1.0) that are expected to have reached consensus
     * at the end of the sequence. Default 0.9.
     */
    public TestSequence setMinimumConsensusRatio(final double expectedConsensusRatio) {
        this.minimumConsensusRatio = expectedConsensusRatio;
        return this;
    }

    /**
     * The Maximum fraction of events (out of 1.0) that are expected to have reached consensus
     * at the end of the sequence.
     *
     * The actual ratio of events reaching consensus may be greater than 1.0. This can happen if events from a previous
     * sequence reach consensus during this sequence.
     */
    public double getMaximumConsensusRatio() {
        return maximumConsensusRatio;
    }

    /**
     * Set the maximum fraction of events (out of 1.0) that are expected to have reached consensus
     * at the end of the sequence. Default 1.0.
     */
    public TestSequence setMaximumConsensusRatio(final double maximumConsensusRatio) {
        this.maximumConsensusRatio = maximumConsensusRatio;
        return this;
    }

    /**
     * Get the minimum ratio of expected stale events.
     */
    public double getMinimumStaleRatio() {
        return minimumStaleRatio;
    }

    /**
     * Set the minimum ratio of expected stale events. Default 0.0.
     *
     * @return this
     */
    public TestSequence setMinimumStaleRatio(final double minimumStaleRatio) {
        this.minimumStaleRatio = minimumStaleRatio;
        return this;
    }

    /**
     * Get the maximum ratio of expected stale events.
     */
    public double getMaximumStaleRatio() {
        return maximumStaleRatio;
    }

    /**
     * Set the maximum ratio of expected stale events. Default 0.01.
     */
    public TestSequence setMaximumStaleRatio(final double maximumStaleRatio) {
        this.maximumStaleRatio = maximumStaleRatio;
        return this;
    }

    /**
     * The length of this sequence (i.e. the number of events to generate before checking event statistics).
     */
    public int getLength() {
        return length;
    }

    /**
     * Set length of this sequence (i.e. the number of events to generate before checking event statistics).
     */
    public TestSequence setLength(final int length) {
        this.length = length;
        return this;
    }

    /**
     * Return true if debugging metrics have been enabled.
     *
     * @return
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Perform extra steps that assist in debugging.
     */
    public TestSequence setDebug(final boolean debug) {
        this.debug = debug;
        return this;
    }

    /**
     * Sets a custom validator for this test sequence.
     *
     * @param customValidator
     * 		the custom validator that accepts a list of events that reached consensus in this sequencey and a list of
     * 		all events created in this sequence.
     */
    public void setCustomValidator(final BiConsumer<List<IndexedEvent>, List<IndexedEvent>> customValidator) {
        this.customValidator = customValidator;
    }

    /**
     * Perform validation on a sequence of events.
     *
     * @param consensusEvents1
     * 		a list of consensus events, should match consensusEvents2
     * @param consensusEvents2
     * 		a list of consensus events, should match consensusEvents1
     * @param allEvents1
     * 		a list of all events, should be equivalent to allEvents2
     * @param allEvents2
     * 		a list of all events, should be equivalent to allEvents1
     */
    public void validateSequence(
            final long currentSequenceNum,
            final List<IndexedEvent> consensusEvents1,
            final List<IndexedEvent> consensusEvents2,
            final List<IndexedEvent> allEvents1,
            final List<IndexedEvent> allEvents2) {

        if (debug) {
            printGranularEventListComparison(allEvents1, allEvents2);

            // This is a no-op unless DebuggableConsensus code is uncommented
            // ((DebuggableConsensus) consensus1).compareMemoizedData((DebuggableConsensus) consensus2);
        }

        // Sort the lists of events
        final List<IndexedEvent> sortedConsensusEvents1 = sortEventList(consensusEvents1);
        final List<IndexedEvent> sortedConsensusEvents2 = sortEventList(consensusEvents2);
        final List<IndexedEvent> sortedAllEvents1 = sortEventList(allEvents1);
        final List<IndexedEvent> sortedAllEvents2 = sortEventList(allEvents2);

        // Verify that ALL base events fed into consensus are exactly identical
        // this will check only pre-consensus data, for non-consensus events, the consensus data does not have to match
        assertBaseEventLists("Verifying input events", sortedAllEvents1, sortedAllEvents2);

        // Verify that the consensus events are the same
        assertEventListsAreEqual("Verifying consensus events", sortedConsensusEvents1, sortedConsensusEvents2);

        // For each statistic we only need to check one list since lists are already verified to be identical.

        final Pair<Integer, Integer> ratios = countConsensusAndStaleEvents(allEvents1);

        // Validate consensus ratio
        final double consensusRatio = ((double) ratios.getLeft()) / allEvents1.size();

        assertTrue(
                consensusRatio >= minimumConsensusRatio,
                String.format(
                        "Consensus ratio %s is less than the expected minimum %s in sequence %s.",
                        consensusRatio, minimumConsensusRatio, currentSequenceNum));
        assertTrue(
                consensusRatio <= maximumConsensusRatio,
                String.format(
                        "Consensus ratio %s is more than the expected maximum %s in sequence %s.",
                        consensusRatio, maximumConsensusRatio, currentSequenceNum));

        // Validate stale ratio
        final double staleRatio = ((double) ratios.getRight()) / allEvents1.size();

        assertTrue(
                staleRatio >= minimumStaleRatio,
                String.format(
                        "Stale ratio %s is less than the expected minimum %s in sequence %s.",
                        staleRatio, minimumStaleRatio, currentSequenceNum));
        assertTrue(
                staleRatio <= maximumStaleRatio,
                String.format(
                        "Stale ratio %s is more than the expected maximum %s in sequence %s.",
                        staleRatio, maximumStaleRatio, currentSequenceNum));

        // Run custom validation
        customValidator.accept(consensusEvents1, allEvents1);
    }
}
