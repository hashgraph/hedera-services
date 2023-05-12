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

package com.swirlds.common.utility.throttle;

import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;
import static com.swirlds.common.utility.Units.SECONDS_TO_NANOSECONDS;

import com.swirlds.common.time.Time;
import java.time.Duration;
import java.time.Instant;

/**
 * <p>
 * A simple utility designed to limit the frequency of an event, e.g. making sure a particular log message
 * isn't written too often.
 * </p>
 *
 * <p>
 * This object is not thread safe. This object was designed for simplicity and ease of use. This object
 * may not be suitable for code pathways with extremely high performance requirements.
 * </p>
 */
public class RateLimiter {

    /**
     * The minimum amount of time that must pass before this operation may happen another time.
     */
    private final Duration minimumPeriod;

    /**
     * The time when this rate limiter last allowed an operation to happen.
     */
    private Instant lastOperation = Instant.EPOCH;

    /**
     * The number of requests that have been denied since the last successful operation.
     */
    private long deniedRequests;

    /**
     * Provides a view of the current time.
     */
    private final Time time;

    /**
     * Create a new rate limiter.
     *
     * @param time
     * 		provides the current time
     * @param minimumPeriod
     * 		the minimum time that must pass between operations
     */
    public RateLimiter(final Time time, final Duration minimumPeriod) {
        this.time = time;
        this.minimumPeriod = minimumPeriod;
    }

    /**
     * Create a new rate limiter.
     *
     * @param time
     * 		provides the current time
     * @param maxFrequency
     * 		the maximum frequency of the operation, in hz
     */
    public RateLimiter(final Time time, final double maxFrequency) {
        this(time, Duration.ofNanos((long) (1.0 / maxFrequency * SECONDS_TO_NANOSECONDS)));
    }

    /**
     * Request permission to perform an operation. Returns true if it is ok to perform the operation,
     * returns false if the operation has been performed too recently in the past.
     *
     * @return true if the operation can be performed without violating rate limits, otherwise false
     */
    public boolean request() {
        final Instant now = time.now();
        final Duration elapsed = Duration.between(lastOperation, now);
        if (isGreaterThanOrEqualTo(elapsed, minimumPeriod)) {
            lastOperation = now;
            deniedRequests = 0;
            return true;
        }
        deniedRequests++;
        return false;
    }

    /**
     * Get the number of times {@link #request()} has returned false since the last time it returned true. Immediately
     * after {@link #request()} returns true, this method will always return 0.
     *
     * @return the number of recently denied requests
     */
    public long getDeniedRequests() {
        return deniedRequests;
    }
}
