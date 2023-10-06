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

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.wiring.internal.AbstractObjectCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * Configures metrics for a {@link Wire}.
 */
public class WireMetricsBuilder {

    private final Metrics metrics;
    private boolean scheduledTaskCountMetricEnabled = false;

    /**
     * Constructor.
     *
     * @param metrics the metrics object to configure
     */
    WireMetricsBuilder(@NonNull final Metrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    /**
     * Set whether the scheduled task count metric should be enabled. Default false.
     *
     * @param enabled true if the scheduled task count metric should be enabled, false otherwise
     * @return this
     */
    @NonNull
    public WireMetricsBuilder withScheduledTaskCountMetricEnabled(final boolean enabled) {
        this.scheduledTaskCountMetricEnabled = enabled;
        return this;
    }

    /**
     * Check if the scheduled task count metric is enabled.
     *
     * @return true if the scheduled task count metric is enabled, false otherwise
     */
    boolean areScheduledTaskCountMetricEnabled() {
        return scheduledTaskCountMetricEnabled;
    }

    /**
     * Register all configured metrics.
     *
     * @param scheduledTaskCounter the counter that is used to track the number of scheduled tasks
     */
    void registerMetrics(@NonNull final String wireName, @Nullable final AbstractObjectCounter scheduledTaskCounter) {
        if (scheduledTaskCountMetricEnabled) {
            Objects.requireNonNull(scheduledTaskCounter);

            final FunctionGauge.Config<Long> config = new FunctionGauge.Config<>(
                    wireName + "_scheduled_task_count",
                    "The number of scheduled tasks for the wire " + wireName,
                    Long.class,
                    scheduledTaskCounter::getCount);
            metrics.getOrCreate(config);
        }
    }
}
