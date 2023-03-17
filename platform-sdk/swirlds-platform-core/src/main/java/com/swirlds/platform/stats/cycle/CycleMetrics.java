/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.stats.cycle;

/**
 * Metrics that track a thread cycle
 */
public interface CycleMetrics {
    /**
     * Called when an interval has ended and the next interval begins.
     *
     * @param interval
     * 		the index of the interval that ended
     * @param durationNanos
     * 		the number of nanoseconds the interval took to complete
     */
    void intervalFinished(int interval, long durationNanos);

    /**
     * Called when a single cycle has ended
     *
     * @param intervalNanoTime
     * 		the time in nanoseconds spent on each interval
     */
    void cycleFinished(long[] intervalNanoTime);

    /**
     * Called when a thread stops being idle
     *
     * @param nanoTime
     * 		the time in nanoseconds spent idle
     */
    void idleTime(long nanoTime);

    /**
     * @return the number of intervals of the cycle
     */
    int getNumIntervals();
}
