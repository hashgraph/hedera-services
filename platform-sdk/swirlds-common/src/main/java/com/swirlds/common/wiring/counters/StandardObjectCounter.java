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

import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A utility for counting the number of objects in a various part of the pipeline.
 */
public class StandardObjectCounter extends ObjectCounter {

    private final AtomicLong count = new AtomicLong(0);
    private final long sleepNanos;

    /**
     * Constructor.
     *
     * @param sleepDuration when a method needs to block, the duration to sleep while blocking
     */
    public StandardObjectCounter(@Nullable final Duration sleepDuration) {
        if (sleepDuration == null) {
            sleepNanos = -1;
        } else {
            sleepNanos = sleepDuration.toNanos();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRamp() {
        count.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableOnRamp() {
        count.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean attemptOnRamp() {
        count.getAndIncrement();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forceOnRamp() {
        count.getAndIncrement();
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
        boolean interrupted = false;

        while (count.get() > 0) {
            if (sleepNanos > 0) {
                try {
                    NANOSECONDS.sleep(sleepNanos);
                } catch (final InterruptedException ex) {
                    interrupted = true;
                }
            } else if (sleepNanos == 0) {
                Thread.yield();
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableWaitUntilEmpty() throws InterruptedException {
        while (count.get() > 0 && !Thread.currentThread().isInterrupted()) {
            if (sleepNanos > 0) {
                NANOSECONDS.sleep(sleepNanos);
            } else if (sleepNanos == 0) {
                Thread.yield();
            }
        }
    }
}
