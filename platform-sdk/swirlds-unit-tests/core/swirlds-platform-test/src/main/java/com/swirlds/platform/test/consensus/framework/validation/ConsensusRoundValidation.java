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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.platform.test.fixtures.event.IndexedEvent;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ConsensusRoundValidation {
    public static void validateConsensusRounds(final ConsensusOutput output1, final ConsensusOutput output2) {
        validateIterableRounds(
                output1.getConsensusRounds().iterator(),
                output2.getConsensusRounds().iterator());

        assertEquals(
                output1.getConsensusRounds().size(),
                output2.getConsensusRounds().size(),
                String.format(
                        "The number of consensus rounds is not the same."
                                + "output1 has %d rounds, output2 has %d rounds",
                        output1.getConsensusRounds().size(),
                        output2.getConsensusRounds().size()));
    }

    public static void validateIterableRounds(
            final Iterator<ConsensusRound> rndIt1, final Iterator<ConsensusRound> rndIt2) {
        int roundIndex = 0;
        while (rndIt1.hasNext() && rndIt2.hasNext()) {
            final ConsensusRound round1 = rndIt1.next();
            final ConsensusRound round2 = rndIt2.next();

            assertEquals(
                    round1.getRoundNum(),
                    round2.getRoundNum(),
                    String.format("round diff at round index %d", roundIndex));

            assertEquals(
                    round1.getEventCount(),
                    round2.getEventCount(),
                    String.format("event number diff at round index %d", roundIndex));

            assertEquals(
                    round1.getSnapshot(),
                    round2.getSnapshot(),
                    String.format("snapshot diff at round index %d", roundIndex));

            final Iterator<EventImpl> evIt1 = round1.getConsensusEvents().iterator();
            final Iterator<EventImpl> evIt2 = round2.getConsensusEvents().iterator();

            int eventIndex = 0;
            while (evIt1.hasNext() && evIt2.hasNext()) {
                final EventImpl e1 = evIt1.next();
                final EventImpl e2 = evIt2.next();

                assertTrue(
                        e1.isConsensus(),
                        String.format(
                                "output:1, roundIndex:%d, eventIndex%d is not consensus", roundIndex, eventIndex));
                assertTrue(
                        e2.isConsensus(),
                        String.format(
                                "output:1, roundIndex:%d, eventIndex%d is not consensus", roundIndex, eventIndex));

                assertConsensusEvents(String.format("Round index:%d, event index %d", roundIndex, eventIndex), e1, e2);
                eventIndex++;
            }

            roundIndex++;
        }
    }

    /**
     * Assert that two events are equal. If they are not equal then cause the test to fail and print
     * a meaningful error message.
     *
     * @param description a string that is printed if the events are unequal
     * @param e1 the first event
     * @param e2 the second event
     */
    private static void assertConsensusEvents(final String description, final EventImpl e1, final EventImpl e2) {
        final boolean equal = Objects.equals(e1.getHashedData(), e2.getHashedData())
                && Objects.equals(e1.getConsensusData(), e2.getConsensusData())
                && e1.isWitness() == e2.isWitness();
        if (!equal) {
            final StringBuilder sb = new StringBuilder();
            sb.append(description).append("\n");
            sb.append("Events are not equal:\n");
            sb.append("Event 1: ").append(e1).append("\n");
            sb.append("Event 2: ").append(e2).append("\n");
            getEventDifference(sb, e1, e2);
            throw new RuntimeException(sb.toString());
        }
    }

    /**
     * This is debugging utility. Given two lists, sort and compare each event.
     *
     * <p>If one list of events is longer than the other, comparison ends when when the shorter list
     * ends.
     *
     * <p>For each event, compares the following: - generation - isWitness - roundCreated
     *
     * <p>For each event that has consensus, compares the following: - consensusTimestamp -
     * roundReceived - consensusOrder
     *
     * @param events1 the first list of events. This list should contain ALL events in their
     *     original order, even if they were not emitted (this can happen if a generator is wrapped
     *     in a shuffled generator).
     * @param events2 the second list of events. This list should contain ALL events in their
     *     original order, even if they were not emitted (this can happen if a generator is wrapped
     *     in a shuffled generator).
     */
    public static void printGranularEventListComparison(
            final List<IndexedEvent> events1, final List<IndexedEvent> events2) {
        // TODO add this as a debug tool

        final int maxIndex = Math.min(events1.size(), events2.size());

        for (int index = 0; index < maxIndex; index++) {

            final IndexedEvent event1 = events1.get(index);
            final IndexedEvent event2 = events2.get(index);

            if (!Objects.equals(event1, event2)) {
                final StringBuilder sb = new StringBuilder()
                        .append("----------\n")
                        .append("Events with index ")
                        .append(event1.getGeneratorIndex())
                        .append(" do not match\n");
                getEventDifference(sb, event1, event2);
                System.out.println(sb);
            }
        }
    }

    /** Add a description to a string builder as to why two events are different. */
    private static void getEventDifference(final StringBuilder sb, final EventImpl event1, final EventImpl event2) {

        checkGeneration(event1, event2, sb);
        checkWitnessStatus(event1, event2, sb);
        checkRoundCreated(event1, event2, sb);
        checkIsStale(event1, event2, sb);
        checkConsensusTimestamp(event1, event2, sb);
        checkRoundReceived(event1, event2, sb);
        checkConsensusOrder(event1, event2, sb);
        checkFame(event1, event2, sb);
    }

    private static void checkFame(final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (event1.isFameDecided() != event2.isFameDecided()) {
            sb.append("   fame decided mismatch: ")
                    .append(event1.isFameDecided())
                    .append(" vs ")
                    .append(event2.isFameDecided())
                    .append("\n");
        } else {
            if (event1.isFamous() != event2.isFamous()) {
                sb.append("   is famous mismatch: ")
                        .append(event1.isFamous())
                        .append(" vs ")
                        .append(event2.isFamous())
                        .append("\n");
            }
        }
    }

    private static void checkConsensusOrder(final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (event1.getConsensusOrder() != event2.getConsensusOrder()) {
            sb.append("   consensus order mismatch: ")
                    .append(event1.getConsensusOrder())
                    .append(" vs ")
                    .append(event2.getConsensusOrder())
                    .append("\n");
        }
    }

    private static void checkRoundReceived(final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (event1.getRoundReceived() != event2.getRoundReceived()) {
            sb.append("   round received mismatch: ")
                    .append(event1.getRoundReceived())
                    .append(" vs ")
                    .append(event2.getRoundReceived())
                    .append("\n");
        }
    }

    private static void checkConsensusTimestamp(
            final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (!Objects.equals(event1.getConsensusTimestamp(), event2.getConsensusTimestamp())) {
            sb.append("   consensus timestamp mismatch: ")
                    .append(event1.getConsensusTimestamp())
                    .append(" vs ")
                    .append(event2.getConsensusTimestamp())
                    .append("\n");
        }
    }

    private static void checkIsStale(final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (event1.isStale() != event2.isStale()) {
            sb.append("   stale mismatch: ")
                    .append(event1.isStale())
                    .append(" vs ")
                    .append(event2.isStale())
                    .append("\n");
        }
    }

    private static void checkRoundCreated(final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (event1.getRoundCreated() != event2.getRoundCreated()) {
            sb.append("   round created mismatch: ")
                    .append(event1.getRoundCreated())
                    .append(" vs ")
                    .append(event2.getRoundCreated())
                    .append("\n");
        }
    }

    private static void checkWitnessStatus(final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (event1.isWitness() != event2.isWitness()) {
            sb.append("   witness mismatch: ")
                    .append(event1.isWitness())
                    .append(" vs ")
                    .append(event2.isWitness())
                    .append("\n");
        }
    }

    private static void checkGeneration(final EventImpl event1, final EventImpl event2, final StringBuilder sb) {
        if (event1.getGeneration() != event2.getGeneration()) {
            sb.append("   generation mismatch: ")
                    .append(event1.getGeneration())
                    .append(" vs ")
                    .append(event2.getGeneration())
                    .append("\n");
        }
    }
}
