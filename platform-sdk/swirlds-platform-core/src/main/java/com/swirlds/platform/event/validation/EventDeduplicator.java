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
        recentEvents = new ConcurrentSequenceSet<>(
                0,
                1024,
                true,
                RecentEvent::generation);
    }

    /**
     * Check if the next event is a duplicate or ancient.
     *
     * @param event the event to check
     * @return true if the event is a duplicate or ancient, false otherwise
     */
    public synchronized boolean isDuplicate(@NonNull final GossipEvent event) throws InterruptedException {
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
