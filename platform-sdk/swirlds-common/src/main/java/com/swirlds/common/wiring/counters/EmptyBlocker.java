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

/**
 * This class is used to implement flushing in a {@link java.util.concurrent.ForkJoinPool} friendly way. Blocks until
 * the count reaches zero.
 */
class EmptyBlocker implements ManagedBlocker {

    private final AtomicLong count;
    private final int sleepNanos;
    private final boolean throwInterruptedException;

    /**
     * Constructor.
     *
     * @param count                     the counter to use
     * @param sleepNanos                the number of nanoseconds to sleep while blocking, or -1 to not sleep
     * @param throwInterruptedException if true, throw an {@link InterruptedException} when interrupted, otherwise allow
     *                                  the thread to maintain its interrupted status but do not throw
     */
    public EmptyBlocker(
            @NonNull final AtomicLong count, final int sleepNanos, final boolean throwInterruptedException) {
        this.count = Objects.requireNonNull(count);
        this.sleepNanos = sleepNanos;
        this.throwInterruptedException = throwInterruptedException;
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
                if (throwInterruptedException) {
                    throw e;
                } else {
                    // Don't throw an interrupted exception, but allow the thread to maintain its interrupted status.
                    Thread.currentThread().interrupt();
                }
            }
        } else if (throwInterruptedException && Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReleasable() {
        return count.get() == 0;
    }
}
