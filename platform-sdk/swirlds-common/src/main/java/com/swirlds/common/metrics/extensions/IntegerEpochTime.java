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

package com.swirlds.common.metrics.extensions;

import com.swirlds.common.time.Time;
import com.swirlds.common.utility.Units;

/**
 * A wrapper around a {@link Time} instance that provides the lower 31 bits of the epoch, and calculates elapsed time.
 * Useful when storing the epoch in an int.
 */
public class IntegerEpochTime {
    private final Time time;

    /**
     * @param time the clock to use for the epoch
     */
    public IntegerEpochTime(final Time time) {
        this.time = time;
    }

    /**
     * @return the lower 31 bits of the current millisecond epoch according to the clock stored in this instance
     */
    public int getMilliTime() {
        return (int) (time.currentTimeMillis() % Integer.MAX_VALUE);
    }

    /**
     * @return the lower 31 bits of the current microsecond epoch according to the clock stored in this instance
     */
    public int getMicroTime() {
        return (int) ((time.nanoTime() / Units.MICROSECONDS_TO_NANOSECONDS) % Integer.MAX_VALUE);
    }

    /**
     * @param startTime a value previously returned by {@link #getMilliTime()}
     * @return the elapsed time in milliseconds from the start time until now
     */
    public int millisElapsed(final int startTime) {
        return elapsed(startTime, getMilliTime());
    }

    /**
     * @param startTime a value previously returned by {@link #getMicroTime()}
     * @return the elapsed time in microseconds from the start time until now
     */
    public int microsElapsed(final int startTime) {
        return elapsed(startTime, getMicroTime());
    }

    /**
     * @param startTime a value previously returned by {@link #getMilliTime()}
     * @param endTime a value previously returned by {@link #getMilliTime()}
     * @return the elapsed time in from the start time until the end time
     */
    public static int elapsed(final int startTime, final int endTime) {
        if (endTime >= startTime) {
            return endTime - startTime;
        } else {
            // if the lower 31 bits of the epoch has rolled over, the start time will be bigger than current time
            return Integer.MAX_VALUE - startTime + endTime;
        }
    }
}
