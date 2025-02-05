/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle.disk;

import static com.swirlds.state.merkle.logging.StateLogger.logQueueAdd;
import static com.swirlds.state.merkle.logging.StateLogger.logQueueIterate;
import static com.swirlds.state.merkle.logging.StateLogger.logQueueRemove;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.merkle.queue.QueueCodec;
import com.swirlds.state.merkle.queue.QueueNode;
import com.swirlds.state.merkle.queue.QueueState;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

// TODO: reduce code duplication between this class and OnDiskReadableQueueState
/**
 * An implementation of {@link com.swirlds.state.spi.WritableQueueState} based on {@link QueueNode}.
 * @param <E> The type of element in the queue
 */
public class OnDiskWritableQueueState<E> extends WritableQueueStateBase<E> {

    @NonNull
    private final VirtualMap virtualMap;

    @NonNull
    private final Codec<E> valueCodec;

    public OnDiskWritableQueueState(
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            @NonNull final Codec<E> valueCodec,
            @NonNull final VirtualMap virtualMap) {
        super(serviceName, stateKey);

        this.valueCodec = requireNonNull(valueCodec);
        this.virtualMap = Objects.requireNonNull(virtualMap);
    }

    @Override
    protected void addToDataSource(@NonNull E element) {
        final QueueState state = getState();
        virtualMap.put(getVirtualMapKey(state.getTailAndIncrement()), element, valueCodec);
        // Log to transaction state log, what was added
        logQueueAdd(getLabel(), element);
    }

    @Override
    protected void removeFromDataSource() {
        final QueueState state = getState();
        if (!state.isEmpty()) {
            // TODO: double check VirtualMap#remove return type
            final var valueToRemove = getFromStore(state.getHead());
            virtualMap.remove(getVirtualMapKey(state.getHeadAndIncrement()));
            // Log to transaction state log, what was added
            logQueueRemove(getLabel(), valueToRemove);
        } else {
            // TODO: double check, this is according to the logic in `15090-D-fcqueue-to-virtualmap`
            // Log to transaction state log, what was added
            logQueueRemove(getLabel(), null);
        }
    }

    /** Iterate over all elements */
    @NonNull
    @Override
    protected Iterator<E> iterateOnDataSource() {
        final QueueState state = getState();
        final QueueIterator it = new QueueIterator(state.getHead(), state.getTail());
        // Log to transaction state log, what was iterated
        logQueueIterate(getLabel(), state.getTail() - state.getHead(), it);
        it.reset();
        return it;
    }

    @NonNull
    private E getFromStore(final long index) {
        final var value = virtualMap.get(getVirtualMapKey(index), valueCodec);
        if (value == null) {
            throw new IllegalStateException("Can't find queue element at index " + index + " in the store");
        }
        return value;
    }

    // TODO: refactor
    private QueueState getState() {
        final int stateId = getStateId();

        if (stateId < 0 || stateId > 65535) {
            throw new IllegalArgumentException("State ID " + stateId + " must fit in [0..65535]");
        }

        final ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) stateId);

        final Bytes stateIdBytes = Bytes.wrap(buffer.array());

        return virtualMap.get(stateIdBytes, QueueCodec.INSTANCE);
    }

    // TODO: test this method
    // TODO: refactor? (it is duplicated in OnDiskReadableQueueState)
    /**
     * Generates a 10-byte big-endian key identifying an element in the Virtual Map.
     * <ul>
     *   <li>The first 2 bytes store the unsigned 16-bit state ID</li>
     *   <li>The next 8 bytes store the {@code index}</li>
     * </ul>
     *
     * @param index the element index within this queue
     * @return a {@link Bytes} object containing exactly 10 bytes in big-endian order
     * @throws IllegalArgumentException if the state ID is outside [0..65535]
     */
    private Bytes getVirtualMapKey(final long index) {
        final int stateId = getStateId();

        if (stateId < 0 || stateId > 65535) {
            throw new IllegalArgumentException("State ID " + stateId + " must fit in [0..65535]");
        }

        final ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) stateId);
        buffer.putLong(index);

        return Bytes.wrap(buffer.array());
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
