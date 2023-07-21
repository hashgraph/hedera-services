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

package com.swirlds.platform.event.validation;

import com.swirlds.common.sequence.set.ConcurrentSequenceSet;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Deduplicates events.
 */
public class EventDeduplicator {

    private final SequenceSet<RecentEvent> recentEvents;

    /**
     * Create a new event deduplicator.
     */
    public EventDeduplicator() {
        recentEvents = new ConcurrentSequenceSet<>(0, 1024, true, RecentEvent::generation);
    }

    /**
     * Check if the next event is a duplicate or ancient.
     *
     * @param event the event to check
     * @return true if the event is a duplicate or ancient, false otherwise
     */
    public synchronized boolean isDuplicate(@NonNull final GossipEvent event) {
        final RecentEvent recentEvent = RecentEvent.of(event);
        final boolean added = recentEvents.add(recentEvent);

        return !added;
    }

    /**
     * Set the current minimum generation non-ancient.
     *
     * @param minimumGenerationNonAncient the current minimum generation non-ancient
     */
    public synchronized void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        recentEvents.shiftWindow(minimumGenerationNonAncient);
    }
}
