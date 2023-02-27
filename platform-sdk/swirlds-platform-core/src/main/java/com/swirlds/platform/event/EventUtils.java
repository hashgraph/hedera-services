/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.internal.EventImpl;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class EventUtils {
    private static final Logger logger = LogManager.getLogger(EventUtils.class);

    /**
     * Converts the event to a short string. Should be replaced by {@link EventStrings#toShortString(EventImpl)}
     *
     * @param event
     * 		the event to convert
     * @return a short string
     */
    public static String toShortString(final EventImpl event) {
        return EventStrings.toShortString(event);
    }

    /**
     * Convert an array of events to a single string, using toShortString() on each, and separating with commas.
     *
     * @param events
     * 		array of events to convert
     * @return a single string with a comma separated list of all of the event strings
     */
    public static String toShortStrings(final EventImpl[] events) {
        if (events == null) {
            return "null";
        }
        return Arrays.stream(events).map(EventUtils::toShortString).collect(Collectors.joining(","));
    }

    public static String toShortStrings(final Iterable<EventImpl> events) {
        if (events == null) {
            return "null";
        }
        return StreamSupport.stream(events.spliterator(), false)
                .map(EventUtils::toShortString)
                .collect(Collectors.joining(","));
    }

    public static int generationComparator(final PlatformEvent e1, final PlatformEvent e2) {
        return Long.compare(e1.getGeneration(), e2.getGeneration());
    }

    /**
     * Prepares consensus events for shadow graph during a restart or reconnect by sorting the events by generation and
     * checking for generation gaps.
     *
     * @param events
     * 		events supplied by consensus
     * @return a list of input events, sorted and checked
     */
    public static List<EventImpl> prepareForShadowGraph(final EventImpl[] events) {
        if (events == null || events.length == 0) {
            return Collections.emptyList();
        }
        // shadow graph expects them to be sorted
        Arrays.sort(events, EventUtils::generationComparator);
        final List<EventImpl> forShadowGraph = Arrays.asList(events);
        try {
            checkForGenerationGaps(forShadowGraph);
        } catch (final IllegalArgumentException e) {
            logger.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "Issue found when checking event to provide to the Shadowgraph."
                            + "This issue might not be fatal, so loading of the state will proceed.",
                    e);
        }

        return forShadowGraph;
    }

    /**
     * Checks if there is a generation difference of more than 1 between events, if there is, throws an exception
     *
     * @param events
     * 		events to look for generation gaps in, sorted in ascending order by generation
     * @throws IllegalArgumentException
     * 		if any problem is found with the signed state events
     */
    public static void checkForGenerationGaps(final List<EventImpl> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Signed state events list should not be null or empty");
        }
        final ListIterator<EventImpl> listIterator = events.listIterator(events.size());

        // Iterate through the list from back to front, evaluating the youngest events first
        EventImpl prev = listIterator.previous();

        while (listIterator.hasPrevious()) {
            final EventImpl event = listIterator.previous();
            final long diff = prev.getGeneration() - event.getGeneration();
            if (diff > 1 || diff < 0) {
                // There is a gap in generations
                throw new IllegalArgumentException(
                        String.format("Found gap between %s and %s", event.toMediumString(), prev.toMediumString()));
            }

            prev = event;
        }
    }

    /**
     * Creates an event comparator using consensus time. If the event does not have a consensus time, the estimated time
     * is used instead.
     *
     * @return the comparator
     */
    public static int consensusPriorityComparator(final EventImpl x, final EventImpl y) {
        if (x == null || y == null) {
            return 0;
        }
        final Instant xTime = x.getConsensusTimestamp() == null ? x.getEstimatedTime() : x.getConsensusTimestamp();
        final Instant yTime = y.getConsensusTimestamp() == null ? y.getEstimatedTime() : y.getConsensusTimestamp();
        if (xTime == null || yTime == null) {
            return 0;
        }
        return xTime.compareTo(yTime);
    }

    /**
     * Get the creator ID of the event. If null return {@link EventConstants#CREATOR_ID_UNDEFINED}.
     *
     * @param event
     * 		the event
     * @return the creator ID as {@code long} of the given event, or the self-ID
     * 		if the given event is {@code null}
     */
    public static long getCreatorId(final BaseEvent event) {
        if (event == null) {
            return EventConstants.CREATOR_ID_UNDEFINED;
        } else {
            return event.getHashedData().getCreatorId();
        }
    }

    /**
     * Compute the creation time of a new event.
     *
     * @param now
     * 		a time {@code Instant}
     * @param selfParent
     * 		the self-parent of the event to be created
     * @return a time {@code Instant} which defines the creation time of an event
     */
    public static Instant getChildTimeCreated(final Instant now, final BaseEvent selfParent) {
        Instant timeCreated = now;

        if (selfParent != null) {
            // Ensure that events created by self have a monotonically increasing creation time.
            // This is true when the computer's clock is running normally.
            // If the computer's clock is reset to an earlier time, then the Instant.now() call
            // above may be earlier than the self-parent's creation time. In that case, it
            // advances to several nanoseconds later than the parent. If the clock is only set back
            // a short amount, then the timestamps will only slow down their advancement for a
            // little while, then go back to advancing one second per second. If the clock is set
            // far in the future, then the parent is created, then the clock is set to the correct
            // time, then the advance will slow down for a long time. One solution for that is to
            // generate no events for enough rounds that the parent will not exist (will be null
            // at this point), and then the correct clock time can be used again. (Assuming this
            // code has implemented nulling out parents from extremely old rounds).
            // If event x is followed by y, then y should be at least n nanoseconds later than x,
            // where n is the number of transactions in x (so each can have a different time),
            // or n=1 if there are no transactions (so each event is a different time).

            final Transaction[] transactions = selfParent.getHashedData().getTransactions();
            long minimumTimeIncrement = 1;
            if (transactions != null && transactions.length > 0) {
                minimumTimeIncrement = transactions.length;
            }

            final Instant minimumNextEventTime =
                    selfParent.getHashedData().getTimeCreated().plusNanos(minimumTimeIncrement);

            if (timeCreated.isBefore(minimumNextEventTime)) {
                timeCreated = minimumNextEventTime;
            }
        }

        return timeCreated;
    }

    /**
     * Get the generation of an event. Returns {@value EventConstants#GENERATION_UNDEFINED} for null events.
     *
     * @param event
     * 		an event
     * @return the generation number of the given event,
     * 		or {@value EventConstants#GENERATION_UNDEFINED} is the event is {@code null}
     */
    public static long getEventGeneration(final BaseEvent event) {
        if (event == null) {
            return EventConstants.GENERATION_UNDEFINED;
        }
        return event.getHashedData().getGeneration();
    }

    /**
     * Get the base hash of an event. Returns null for null events.
     *
     * @param event
     * 		an event
     * @return a {@code byte[]} which contains the hash bytes of the given event, or {@code null}
     * 		if the given event is {@code null}
     */
    public static byte[] getEventHash(final BaseEvent event) {
        if (event == null) {
            return null;
        }
        return event.getHashedData().getHash().getValue();
    }
}
