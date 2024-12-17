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

import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Encapsulates logging for the wiring health monitor.
 */
public class HealthMonitorLogger {

    private static final Logger logger = LogManager.getLogger(HealthMonitorLogger.class);

    /**
     * The amount of time that must pass before we start logging health information.
     */
    private final Duration healthLogThreshold;

    /**
     * A rate limited logger for each scheduler.
     */
    private final Map<String /* scheduler name */, RateLimitedLogger> schedulerLoggers = new HashMap<>();

    /**
     * Constructor.
     *
     * @param platformContext    the platform context
     * @param schedulers         the task schedulers being monitored
     * @param healthLogThreshold the amount of time that must pass before we start logging health information
     * @param healthLogPeriod    the period at which we log health information
     */
    public HealthMonitorLogger(
            @NonNull final PlatformContext platformContext,
            @NonNull final List<TaskScheduler<?>> schedulers,
            @NonNull final Duration healthLogThreshold,
            @NonNull final Duration healthLogPeriod) {

        this.healthLogThreshold = healthLogThreshold;
        for (final TaskScheduler<?> scheduler : schedulers) {
            final String schedulerName = scheduler.getName();
            final RateLimitedLogger rateLimitedLogger =
                    new RateLimitedLogger(logger, platformContext.getTime(), healthLogPeriod);
            schedulerLoggers.put(schedulerName, rateLimitedLogger);
        }
    }

    /**
     * Report an unhealthy scheduler.
     *
     * @param scheduler         the unhealthy scheduler
     * @param unhealthyDuration the duration for which the scheduler has been unhealthy
     */
    public void reportUnhealthyScheduler(
            @NonNull final TaskScheduler<?> scheduler, @NonNull final Duration unhealthyDuration) {
        if (CompareTo.isLessThan(unhealthyDuration, healthLogThreshold)) {
            // Don't log about small unhealthy durations
            return;
        }

        final RateLimitedLogger rateLimitedLogger = schedulerLoggers.get(scheduler.getName());
        final String formattedDuration =
                UNIT_NANOSECONDS.buildFormatter(unhealthyDuration.toNanos()).render();
        rateLimitedLogger.warn(
                STARTUP.getMarker(),
                "Task scheduler {} has been unhealthy for {}. It currently has {}/{} unhandled tasks.",
                scheduler.getName(),
                formattedDuration,
                scheduler.getUnprocessedTaskCount(),
                scheduler.getCapacity());
    }
}
