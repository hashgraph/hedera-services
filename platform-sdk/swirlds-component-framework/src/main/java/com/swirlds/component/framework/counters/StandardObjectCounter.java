/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.counters;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A utility for counting the number of objects in various parts of the pipeline.
 */
public class StandardObjectCounter extends ObjectCounter {

    private final AtomicLong count = new AtomicLong(0);
    private final ManagedBlocker waitUntilEmptyBlocker;

    /**
     * Constructor.
     *
     * @param sleepDuration when a method needs to block, the duration to sleep while blocking
     */
    public StandardObjectCounter(@NonNull final Duration sleepDuration) {
        final long sleepNanos = sleepDuration.toNanos();
        waitUntilEmptyBlocker = new EmptyBlocker(count, sleepNanos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRamp(final long delta) {
        count.addAndGet(delta);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean attemptOnRamp(final long delta) {
        count.getAndAdd(delta);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forceOnRamp(final long delta) {
        count.getAndAdd(delta);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void offRamp(final long delta) {
        count.addAndGet(-delta);
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
