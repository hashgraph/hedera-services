/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.utility.throttle;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.CompareTo;
import java.time.Duration;

/**
 * Utilities for enforcing that an operation take a minimum amount of time.
 */
public final class MinimumTime {

    private MinimumTime() {}

    /**
     * Perform an operation, ensuring that it takes at least a minimum amount of time. If the operation
     * takes less time than the specified amount then sleep until the minimum is met.
     * If the operation takes more time than the specified amount then do not sleep.
     *
     * @param time
     * 		provides wall clock time
     * @param runnable
     * 		the operation to run
     * @param minimumTime
     * 		the minimum amount of time that this function is required to take to complete
     */
    public static void runWithMinimumTime(
            final Time time, final InterruptableRunnable runnable, final Duration minimumTime)
            throws InterruptedException {

        final long start = time.nanoTime();
        runnable.run();
        final long end = time.nanoTime();
        final Duration delta = Duration.ofNanos(end - start);

        final Duration remainingTime = minimumTime.minus(delta);

        if (CompareTo.isGreaterThan(remainingTime, Duration.ZERO)) {
            NANOSECONDS.sleep(remainingTime.toNanos());
        }
    }
}
