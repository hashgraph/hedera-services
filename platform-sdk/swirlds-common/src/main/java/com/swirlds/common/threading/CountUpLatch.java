package com.swirlds.common.threading;

import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.common.utility.CompareTo.isLessThan;

import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
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

    private final Time time;
    private final AtomicLong currentCount;
    private final Phaser phaser;

    /**
     * Create a new CountUpLatch with an initial count of 0.
     */
    public CountUpLatch() {
        this(0, OSTime.getInstance());
    }

    /**
     * Create a new CountUpLatch with the given initial count.
     *
     * @param initialCount the initial count
     */
    public CountUpLatch(final long initialCount) {
        this(initialCount, OSTime.getInstance());
    }

    /**
     * Create a new CountUpLatch with the given initial count.
     *
     * @param initialCount the initial count
     * @param time         provides wall clock time
     */
    public CountUpLatch(final long initialCount, final Time time) {
        this.time = time;
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
    public void set(final long count) {
        this.currentCount.getAndUpdate(original -> {
            if (count < original) {
                throw new IllegalArgumentException("Current count is " + original +
                        " but new count is " + count + ", cannot set count to a lower value");
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
    public void add(final long delta) {
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

        final Instant start = time.now();
        phaser.register();
        try {
            while (currentCount.get() < count && isLessThan(Duration.between(start, time.now()), timeToWait)) {
                phaser.arriveAndAwaitAdvance();
            }
        } finally {
            phaser.arriveAndDeregister();
        }

        return currentCount.get() >= count;
    }
}
