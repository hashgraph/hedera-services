package com.swirlds.platform.event.future;

import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Stores events with a future birth round. Preserves event order so that we don't need to do a topological sort when
 * events from the round are no longer time travelers.
 *
 * @param birthRound a birth round
 * @param eventList  a list of events with the specified birth round, events will be inserted in strict topological
 *                   order
 */
public record FutureEvents(long birthRound, @NonNull List<GossipEvent> eventList) {

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Long.hashCode(birthRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof final FutureEvents that) {
            return birthRound == that.birthRound;
        }
        return false;
    }
}
