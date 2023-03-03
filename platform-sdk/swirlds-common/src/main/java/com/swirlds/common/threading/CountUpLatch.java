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
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Similar to a {@link java.util.concurrent.CountDownLatch}, but counts up instead.
 * <p>
 * Note: this may not be performant if waiting for a very large number of count increases or for very high frequency
 * updates. Each time the count increases we touch concurrency objects.
 */
public class CountUpLatch {

    private final AtomicLong currentCount;
    private final Phaser phaser;

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
        this.phaser = new Phaser(1);
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
     */
    public void increment() {
        add(1);
    }

    /**
     * Set the count to a higher value.
     *
     * @param count the new count, must be greater than the current value
     * @throws IllegalArgumentException if the new count is less than the current count
     */
    public synchronized void set(final long count) {
        this.currentCount.getAndUpdate(original -> {
            if (count < original) {
                throw new IllegalArgumentException("Current count is " + original + " but new count is " + count
                        + ", cannot set count to a lower value");
            }
            return count;
        });
        phaser.arriveAndAwaitAdvance();
    }

    /**
     * Increment the count by the given delta.
     *
     * @param delta the amount to increment the count by
     */
    public synchronized void add(final long delta) {
        currentCount.addAndGet(delta);
        phaser.arriveAndAwaitAdvance();
    }

    /**
     * Wait until the count reaches the given value.
     *
     * @param count the count to wait for
     * @throws InterruptedException if interrupted while waiting
     */
    public void await(final long count) throws InterruptedException {
        if (currentCount.get() >= count) {
            return;
        }

        phaser.register();
        try {
            while (currentCount.get() < count) {
                phaser.arriveAndAwaitAdvance();
            }
        } finally {
            phaser.arriveAndDeregister();
        }
    }

    /**
     * Wait until the count reaches the given value.
     *
     * @param count      the count to wait for
     * @param timeToWait the maximum time to wait
     * @return true if the count reached the given value, false if the time to wait expired
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean await(final long count, final Duration timeToWait) throws InterruptedException {
        if (currentCount.get() >= count) {
            return true;
        }

        final Instant start = Instant.now();
        phaser.register();
        try {
            while (currentCount.get() < count) {
                final Instant now = Instant.now();
                final Duration elapsed = Duration.between(start, now);
                if (isLessThan(timeToWait, elapsed)) {
                    break;
                }

                final long remainingMillis = timeToWait.minus(elapsed).toMillis();

                final int phase = phaser.arrive();
                phaser.awaitAdvanceInterruptibly(phase, remainingMillis, TimeUnit.MILLISECONDS);
            }
        } catch (final TimeoutException e) {
            // ignore
        } finally {
            phaser.arriveAndDeregister();
        }

        return currentCount.get() >= count;
    }
}
