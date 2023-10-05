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

package com.swirlds.common.wiring.internal;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/**
 * A utility for counting the number of objects in a various part of the pipeline. Will apply backpressure if the number
 * of objects exceeds a specified capacity.
 */
public class BackpressureObjectCounter extends AbstractObjectCounter {

    private final AtomicLong count = new AtomicLong(0);
    private final LongUnaryOperator increment;
    private final long sleepNanos;

    private static class NoCapacityException extends RuntimeException {
        public NoCapacityException() {}
    }

    /**
     * Constructor.
     *
     * @param capacity      the maximum number of objects that can be in the part of the system that this object is
     *                      being used to monitor before backpressure is applied
     * @param sleepDuration when there is no capacity available, sleep for this duration before trying again, if null
     *                      then do not sleep
     */
    public BackpressureObjectCounter(final long capacity, @Nullable Duration sleepDuration) {

        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero");
        }

        if (sleepDuration == null) {
            sleepNanos = -1;
        } else {
            sleepNanos = sleepDuration.toNanos();
        }

        increment = count -> {
            if (count >= capacity) {
                throw new NoCapacityException();
            }
            return count + 1;
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRamp() {
        while (true) {
            try {
                count.updateAndGet(increment);
                return;
            } catch (final NoCapacityException e) {
                if (sleepNanos >= 0) {
                    try {
                        NANOSECONDS.sleep(sleepNanos);
                    } catch (final InterruptedException ex) {
                        // If we get interrupted we will end up spinning around in this loop
                        // very rapidly without actually sleeping... but that's should be rare,
                        // and so the performance implications are acceptable.
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableOnRamp() throws InterruptedException {
        while (true) {
            try {
                count.updateAndGet(increment);
                return;
            } catch (final NoCapacityException e) {
                if (sleepNanos >= 0) {
                    NANOSECONDS.sleep(sleepNanos);
                }
            }
        }
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
}
