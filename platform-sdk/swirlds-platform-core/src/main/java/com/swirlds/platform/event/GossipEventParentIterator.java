package com.swirlds.platform.event;

import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Objects;

// TODO unit test this

/**
 * Iterates over the parents of a {@link GossipEvent}.
 */
public class GossipEventParentIterator implements Iterator<EventDescriptor> {

    private final GossipEvent event;
    private final int eventCount;
    private final boolean returnSelfParent;
    private int returnedCount = 0;

    /**
     * Iterate over the parents of an event. Iteration order is self parent followed by other parents in the order they
     * appear in the serialized event.
     *
     * @param event the event whose parents we want to iterate over
     */
    public GossipEventParentIterator(@NonNull final GossipEvent event) {
        this(event, false);
    }

    /**
     * Iterate over the parents of an event. Iteration order is self parent followed by other parents in the order they
     * appear in the serialized event.
     *
     * @param event          the event whose parents we want to iterate over
     * @param skipSelfParent whether to skip the self parent. If true, will only iterate over other parents.
     */
    public GossipEventParentIterator(@NonNull final GossipEvent event, final boolean skipSelfParent) {
        this.event = Objects.requireNonNull(event);
        returnSelfParent = !skipSelfParent && event.getHashedData().getSelfParent() != null;
        eventCount = (returnSelfParent ? 1 : 0) + event.getHashedData().getOtherParents().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return returnedCount < eventCount;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public EventDescriptor next() {
        if (!hasNext()) {
            throw new IllegalStateException("No more parents");
        }

        final EventDescriptor parent;
        if (returnedCount == 0) {
            if (returnSelfParent) {
                parent = event.getHashedData().getSelfParent();
            } else {
                parent = event.getHashedData().getOtherParents().get(0);
            }
        } else {
            final int index = returnedCount - (returnSelfParent ? 1 : 0);
            parent = event.getHashedData().getOtherParents().get(index);
        }

        returnedCount++;

        return Objects.requireNonNull(parent);
    }
}
