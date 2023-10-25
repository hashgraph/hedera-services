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

package com.swirlds.common.wiring.counters;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/**
 * A utility for counting the number of objects in a various part of the pipeline. Will apply backpressure if the number
 * of objects exceeds a specified capacity.
 */
public class BackpressureObjectCounter extends ObjectCounter {

    private final AtomicLong count = new AtomicLong(0);
    private final LongUnaryOperator increment;

    private final ManagedBlocker onRampBlocker;
    private final ManagedBlocker waitUntilEmptyBlocker;

    /**
     * Constructor.
     *
     * @param capacity      the maximum number of objects that can be in the part of the system that this object is
     *                      being used to monitor before backpressure is applied
     * @param sleepDuration when a method needs to block, the duration to sleep while blocking
     */
    public BackpressureObjectCounter(final long capacity, @NonNull final Duration sleepDuration) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero");
        }

        increment = count -> {
            if (count >= capacity) {
                throw new NoCapacityException();
            }
            return count + 1;
        };

        final int sleepNanos = (int) sleepDuration.toNanos();

        onRampBlocker = new BackpressureBlocker(count, increment, sleepNanos);
        waitUntilEmptyBlocker = new EmptyBlocker(count, sleepNanos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRamp() {
        try {
            count.updateAndGet(increment);
            // Best case scenario: capacity is was immediately available.
        } catch (final NoCapacityException e) {
            // Slow case. Capacity wasn't available, so we need to block.
            try {
                // This will block until capacity is available and the count has been incremented.
                ForkJoinPool.managedBlock(onRampBlocker);
            } catch (final InterruptedException ex) {
                // This should be impossible.
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while blocking on an onRamp()");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean attemptOnRamp() {
        try {
            count.updateAndGet(increment);
            return true;
        } catch (final NoCapacityException e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forceOnRamp() {
        count.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void offRamp() {
        count.decrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCount() {
        return count.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilEmpty() {
        if (count.get() == 0) {
            return;
        }

        try {
            ForkJoinPool.managedBlock(waitUntilEmptyBlocker);
        } catch (final InterruptedException e) {
            // This should be impossible.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while blocking on an waitUntilEmpty()");
        }
    }
}
