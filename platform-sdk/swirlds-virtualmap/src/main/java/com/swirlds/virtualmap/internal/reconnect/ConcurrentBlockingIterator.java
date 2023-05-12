/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.reconnect;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This iterator was designed specifically for use with {@link VirtualRootNode} during reconnect on the
 * learner, but in concept could be adapted for more general use. This class enables a publisher/subscriber
 * approach to an iterator. New items are added via {@link #supply(Object, long, TimeUnit)}, while items are
 * consumed using {@link #next()}. {@link #hasNext()} will return true until there are no more items to
 * consume and will block if there are no items and {@link #close()} has not been called. Critically,
 * you must call {@link #close()} for the iterator to ever return {@code false} from {@link #hasNext()} or
 * throw {@link NoSuchElementException} from {@link #next()}. If new elements are supplied faster than they
 * are consumed, then {@link #supply(Object, long, TimeUnit)} will block until space becomes available. The
 * constructor allows you to define what the size of the inner buffer is.
 *
 * @param <T>
 * 		The type
 */
public class ConcurrentBlockingIterator<T> implements Iterator<T> {

    /**
     * The underlying buffer of items into which elements are placed prior to removal.
     */
    private final LinkedBlockingQueue<T> buffer;

    /**
     * Indicates that the array has been closed. Once closed, new elements cannot be
     * supplied but existing elements in the buffer can still be consumed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * The maximum time to wait for a result to appear in the buffer before we give up and die.
     */
    private volatile int maxWaitTime;

    /**
     * The time unit for {@link #maxWaitTime}.
     */
    private volatile TimeUnit maxWaitTimeUnit;

    /**
     * Contains a reference to the next element. This is basically a temporary variable.
     */
    private T next;

    /**
     * Create a new {@link ConcurrentBlockingIterator}.
     *
     * @param bufferSize
     * 		The size of the internal buffer. Must be greater than 0.
     * @param maxWaitTime
     * 		The maximum time to wait on the buffer before throwing an exception if it fails to deliver results.
     * 		Must be non-negative.
     * @param maxWaitTimeUnit
     * 		The time unit for max wait time. If null, defaults to {@link TimeUnit#MILLISECONDS}.
     */
    public ConcurrentBlockingIterator(final int bufferSize, final int maxWaitTime, final TimeUnit maxWaitTimeUnit) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be greater than zero");
        }

        if (maxWaitTime < 0) {
            throw new IllegalArgumentException("maxWaitTime must not be negative");
        }

        this.buffer = new LinkedBlockingQueue<>(bufferSize);
        this.maxWaitTime = maxWaitTime;
        this.maxWaitTimeUnit = maxWaitTimeUnit == null ? MILLISECONDS : maxWaitTimeUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }

        // Busy loop. This will lead to really high performance at the cost of a CPU, but since
        // we're in the middle of some pretty serious reconnect stuff, we should allow that.

        // if closed and buffer.poll != null || !closed
        final long waitMillis = maxWaitTimeUnit.toMillis(maxWaitTime);
        final long timeOutWhenMillisAre = System.currentTimeMillis() + waitMillis;
        while ((next = buffer.poll()) == null) {
            if (closed.get()) {
                return false;
            } else {
                if (System.currentTimeMillis() > timeOutWhenMillisAre) {
                    throw new RuntimeException(new TimeoutException("Timed out trying to read from buffer"));
                }
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T next() {
        if (next == null && !hasNext()) {
            throw new NoSuchElementException();
        }

        assert next != null : "Unexpected null next";
        final T ret = next;
        next = null;
        return ret;
    }

    /**
     * Supplies a new element to the iterator, blocking if the internal buffer is full.
     *
     * @param element
     * 		The element to add. Cannot be null.
     * @return true if submitted, false if the timeout was exceeded.
     * @throws InterruptedException
     * 		If interrupted while waiting
     */
    public boolean supply(final T element, final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        if (closed.get()) {
            throw new IllegalStateException("Cannot supply elements to a closed ConcurrentBlockingIterator");
        }

        Objects.requireNonNull(element);
        return buffer.offer(element, timeout, timeUnit);
    }

    /**
     * Closes the iterator. After closing, no elements may be added, but existing elements in the iterator
     * can still be retrieved.
     */
    public void close() {
        closed.set(true);
    }

    /**
     * Adjust the max wait time.
     *
     * @param maxWaitTime
     * 		The maximum time to wait on the buffer before throwing an exception if it fails to deliver results.
     * 		Must be non-negative.
     * @param maxWaitTimeUnit
     * 		The time unit for max wait time. If null, defaults to {@link TimeUnit#MILLISECONDS}.
     */
    public void setMaxWaitTime(final int maxWaitTime, final TimeUnit maxWaitTimeUnit) {
        if (maxWaitTime < 0) {
            throw new IllegalArgumentException("maxWaitTime must not be negative");
        }

        this.maxWaitTime = maxWaitTime;
        this.maxWaitTimeUnit = maxWaitTimeUnit == null ? MILLISECONDS : maxWaitTimeUnit;
    }
}
