package com.swirlds.platform.event.creation;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Enforces event probation. An event is only eligible for use as a parent if it has been known about for a certain
 * amount of time (i.e. the probation period). This class is responsible for enforcing that probation period, and
 * preventing events from passing through until they are no longer on probation.
 */
public class ProbationBuffer {

    private final Duration probationDuration;

    private final Deque<GossipEvent> buffer = new ArrayDeque<>();

    public ProbationBuffer(@NonNull final PlatformContext platformContext) {

        probationDuration = platformContext.getConfiguration().getConfigData(EventCreationConfig.class)
                .globalProbation();
    }

    /**
     * Add an event to the probation buffer.
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) {
        buffer.addLast(event);
    }

    /**
     * Remove an event from the probation buffer if they are no longer on probation.
     *
     * @param now the current time
     * @return a list of events that have been released from probation
     */
    @NonNull
    List<GossipEvent> maybeReleaseEvents(@NonNull final Instant now) {
        final List<GossipEvent> releasedEvents = new ArrayList<>();
        final Iterator<GossipEvent> iterator = buffer.iterator();

        while (iterator.hasNext()) {
            final GossipEvent event = iterator.next();

            if (event.getSenderId() == null) {
                // We didn't receive this event through gossip, no need to enforce probation.
                iterator.remove();
                releasedEvents.add(event);
                continue;
            }

            final Instant eventReceivedTime = event.getTimeReceived();
            final Instant probationEndTime = eventReceivedTime.plus(probationDuration);

            if (now.isAfter(probationEndTime)) {
                iterator.remove();
                releasedEvents.add(event);
            } else {
                // Once we find an event that is still on probation we can stop looking.
                break;
            }
        }

        return releasedEvents;
    }

    public void flush() {
        // TODO we need a way to empty this buffer
    }

}
