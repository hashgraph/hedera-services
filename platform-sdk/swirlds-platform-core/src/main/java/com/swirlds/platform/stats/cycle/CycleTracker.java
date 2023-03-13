/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.time.Time;
import java.util.Arrays;

/**
 * Tracks the time points of a thread cycle and passes on the information to the metrics
 */
public class CycleTracker {
    private final CycleMetrics metrics;
    /** provides the current time in nanoseconds */
    private final Time time;
    /** duration of each interval, in nanoseconds */
    private final long[] duration;

    private long lastTime;
    private int intervalStarted;

    public CycleTracker(final Time time, final CycleMetrics metrics) {
        this.metrics = metrics;
        this.time = time;
        this.duration = new long[metrics.getNumIntervals()];
        this.lastTime = now();
    }

    /**
     * Called when a cycle starts
     */
    public void startCycle() {
        Arrays.fill(duration, 0);
        final long now = now();
        metrics.idleTime(now - lastTime);
        lastTime = now;
        intervalStarted = 0;
    }

    /**
     * Mark the end of the interval specified
     *
     * @param i
     * 		the index of the interval that ended
     */
    public void intervalEnded(final int i) {
        if (i < 0) {
            throw new IllegalArgumentException(
                    "Time point must be greater than 0. Use startCycle() to mark the beginning of the cycle.");
        } else if (i >= metrics.getNumIntervals()) {
            throw new IllegalArgumentException(String.format(
                    "Time point must be less than %s. Use stopCycle() to mark the end of the cycle.",
                    metrics.getNumIntervals() - 1));
        }

        final long now = now();
        duration[i] = now - lastTime;
        lastTime = now;
        intervalStarted = i + 1;
        metrics.intervalFinished(i, duration[i]);
    }

    /**
     * Capture the end of the cycle time and update all stats for the time periods during the cycle.
     */
    public void cycleEnded() {
        if (intervalStarted < metrics.getNumIntervals()) {
            intervalEnded(intervalStarted);
        }
        metrics.cycleFinished(duration);
    }

    /**
     * Get the current system time value in ns
     *
     * @return current time in ns
     */
    private long now() {
        return time.nanoTime();
    }
}
