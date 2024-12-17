/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.model.internal.monitor;

import static com.swirlds.common.utility.CompareTo.isLessThan;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.metrics.api.IntegerGauge;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Encapsulates metrics for the wiring health monitor.
 */
public class HealthMonitorMetrics {

    private static final DurationGauge.Config DURATION_GAUGE_CONFIG = new DurationGauge.Config(
                    "platform", "unhealthyDuration", ChronoUnit.SECONDS)
            .withDescription("The duration that the most unhealthy scheduler has been in an unhealthy state.");
    private final DurationGauge unhealthyDuration;

    private static final IntegerGauge.Config HEALTHY_CONFIG = new IntegerGauge.Config("platform", "healthy")
            .withDescription("1 if the platform is healthy, 0 otherwise. "
                    + "Triggers once unhealthyDuration metric crosses configured threshold.");
    private final IntegerGauge healthy;

    private final Duration healthThreshold;

    /**
     * Constructor.
     *
     * @param platformContext    the platform context
     * @param healthLogThreshold the duration after which the system is considered unhealthy
     */
    public HealthMonitorMetrics(
            @NonNull final PlatformContext platformContext, @NonNull final Duration healthLogThreshold) {
        unhealthyDuration = platformContext.getMetrics().getOrCreate(DURATION_GAUGE_CONFIG);
        healthy = platformContext.getMetrics().getOrCreate(HEALTHY_CONFIG);

        // Always initialize the system as healthy
        healthy.set(1);

        healthThreshold = healthLogThreshold;
    }

    /**
     * Set the unhealthy duration.
     *
     * @param duration the unhealthy duration
     */
    public void reportUnhealthyDuration(@NonNull final Duration duration) {
        unhealthyDuration.set(duration);
        healthy.set(isLessThan(duration, healthThreshold) ? 1 : 0);
    }
}
