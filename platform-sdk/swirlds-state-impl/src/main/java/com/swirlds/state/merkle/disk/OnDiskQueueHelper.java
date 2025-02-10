package com.swirlds.state.merkle.disk;

import com.hedera.pbj.runtime.Codec;
import com.swirlds.state.merkle.StateUtils;
import com.swirlds.state.merkle.queue.QueueCodec;
import com.swirlds.state.merkle.queue.QueueState;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static com.swirlds.state.merkle.StateUtils.computeLabel;
import static com.swirlds.state.merkle.StateUtils.getVirtualMapKey;
import static com.swirlds.state.merkle.logging.StateLogger.logQueueIterate;
import static java.util.Objects.requireNonNull;

public final class OnDiskQueueHelper<E> {

    @NonNull
    private final String serviceName;

    @NonNull
    private final String stateKey;

    @NonNull
    private final VirtualMap virtualMap;

    @NonNull
    private final Codec<E> valueCodec;

    public OnDiskQueueHelper(@NonNull final String serviceName,
                             @NonNull final String stateKey,
                             @NonNull final VirtualMap virtualMap,
                             @NonNull final Codec<E> valueCodec) {
        this.serviceName = requireNonNull(serviceName);
        this.stateKey = requireNonNull(stateKey);
        this.virtualMap = requireNonNull(virtualMap);
        this.valueCodec = requireNonNull(valueCodec);
    }

    @NonNull
    public Iterator<E> iterateOnDataSource() {
        final QueueState state = getState();
        final QueueIterator it = new QueueIterator(state.getHead(), state.getTail());
        // Log to transaction state log, what was iterated
        logQueueIterate(computeLabel(serviceName, stateKey), state.getTail() - state.getHead(), it);
        it.reset();
        return it;
    }

    @NonNull
    public E getFromStore(final long index) {
        final var value = virtualMap.get(getVirtualMapKey(serviceName, stateKey, index), valueCodec);
        if (value == null) {
            throw new IllegalStateException("Can't find queue element at index " + index + " in the store");
        }
        return value;
    }

    public QueueState getState() {
        return virtualMap.get(getVirtualMapKey(serviceName, stateKey), QueueCodec.INSTANCE);
    }

    public void updateState(@NonNull final QueueState state) {
        virtualMap.put(getVirtualMapKey(serviceName, stateKey), state, QueueCodec.INSTANCE);
    }

    /**
     * A tiny utility class to iterate over the queue node.
     */
    private class QueueIterator implements Iterator<E> {

        // Queue position to start from, inclusive
        private final long start;

        // Queue position to iterate up to, exclusive
        private final long limit;

        // The current iterator position, start <= current < limit
        private long current;

        // Start (inc), limit (exc)
        public QueueIterator(final long start, final long limit) {
            this.start = start;
            this.limit = limit;
            reset();
        }

        @Override
        public boolean hasNext() {
            return current < limit;
        }

        @Override
        public E next() {
            if (current == limit) {
                throw new NoSuchElementException();
            }
            try {
                return getFromStore(current++);
            } catch (final IllegalStateException e) {
                throw new ConcurrentModificationException(e);
            }
        }

        void reset() {
            current = start;
        }
    }
}
