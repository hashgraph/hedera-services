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

package com.swirlds.common.wiring.counters;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is used to implement backpressure in a {@link java.util.concurrent.ForkJoinPool} friendly way.
 */
class BackpressureBlocker implements ManagedBlocker {

    /**
     * The current count. This object will attempt to block until this count can be increased by one without exceeding
     * the maximum capacity.
     */
    private final AtomicLong count;

    /**
     * The maximum desired capacity. It is possible that the current count may exceed this capacity (i.e. if
     * {@link ObjectCounter#forceOnRamp()} is used to bypass the capacity).
     */
    private final long capacity;

    /**
     * The amount of time to sleep while waiting for capacity to become available, or 0 to not sleep.
     */
    private final long sleepNanos;

    private long delta = 1L;

    /**
     * Constructor.
     *
     * @param count      the counter to use
     * @param capacity   the maximum number of objects that can be in the part of the system that this object is being
     *                   used to monitor before backpressure is applied
     * @param sleepNanos the number of nanoseconds to sleep while blocking, or 0 to not sleep
     */
    public BackpressureBlocker(@NonNull final AtomicLong count, final long capacity, final long sleepNanos) {
        this.count = Objects.requireNonNull(count);
        this.capacity = capacity;
        this.sleepNanos = sleepNanos;
    }

    public BackpressureBlocker withDelta(long delta) {
        this.delta = delta;
        return this;
    }

    /**
     * This method just needs to block for a while. It sleeps to avoid burning CPU cycles. Since this method always
     * returns false, the fork join pool will use {@link #isReleasable()} exclusively for determining if it's time to
     * stop blocking.
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

        // Although we could technically check the count here and stop the back pressure if the count is below the
        // threshold, it's simpler not to. Immediately after this method is called, isReleasable() will be called,
        // which will do the checking for us. Easier to just let that method do the work, and have this method
        // only be responsible for sleeping.
        return false;
    }

    /**
     * Checks if it's ok to stop blocking.
     *
     * @return true if capacity has been reserved and it's ok to stop blocking, false if capacity could not be reserved
     * and we need to continue blocking.
     */
    @Override
    public boolean isReleasable() {
        final long resultingCount = count.addAndGet(delta);
        if (resultingCount <= capacity) {
            // We didn't violate capacity by incrementing the count, so we're done.
            return true;
        } else {
            // We may have violated capacity restrictions by incrementing the count.
            // Decrement count and take the slow pathway.
            count.addAndGet(-delta);
            return false;
        }
    }
}
