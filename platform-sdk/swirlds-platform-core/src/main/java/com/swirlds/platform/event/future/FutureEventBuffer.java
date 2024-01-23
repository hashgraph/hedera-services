package com.swirlds.platform.event.future;

import static com.swirlds.platform.consensus.ConsensusConstants.ROUND_FIRST;

import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.sequence.set.StandardSequenceSet;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Buffers events from the future (i.e. events with a birth round that is greater than the round that consensus is
 * currently working on). It is important to note that the future event buffer is only used to store events from the
 * near future that can be fully validated. Events from beyond the event horizon (i.e. far future events that cannot be
 * immediately validated) are never stored by any part of the system.
 * <p>
 * Output from the future event buffer is guaranteed to preserve topological ordering, as long as the input to the
 * buffer is topologically ordered.
 */
public class FutureEventBuffer {

    /**
     * The latest round of consensus that we know is being worked on. Due to asynchronous nature of the system, it is
     * possible that consensus is working on a round that is after than the current round, but we will never be working
     * on a round that is before the current round.
     */
    private long currentRound = ROUND_FIRST;

    // TODO metrics

    private final SequenceSet<FutureEvents> futureEvents = new StandardSequenceSet<>(
            ROUND_FIRST,
            8,
            true,
            FutureEvents::birthRound);

    /**
     * Add an event to the future event buffer.
     *
     * @param event the event to add
     * @return the event if it is not a time traveler, or null if the event is from the future and needs to be buffered.
     */
    @Nullable
    public GossipEvent addEvent(@NonNull final GossipEvent event) {
        if (event.getHashedData().getBirthRound() <= currentRound) {
            return event;
        }
        futureEvents.add(event);
        return null;
    }

    /**
     * Update the current event window. As the event window advances, time catches up to time travelers, and events that
     * were previously from the future are now from the present.
     *
     * @param eventWindow the new event window
     * @return a list of events that were previously from the future but are now from the present
     */
    public List<GossipEvent> updateEventWindow(@NonNull final NonAncientEventWindow eventWindow) {
        currentRound = eventWindow.pendingConsensusRound();

        final List<GossipEvent> events = new ArrayList<>();
        futureEvents.shiftWindow(currentRound, events::add);

        // TODO this can be rewritten to not require a sort
        events.sort(Comparator.comparingLong(GossipEvent::getGeneration));

        return null;
    }

    /**
     * Clear all data from the future event buffer.
     */
    public void clear() {
        futureEvents.clear();
    }
}
