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

package com.swirlds.platform.event.stream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Utility methods for testing event stream validation.
 */
final class EventStreamValidationTestUtils {

    private EventStreamValidationTestUtils() {}

    /**
     * Describes a state that we plan on writing at a future time.
     *
     * @param timestamp                   the consensus timestamp of the state
     * @param runningEventHash            the running event hash of the state
     * @param minimumGenerationNonAncient the minimum generation non-ancient for the state
     * @param eventCount                  the number of events that have reached consensus so far
     */
    record PlannedState(
            @NonNull Instant timestamp,
            @NonNull Hash runningEventHash,
            long minimumGenerationNonAncient,
            long eventCount) {
    }

    /**
     * Describes a PCES file that we plan on writing at a future time.
     *
     * @param minimumGeneration  the file's minimum generation
     * @param maximumGeneration  the file's maximum generation
     * @param timestamp          the file's timestamp
     * @param origin             the file's origin
     * @param lastEventTruncated if true then the last event should be truncated
     * @param events             the events that will go into the file in the order they will appear in the file
     */
    record PlannedPreconsensusEventFile(
            long minimumGeneration,
            long maximumGeneration,
            long timestamp,
            long origin,
            boolean lastEventTruncated,
            @NonNull List<GossipEvent> events) {
    }

    /**
     * Describes a CES file that we plan on writing at a future time.
     *
     * @param timestamp            the file's timestamp
     * @param signatureFilePresent if true then the file will have a signature file
     * @param lastEventTruncated   if true then the last event should be truncated
     * @param initialHash          the file's initial hash
     * @param finalHash            the file's final hash
     * @param events               the events that will go into the file in the order they will appear in the file
     */
    record PlannedConsensusEventFile(
            long timestamp,
            boolean signatureFilePresent,
            boolean lastEventTruncated,
            @Nullable Hash initialHash,
            @Nullable Hash finalHash,
            @NonNull List<GossipEvent> events) {
    }

    /**
     * Describes the data that we plan on writing at a future time.
     *
     * @param plannedStates                 the states that we plan on writing
     * @param plannedPreconsensusEventFiles the PCES files that we plan on writing
     * @param plannedConsensusEventFiles    the CES files that we plan on writing
     */
    record PlannedStreamData(
            @NonNull List<PlannedState> plannedStates,
            @NonNull List<PlannedPreconsensusEventFile> plannedPreconsensusEventFiles,
            @NonNull List<PlannedConsensusEventFile> plannedConsensusEventFiles) {
    }

    /**
     * Generate data that we will eventually write to disk. This data can be modified prior to writing it.
     *
     * @param random a random number generator
     * @return the generated data
     */
    @NonNull
    public PlannedStreamData generatePlannedStreamData(@NonNull final Random random) {
        return null; // TODO
    }

    /**
     * Write a series of states and stream files to disk.
     *
     * @param plannedStreamData the data to write
     * @param stateDirectory    the directory where the states and streams should be written
     */
    public void writePlannedStreamData(
            @NonNull final PlannedStreamData plannedStreamData, @NonNull final Path stateDirectory) {
        // TODO
    }

    /**
     * Generate a series of on-disk states.
     *
     * @param random          a random number generator
     * @param requestedStates describes the states to be generated
     * @return the generated states
     */
    @NonNull
    private static List<SignedState> generateStates(
            @NonNull final Random random, @NonNull List<PlannedState> requestedStates) {

        final List<SignedState> states = new ArrayList<>(requestedStates.size());

        for (final PlannedState requestedState : requestedStates) {
            // TODO
        }

        return states;
    }

    private static void generatePreconsensusEventStream(@NonNull final Random random) {}

    private static void generateConsensusEventStream(@NonNull final Random random, @NonNull final Path path) {}

    /**
     * Randomly generate a series of events.
     *
     * @param random a random number generator
     * @param count  the number of events to generate
     * @return the generated events
     */
    @SuppressWarnings("DataFlowIssue")
    @NonNull
    private static List<EventImpl> generateRawEvents(@NonNull final Random random, final int count) {
        final int nodeCount = 8;

        final List<StandardEventSource> sources = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            sources.add(new StandardEventSource(false, 1));
        }

        final StandardGraphGenerator generator =
                new StandardGraphGenerator(random.nextLong(), (EventSource<?>) sources);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(generator.generateEvent());
        }

        return events;
    }

    /**
     * Check if an event is an ancestor of another.
     *
     * @param eventA the first event
     * @param eventB the second event
     * @return true if event A is an ancestor of event B, false otherwise
     */
    private static boolean isAncestorOf(@NonNull final EventImpl eventA, @NonNull final EventImpl eventB) {
        final Set<EventImpl> visited = new HashSet<>();
        final Queue<EventImpl> queue = new LinkedList<>();

        queue.add(eventB);

        while (!queue.isEmpty()) {
            final EventImpl next = queue.remove();

            if (next.equals(eventA)) {
                return true;
            }

            final EventImpl selfParent = next.getSelfParent();
            if (selfParent != null && visited.add(selfParent)) {
                queue.add(selfParent);
            }

            final EventImpl otherParent = next.getOtherParent();
            if (otherParent != null && visited.add(otherParent)) {
                queue.add(otherParent);
            }
        }

        return false;
    }

    /**
     * Compare two events based on their topological ordering.
     *
     * @param eventA the first event
     * @param eventB the second event
     * @return -1 if event A is an ancestor of event B, 1 if event B is an ancestor of event A, or 0 if neither is an
     * ancestor of the other
     */
    private static int topologicalComparison(@NonNull final EventImpl eventA, @NonNull final EventImpl eventB) {
        if (isAncestorOf(eventA, eventB)) {
            return -1;
        } else if (isAncestorOf(eventB, eventA)) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Shuffle event order without breaking topological ordering.
     *
     * @param events the events to shuffle, list is not modified
     * @return the shuffled events
     */
    @NonNull
    private static List<EventImpl> shuffleEvents(@NonNull final Random random, @NonNull final List<EventImpl> events) {

        final List<EventImpl> shuffledEvents = new ArrayList<>(events);
        Collections.shuffle(shuffledEvents, random);

        // At this point in time, events are in an ordering that probably doesn't respect the topological ordering.
        // Sort by topological ordering. Since the sorting is stable, we are unlikely to get the same ordering
        // as the original list.

        shuffledEvents.sort(EventStreamValidationTestUtils::topologicalComparison);

        return shuffledEvents;
    }

    /**
     * Convert a list of EventImpls into GossipEvents.
     *
     * @param eventImpls the events to convert
     * @return the converted events
     */
    private static List<GossipEvent> convertToGossipEvents(@NonNull final List<EventImpl> eventImpls) {
        final List<GossipEvent> gossipEvents = new ArrayList<>(eventImpls.size());
        for (final EventImpl eventImpl : eventImpls) {
            gossipEvents.add(new GossipEvent(eventImpl.getHashedData(), eventImpl.getUnhashedData()));
        }
        return gossipEvents;
    }
}
