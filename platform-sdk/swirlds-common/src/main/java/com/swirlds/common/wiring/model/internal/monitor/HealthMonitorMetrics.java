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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.DurationGauge;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Encapsulates metrics for the wiring health monitor.
 */
public class HealthMonitorMetrics {

    private static final DurationGauge.Config DURATION_GAUGE_CONFIG = new DurationGauge.Config(
                    "platform", "unhealthyDuration", ChronoUnit.SECONDS)
            .withUnit("s")
            .withDescription("The duration that the most unhealthy scheduler has been in an unhealthy state.");
    private final DurationGauge unhealthyDuration;

    // TODO metric for number of unhealthy schedulers? Should use minimum threshold maybe

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    public HealthMonitorMetrics(@NonNull final PlatformContext platformContext) {
        unhealthyDuration = platformContext.getMetrics().getOrCreate(DURATION_GAUGE_CONFIG);
    }

    /**
     * Set the unhealthy duration.
     *
     * @param duration the unhealthy duration
     */
    public void reportUnhealthyDuration(@NonNull final Duration duration) {
        unhealthyDuration.set(duration);
    }
}
