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

package com.swirlds.common.threading;

import static com.swirlds.common.utility.CompareTo.isLessThan;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongBinaryOperator;

/**
 * Similar to a {@link java.util.concurrent.CountDownLatch}, but counts up instead.
 */
public class CountUpLatch {

    private final AtomicLong currentCount;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    /**
     * Create a new CountUpLatch with an initial count of 0.
     */
    public CountUpLatch() {
        this(0);
    }

    /**
     * Create a new CountUpLatch with the given initial count.
     *
     * @param initialCount the initial count
     */
    public CountUpLatch(final long initialCount) {
        this.currentCount = new AtomicLong(initialCount);
    }

    /**
     * Get the current count.
     *
     * @return the current count
     */
    public long getCount() {
        return currentCount.get();
    }

    /**
     * Increment the count by 1.
     * <p>
     * Methods that update the count are not mutually thread safe. Concurrent threads should never attempt to update the
     * count. It is, however, safe for many threads to concurrently call methods that query the count.
     */
    public void increment() {
        add(1);
    }

    /**
     * Used to set the count in a way that will not allow the count to decrease.
     */
    private static final LongBinaryOperator SET_COUNT =
            (final long previous, final long proposed) -> Math.max(proposed, previous);

    /**
     * Set the count to a higher value.
     * <p>
     * Methods that update the count are not mutually thread safe. Concurrent threads should never attempt to update the
     * count. It is, however, safe for many threads to concurrently call methods that query the count.
     *
     * @param count the new count, must be greater than the current value
     * @throws IllegalArgumentException if the new count is less than the current count (the state of this object is
     *                                  messed up if this happens -- this is an unrecoverable error)
     */
    public void set(final long count) {
        final long result = currentCount.accumulateAndGet(count, SET_COUNT);
        if (result != count) {
            throw new IllegalArgumentException(
                    "Can't set the count to a lower value. Previous = " + result + ", provided = " + count);
        }
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Increment the count by the given delta.
     * <p>
     * Methods that update the count are not mutually thread safe. Concurrent threads should never attempt to update the
     * count. It is, however, safe for many threads to concurrently call methods that query the count.
     *
     * @param delta the amount to increment the count by
     * @throws IllegalArgumentException if the delta is negative
     */
    public void add(final long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("Cannot add a negative delta (" + delta + ")");
        }
        currentCount.addAndGet(delta);

        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wait until the count reaches the given value.
     *
     * @param count the count to wait for
     * @throws InterruptedException if interrupted while waiting
     */
    public void await(final long count) throws InterruptedException {
        while (currentCount.get() < count) {
            lock.lock();
            try {
                if (currentCount.get() >= count) {
                    return;
                }
                condition.await();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Compute the remaining time we need to wait.
     *
     * @param start      the time we started waiting
     * @param timeToWait the maximum time to wait
     * @return the remaining time to wait
     */
    private static Duration getRemainingTime(final Instant start, final Duration timeToWait) {
        final Instant now = Instant.now();
        final Duration elapsed = Duration.between(start, now);
        if (isLessThan(timeToWait, elapsed)) {
            // we are out of time
            return Duration.ZERO;
        }
        return timeToWait.minus(elapsed);
    }

    /**
     * Wait until the count reaches the given value.
     *
     * @param count      the count to wait for
     * @param timeToWait the maximum time to wait
     * @return true if the count reached the given value, false if the time to wait expired
     * @throws InterruptedException if interrupted while waiting
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public boolean await(final long count, final Duration timeToWait) throws InterruptedException {
        final Instant start = Instant.now();

        while (currentCount.get() < count) {
            final long remainingMillis = getRemainingTime(start, timeToWait).toMillis();
            if (remainingMillis <= 0) {
                return currentCount.get() >= count;
            }

            lock.lock();
            try {
                if (currentCount.get() >= count) {
                    return true;
                }
                condition.await(remainingMillis, TimeUnit.MILLISECONDS);
            } finally {
                lock.unlock();
            }
        }

        return true;
    }
}
