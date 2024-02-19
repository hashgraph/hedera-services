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

package com.swirlds.common.wiring.model.internal;

import static com.swirlds.base.units.UnitConstants.NANOSECONDS_TO_SECONDS;
import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.utility.LongRunningAverage;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A standard implementation of a {@link WiringHealthMonitor}.
 */
public class StandardWiringHealthMonitor implements WiringHealthMonitor {

    /**
     * A scheduler that is being monitored.
     *
     * @param scheduler         the scheduler
     * @param runningAverage    the number of measurements to average when deciding if the scheduler is stressed
     * @param stressedThreshold the capacity threshold at which the scheduler is considered to be stressed
     */
    private record MonitoredScheduler(
            @NonNull TaskScheduler<?> scheduler, @NonNull LongRunningAverage runningAverage, long stressedThreshold) {}

    private final List<MonitoredScheduler> schedulersToMonitor = new ArrayList<>();
    private Instant stressStartTime;
    private final AtomicReference<Duration> stressedDuration = new AtomicReference<>(null);
    private final int runningAverageSize;

    /**
     * Constructor.
     *
     * @param runningAverageSize the number of measurements to average when deciding if the scheduler is stressed
     */
    public StandardWiringHealthMonitor(@NonNull final PlatformContext platformContext, final int runningAverageSize) {
        this.runningAverageSize = runningAverageSize;

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(PLATFORM_CATEGORY, "stressed", Boolean.class, this::isStressed)
                        .withDescription("True if the system is currently stressed")
                        .withUnit("status"));

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(PLATFORM_CATEGORY, "stressedDuration", Double.class, () -> {
                            final Duration stressedDuration = this.stressedDuration.get();
                            if (stressedDuration == null) {
                                return 0.0;
                            }
                            return stressedDuration.toNanos() * NANOSECONDS_TO_SECONDS;
                        })
                        .withDescription("The length of time the system has been stressed")
                        .withUnit("seconds"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerScheduler(@NonNull final TaskScheduler<?> scheduler, final long stressedThreshold) {
        Objects.requireNonNull(scheduler);
        schedulersToMonitor.add(
                new MonitoredScheduler(scheduler, new LongRunningAverage(runningAverageSize), stressedThreshold));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkHealth(@NonNull final Instant now) {
        boolean currentlyStressed = false;
        for (final MonitoredScheduler monitoredScheduler : schedulersToMonitor) {
            if (isSchedulerStressed(monitoredScheduler)) {
                currentlyStressed = true;

                // Continue iterating even if we find a stressed scheduler, so that we
                // can update the running averages for all monitored schedulers.
            }
        }

        if (currentlyStressed) {
            if (stressStartTime == null) {
                stressStartTime = now;
            }
            final Duration duration = Duration.between(stressStartTime, now);
            stressedDuration.set(duration);
        } else {
            stressStartTime = null;
            stressedDuration.set(null);
        }
    }

    /**
     * Poll a scheduler to determine if it is stressed.
     *
     * @param monitoredScheduler the scheduler to poll
     * @return true if the scheduler is stressed
     */
    private boolean isSchedulerStressed(@NonNull final MonitoredScheduler monitoredScheduler) {
        final long size = monitoredScheduler.scheduler.getUnprocessedTaskCount();
        monitoredScheduler.runningAverage.add(size);
        final long averageSize = monitoredScheduler.runningAverage.getAverage();

        // We call a scheduler stressed if the current size exceeds the stress threshold and the average size exceeds
        // the stress threshold. This is intended to avoid reporting stress in the presence of short-lived spikes.
        // Once the current size drops below the stress threshold, we will want to immediately stop reporting stress.

        return (size >= monitoredScheduler.stressedThreshold) && (averageSize >= monitoredScheduler.stressedThreshold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStressed() {
        return stressedDuration.get() != null;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Duration getStressedDuration() {
        return stressedDuration.get();
    }
}
