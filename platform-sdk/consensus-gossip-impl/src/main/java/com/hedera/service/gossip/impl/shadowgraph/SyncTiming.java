/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.service.gossip.impl.shadowgraph;

/**
 * A type to record the points for gossip steps. At the end of a gossip session,
 * the results are reported to the relevant statistics accumulators which quantify
 * gossip performance.
 */
public class SyncTiming {
    /**
     * Number of time points to record = {number of time intervals} + 1.
     */
    private static final int NUM_TIME_POINTS = 6;

    /**
     * JVM time points, in nanoseconds.
     */
    private final long[] timePoints = new long[NUM_TIME_POINTS];

    /**
     * Set the 0th time point.
     */
    public void start() {
        timePoints[0] = now();
    }

    /**
     * Record the ith time point.
     *
     * @param index
     *      the ith time point to record
     */
    public void setTimePoint(final int index) {
        timePoints[index] = now();
    }

    /**
     * Get the ith time point.
     *
     * @param index
     *      the index of the time point
     * @return the ith recorded time point
     */
    public long getTimePoint(final int index) {
        return timePoints[index];
    }

    /**
     * Get the difference between two time points.
     *
     * @param end
     *      the ending point
     * @param start
     *      the starting point
     * @return the difference
     */
    public long getPointDiff(final int end, final int start) {
        return timePoints[end] - timePoints[start];
    }

    /**
     * Get the current system time value in ns.
     *
     * @return current time in ns
     */
    private static long now() {
        return System.nanoTime();
    }
}
