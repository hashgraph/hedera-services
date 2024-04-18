/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class EventUtils {
    private static final Logger logger = LogManager.getLogger(EventUtils.class);

    public static int generationComparator(final PlatformEvent e1, final PlatformEvent e2) {
        return Long.compare(e1.getGeneration(), e2.getGeneration());
    }

    /**
     * Prepares consensus events for shadow graph during a restart or reconnect by sorting the events by generation and
     * checking for generation gaps.
     *
     * @param events events supplied by consensus
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
     * @param events events to look for generation gaps in, sorted in ascending order by generation
     * @throws IllegalArgumentException if any problem is found with the signed state events
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
                throw new IllegalArgumentException(String.format("Found gap between %s and %s", event, prev));
            }

            prev = event;
        }
    }

    /**
     * Calculate the creation time for a new event.
     * <p>
     * Regardless of whatever the host computer's clock says, the event creation time must always advance from self
     * parent to child. Further, the time in between the self parent and the child must be large enough so that every
     * transaction in the parent can be assigned a unique timestamp at nanosecond precision.
     *
     * @param now                        the current time
     * @param selfParentCreationTime     the creation time of the self parent
     * @param selfParentTransactionCount the number of transactions in the self parent
     * @return the creation time for the new event
     */
    @NonNull
    public static Instant calculateNewEventCreationTime(
            @NonNull final Instant now,
            @NonNull final Instant selfParentCreationTime,
            final int selfParentTransactionCount) {

        final int minimumIncrement = Math.max(1, selfParentTransactionCount);
        final Instant minimumNextEventTime = selfParentCreationTime.plusNanos(minimumIncrement);
        if (now.isBefore(minimumNextEventTime)) {
            return minimumNextEventTime;
        } else {
            return now;
        }
    }
}
