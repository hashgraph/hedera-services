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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/**
 * This class is used to implement backpressure in a {@link java.util.concurrent.ForkJoinPool} friendly way.
 */
class BackpressureBlocker implements ManagedBlocker {

    private final AtomicLong count;
    private final LongUnaryOperator increment;
    private final int sleepNanos;

    /**
     * Constructor.
     *
     * @param count                     the counter to use
     * @param increment                 a function that increments the counter if possible and throws a
     *                                  {@link NoCapacityException} if it is not possible
     * @param sleepNanos                the number of nanoseconds to sleep while blocking, or -1 to not sleep
     */
    public BackpressureBlocker(
            @NonNull final AtomicLong count, @NonNull final LongUnaryOperator increment, final int sleepNanos) {
        this.count = Objects.requireNonNull(count);
        this.increment = Objects.requireNonNull(increment);
        this.sleepNanos = sleepNanos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean block() throws InterruptedException {
        if (sleepNanos > 0) {
            try {
                NANOSECONDS.sleep(sleepNanos);
            } catch (final InterruptedException e) {
                // Don't throw an interrupted exception, but allow the thread to maintain its interrupted status.
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReleasable() {
        // Make an attempt to reserve capacity.

        try {
            count.updateAndGet(increment);
            // Capacity was reserved, no need to block.
            return true;
        } catch (final NoCapacityException e) {
            // We were unable to reserve capacity, so blocking will be necessary.
            return false;
        }
    }
}
