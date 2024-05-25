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

import static com.swirlds.common.utility.CompareTo.isGreaterThan;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Monitors the health of a wiring model. A healthy wiring model is a model without too much work backed up in queues.
 * An unhealthy wiring model is a model with at least one queue that is backed up with too much work.
 */
public class HealthMonitor {

    /**
     * A list of task schedulers without unlimited capacities.
     */
    private final List<TaskScheduler<?>> schedulers;

    /**
     * Corresponds to the schedulers list. The instant at the index of a scheduler indicates the last timestamp at which
     * that scheduler was observed to be in a healthy state.
     */
    private final List<Instant> lastHealthyTimes = new ArrayList<>();

    /**
     * The previous value returned by {@link #checkSystemHealth(Instant)}. Used to avoid sending repeat output.
     */
    private Duration previouslyReportedDuration = Duration.ZERO;

    /**
     * Metrics for the health monitor.
     */
    private final HealthMonitorMetrics metrics;

    /**
     * Logs health issues.
     */
    private final HealthMonitorLogger logger;

    // TODO: create a special configuration for wiring framework stuff that is isolated from the schedulers config

    /**
     * Constructor.
     *
     * @param platformContext    the platform context
     * @param schedulers         the task schedulers to monitor
     */
    public HealthMonitor(
            @NonNull final PlatformContext platformContext, @NonNull final List<TaskScheduler<?>> schedulers) {

        metrics = new HealthMonitorMetrics(platformContext);

        this.schedulers = new ArrayList<>();
        for (final TaskScheduler<?> scheduler : schedulers) {
            if (scheduler.getCapacity() != TaskSchedulerBuilder.UNLIMITED_CAPACITY) {
                this.schedulers.add(Objects.requireNonNull(scheduler));
                lastHealthyTimes.add(platformContext.getTime().now());
            }
        }

        logger = new HealthMonitorLogger(platformContext, this.schedulers);
    }

    /**
     * Called periodically. Scans the task schedulers for health issues.
     *
     * @param now the current time
     * @return the amount of time any single scheduler has been concurrently unhealthy. Returns {@link Duration#ZERO} if
     * all schedulers are healthy, returns null if there is no change in health status.
     */
    @Nullable
    public Duration checkSystemHealth(@NonNull final Instant now) {
        Duration longestUnhealthyDuration = Duration.ZERO;

        for (int i = 0; i < lastHealthyTimes.size(); i++) {
            final TaskScheduler<?> scheduler = schedulers.get(i);
            final boolean healthy = scheduler.getUnprocessedTaskCount() <= scheduler.getCapacity();
            if (healthy) {
                lastHealthyTimes.set(i, now);
            } else {
                final Duration unhealthyDuration = Duration.between(lastHealthyTimes.get(i), now);
                logger.reportUnhealthyScheduler(scheduler, unhealthyDuration);
                if (isGreaterThan(unhealthyDuration, longestUnhealthyDuration)) {
                    longestUnhealthyDuration = unhealthyDuration;
                }
            }
        }

        try {
            // Only report when there is a change in health status
            metrics.reportUnhealthyDuration(longestUnhealthyDuration);
            return longestUnhealthyDuration.equals(previouslyReportedDuration) ? null : longestUnhealthyDuration;
        } finally {
            previouslyReportedDuration = longestUnhealthyDuration;
        }
    }
}
