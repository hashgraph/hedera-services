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

package com.swirlds.common.utility;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A simple stop watch that can be used to measure elapsed time.
 */
public class StopWatch {
    private long startTime;
    private long endTime;
    private boolean running;
    private boolean suspended;

    /**
     * Creates a new StopWatch.
     */
    public StopWatch() {
        this.running = false;
        this.suspended = false;
    }

    /**
     * Starts StopWatch
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("StopWatch is already running.");
        }
        this.startTime = System.nanoTime();
        this.running = true;
    }

    /**
     * Stops StopWatch only after being running
     */
    public void stop() {
        if (!running || suspended) {
            throw new IllegalStateException("StopWatch is not running.");
        }
        this.endTime = System.nanoTime();
        this.running = false;
    }

    /**
     * Suspends StopWatch only after being started
     */
    public void suspend() {
        if (!running || suspended) {
            throw new IllegalStateException("StopWatch is not running or is already suspended.");
        }
        this.endTime = System.nanoTime();
        this.suspended = true;
    }

    /**
     * Resumes StopWatch only after being suspended
     */
    public void resume() {
        if (!suspended) {
            throw new IllegalStateException("StopWatch is not suspended.");
        }
        this.startTime += (System.nanoTime() - endTime); // Adjusting the startTime
        this.suspended = false;
    }

    /**
     * Returns the elapsed time in nanoseconds.
     *
     * @return the elapsed time in nanoseconds
     * @throws IllegalStateException if the StopWatch is still running or has not been started yet
     */
    long getElapsedTimeNano() {
        if (startTime == 0 || endTime == 0) {
            throw new IllegalStateException("StopWatch has not been started yet.");
        }

        if (running) {
            throw new IllegalStateException("StopWatch is still running.");
        }

        return endTime - startTime;
    }

    /**
     * Returns the elapsed time in microseconds.
     *
     * @return the elapsed time in microseconds
     * @throws IllegalStateException if the StopWatch is still running or has not been started yet
     */
    public long getElapsedTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(getElapsedTimeNano());
    }

    /**
     * Returns if StopWatch is running
     *
     * @return true if StopWatch is running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns if StopWatch is suspended
     *
     * @return true if StopWatch is suspended, false otherwise
     */
    public boolean isSuspended() {
        return suspended;
    }

    /**
     * Returns if StopWatch is stopped
     *
     * @return true if StopWatch is stopped, false otherwise
     */
    public boolean isStopped() {
        return !running && !suspended;
    }

    /**
     * Resets StopWatch
     */
    public void reset() {
        this.startTime = 0;
        this.endTime = 0;
        this.running = false;
    }

    /**
     * Returns the elapsed time in the specified time unit.
     *
     * @param unit
     * 		the time unit to return the elapsed time in
     * @return the elapsed time in the specified {@code unit}
     * @throws IllegalStateException if the StopWatch is still running or has not been started yet
     */
    public long getTime(@NonNull final TimeUnit unit) {
        Objects.requireNonNull(unit, "unit is null");

        return switch (unit) {
            case NANOSECONDS -> getElapsedTimeNano();
            case MICROSECONDS -> TimeUnit.NANOSECONDS.toMicros(getElapsedTimeNano());
            case MILLISECONDS -> TimeUnit.NANOSECONDS.toMillis(getElapsedTimeNano());
            case SECONDS -> TimeUnit.NANOSECONDS.toSeconds(getElapsedTimeNano());
            case MINUTES -> TimeUnit.NANOSECONDS.toMinutes(getElapsedTimeNano());
            case HOURS -> TimeUnit.NANOSECONDS.toHours(getElapsedTimeNano());
            case DAYS -> TimeUnit.NANOSECONDS.toDays(getElapsedTimeNano());
        };
    }
}
