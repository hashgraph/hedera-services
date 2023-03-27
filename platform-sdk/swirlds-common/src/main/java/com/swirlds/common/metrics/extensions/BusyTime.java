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
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;

public class BusyTime {
    public static final int WORK_START = 0;
    public static final int WORK_END = 1;
    /** An instance that provides the current time */
    private final IntegerEpochTime time;

    /** Used to atomically update and reset the time and status */
    private final IntegerPairAccumulator<Double> accumulator;

    /**
     * The default constructor, uses the {@link OSTime}
     *
     * @param config
     * 		the configuration for this metric
     */
    public BusyTime(final Metrics metrics, final DefaultMetricConfig config) {
        this(metrics, config, OSTime.getInstance());
    }

    /**
     * A constructor where a custom {@link Time} instance could be supplied
     *
     * @param config
     * 		the configuration for this metric
     * @param time
     * 		provides the current time
     */
    public BusyTime(final Metrics metrics, final DefaultMetricConfig config, final Time time) {
        this.time = new IntegerEpochTime(time);
        this.accumulator = metrics.getOrCreate(new IntegerPairAccumulator.Config<>(
                        config.getCategory(), config.getName(), Double.class, this::busyFraction)
                .withDescription(config.getDescription())
                .withUnit("fraction")
                .withFormat(FloatFormats.FORMAT_1_3)
                .withCombinedAccumulator(this::statusUpdate)
                .withLeftInitializer(this.time::getMicroTime)
                .withRightReset(this::resetStatus));
    }

    public void startingWork() {
        accumulator.update(time.getMicroTime(), WORK_START);
    }

    public void finishedWork() {
        accumulator.update(time.getMicroTime(), WORK_END);
    }

    public double getBusyFraction() {
        return accumulator.get();
    }

    public IntegerPairAccumulator<Double> getAccumulator() {
        return accumulator;
    }

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

    private long statusUpdate(final long previousPair, final long suppliedValues) {
        final int measurementStart = IntPairUtils.extractLeft(previousPair);
        final int currentStatus = IntPairUtils.extractRight(previousPair);
        final int currentTime = IntPairUtils.extractLeft(suppliedValues);
        final int statusChange = IntPairUtils.extractRight(suppliedValues);

        if ((statusChange == WORK_START && !isIdle(currentStatus))
                || (statusChange == WORK_END && isIdle(currentStatus))) {
            // this means that the metric has not been updated correctly, we will not change the value
            return previousPair;
        }
        final int elapsedTime = IntegerEpochTime.elapsed(measurementStart, currentTime);
        if (statusChange == WORK_START) {
            final int busyTime = Math.abs(currentStatus) - 1;
            final int idleTime = elapsedTime - busyTime;
            return IntPairUtils.combine(measurementStart, idleTime + 1);
        }
        final int idleTime = currentStatus - 1;
        final int busyTime = elapsedTime - idleTime;
        return IntPairUtils.combine(measurementStart, -busyTime - 1);
    }

    private int resetStatus(final int status) {
        if (status > 0) {
            return 1;
        }
        return -1;
    }
}
