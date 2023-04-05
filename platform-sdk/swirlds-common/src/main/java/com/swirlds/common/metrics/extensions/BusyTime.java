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

import com.swirlds.common.metrics.FloatFormats;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.time.IntegerEpochTime;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A metric that measures the fraction of time that a thread is busy. This metric could be used to track the overall busy
 * time of a thread, or the busy time of a specific subtask. The granularity of this metric is in microseconds. A
 * snapshot of this metric must be taken at least once every 34 minutes in order to get accurate data.
 */
public class BusyTime {
    /** passed to the accumulator method to indicate that work has started */
    private static final long WORK_START = 0;
    /** passed to the accumulator method to indicate that work has ended */
    private static final long WORK_END = 1;
    /** the initial value of status when the instance is created */
    private static final int INITIAL_STATUS = -1;
    /** An instance that provides the current time */
    private final IntegerEpochTime time;
    /** Used to atomically update and reset the time and status */
    private final AtomicLong accumulator;

    /**
     * The default constructor, uses the {@link OSTime} instance to get the current time
     */
    public BusyTime() {
        this(OSTime.getInstance());
    }

    /**
     * A constructor where a custom {@link Time} instance could be supplied
     *
     * @param time
     * 		provides the current time
     */
    public BusyTime(@NonNull final Time time) {
        this.time = new IntegerEpochTime(time);
        this.accumulator = new AtomicLong(IntPairUtils.combine(this.time.getMicroTime(), INITIAL_STATUS));
    }

    /**
     * Adds a {@link FunctionGauge} to the given {@link Metrics} instance
     *
     * @param metrics
     * 		the metrics instance to add the metric to
     * @param config
     * 		the configuration for this metric
     */
    public void addMetric(@NonNull final Metrics metrics, @NonNull final DefaultMetricConfig config) {
        metrics.getOrCreate(
                new FunctionGauge.Config<>(config.getCategory(), config.getName(), Double.class, this::getAndReset)
                        .withDescription(config.getDescription())
                        .withUnit("fraction")
                        .withFormat(FloatFormats.FORMAT_1_3));
    }

    /**
     * Notifies the metric that work has started
     */
    public void startingWork() {
        accumulator.accumulateAndGet(WORK_START, this::statusUpdate);
    }

    /**
     * Notifies the metric that work has finished
     */
    public void finishedWork() {
        accumulator.accumulateAndGet(WORK_END, this::statusUpdate);
    }

    /**
     * @return the fraction of time that the thread has been busy, where 0.0 means the thread is not at all busy, and
     * 1.0
     * 		means that the thread is 100% busy
     */
    public double getBusyFraction() {
        final long pair = accumulator.get();
        return busyFraction(IntPairUtils.extractLeft(pair), IntPairUtils.extractRight(pair));
    }

    /**
     * Same as {@link #getBusyFraction()} but also resets the metric
     *
     * @return the fraction of time that the thread has been busy, where 0.0 means the thread is not at all busy, and
     * 1.0
     * 		means that the thread is 100% busy
     */
    public double getAndReset() {
        final long pair = accumulator.getAndUpdate(this::reset);
        return busyFraction(IntPairUtils.extractLeft(pair), IntPairUtils.extractRight(pair));
    }

    /**
     * Gets the fraction of time this thread has been busy since the last reset
     *
     * @param measurementStart
     * 		the micro epoch time when the last reset occurred
     * @param status
     * 		the current status of the thread and the time spent in the opposite status
     * @return the fraction of time that the thread has been busy, where 0.0 means the thread is not at all busy, and
     * 1.0
     * 		means that the thread is 100% busy
     */
    private double busyFraction(final int measurementStart, final int status) {
        final int elapsedTime = time.microsElapsed(measurementStart);
        if (elapsedTime == 0) {
            return 0;
        }
        final int busyTime;
        if (isIdle(status)) {
            busyTime = Math.abs(status) - 1;
        } else {
            busyTime = elapsedTime - (status - 1);
        }
        return ((double) busyTime) / elapsedTime;
    }

    private boolean isIdle(final int status) {
        return status < 0;
    }

    private long statusUpdate(final long previousPair, final long statusChange) {
        // the epoch time when the last reset occurred
        final int measurementStart = IntPairUtils.extractLeft(previousPair);
        // the current thread status is represented by the (+ -) sign. the number represents the time spent in the
        // opposite status. this is that its time spent being busy/idle can be deduced whenever the sample is taken. if
        // the time spend busy is X, and the measurement time is Y, then idle time is Y-X. since zero is neither
        // positive nor negative, the values are offset by 1
        final int currentStatus = IntPairUtils.extractRight(previousPair);
        // the current micro epoch time
        final int currentTime = time.getMicroTime();

        if ((statusChange == WORK_START && !isIdle(currentStatus))
                || (statusChange == WORK_END && isIdle(currentStatus))) {
            // this means that the metric has not been updated correctly, we will not change the value
            return previousPair;
        }
        // the time elapsed since the last reset
        final int elapsedTime = IntegerEpochTime.elapsed(measurementStart, currentTime);
        if (statusChange == WORK_START) {
            // this means the thread was previously idle and now started working
            // the time spent being busy beforehand
            final int busyTime = Math.abs(currentStatus) - 1;
            // the time spent idle is all the elapsed time minus the time spent busy
            final int idleTime = elapsedTime - busyTime;
            return IntPairUtils.combine(measurementStart, idleTime + 1);
        }
        // this means the thread was previously busy and now stopped working
        // the time spent being idle beforehand
        final int idleTime = currentStatus - 1;
        // the time spent busy is all the elapsed time minus the time spent idle
        final int busyTime = elapsedTime - idleTime;
        return IntPairUtils.combine(measurementStart, -busyTime - 1);
    }

    private long reset(final long currentPair) {
        return IntPairUtils.combine(time.getMicroTime(), resetStatus(IntPairUtils.extractRight(currentPair)));
    }

    private int resetStatus(final int status) {
        if (status > 0) {
            return 1;
        }
        return -1;
    }
}
