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

package com.swirlds.common.wiring;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.NoOpFractionalTimer;
import com.swirlds.common.metrics.extensions.StandardFractionalTimer;
import com.swirlds.common.wiring.counters.ObjectCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * Configures metrics for a {@link Wire}.
 */
public class WireMetricsBuilder {

    private final Metrics metrics;
    private final Time time;
    private boolean unhandledTaskMetricEnabled = false;
    private boolean busyFractionMetricEnabled = false;
    private StandardFractionalTimer busyFractionTimer;

    /**
     * Constructor.
     *
     * @param metrics the metrics object to configure
     * @param time    the time object to use for metrics
     */
    WireMetricsBuilder(@NonNull final Metrics metrics, @NonNull final Time time) {
        this.metrics = Objects.requireNonNull(metrics);
        this.time = Objects.requireNonNull(time);
    }

    /**
     * Set whether the unhandled task count metric should be enabled. Default false.
     *
     * @param enabled true if the unhandled task count metric should be enabled, false otherwise
     * @return this
     */
    @NonNull
    public WireMetricsBuilder withUnhandledTaskMetricEnabled(final boolean enabled) {
        this.unhandledTaskMetricEnabled = enabled;
        return this;
    }

    /**
     * Set whether the busy fraction metric should be enabled. Default false.
     * <p>
     * Note: this metric is currently only compatible with non-concurrent wire implementations. At a future time this
     * metric will be updated to work with concurrent wire implementations.
     *
     * @param enabled true if the busy fraction metric should be enabled, false otherwise
     * @return this
     */
    public WireMetricsBuilder withBusyFractionMetricsEnabled(final boolean enabled) {
        this.busyFractionMetricEnabled = enabled;
        return this;
    }

    /**
     * Check if the scheduled task count metric is enabled.
     *
     * @return true if the scheduled task count metric is enabled, false otherwise
     */
    boolean isUnhandledTaskMetricEnabled() {
        return unhandledTaskMetricEnabled;
    }

    /**
     * Check if the busy fraction metric is enabled.
     *
     * @return true if the busy fraction metric is enabled, false otherwise
     */
    boolean isBusyFractionMetricEnabled() {
        return busyFractionMetricEnabled;
    }

    /**
     * Build a fractional timer (if enabled)
     *
     * @return the fractional timer
     */
    @NonNull
    FractionalTimer buildBusyTimer() {
        if (busyFractionMetricEnabled) {
            busyFractionTimer = new StandardFractionalTimer(time);
            return busyFractionTimer;
        } else {
            return NoOpFractionalTimer.getInstance();
        }
    }

    /**
     * Register all configured metrics.
     *
     * @param unhandledTaskCounter the counter that is used to track the number of scheduled tasks
     */
    void registerMetrics(@NonNull final String wireName, @Nullable final ObjectCounter unhandledTaskCounter) {
        if (unhandledTaskMetricEnabled) {
            Objects.requireNonNull(unhandledTaskCounter);

            final FunctionGauge.Config<Long> config = new FunctionGauge.Config<>(
                            "platform", wireName + "_unhandled_task_count", Long.class, unhandledTaskCounter::getCount)
                    .withDescription(
                            "The number of scheduled tasks that have not been fully handled for the wire " + wireName);
            metrics.getOrCreate(config);
        }

        if (busyFractionMetricEnabled) {
            busyFractionTimer.registerMetric(
                    metrics,
                    "platform",
                    wireName + "_busy_fraction",
                    "Fraction (out of 1.0) of time spent processing tasks for the wire " + wireName);
        }
    }
}
