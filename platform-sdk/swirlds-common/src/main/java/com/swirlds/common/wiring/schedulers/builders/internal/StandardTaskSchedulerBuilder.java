/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.schedulers.builders.internal;

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT_THREADSAFE;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.NO_OP;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.NoOpFractionalTimer;
import com.swirlds.common.metrics.extensions.StandardFractionalTimer;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.schedulers.internal.ConcurrentTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.DirectTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.NoOpTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.SequentialTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.SequentialThreadTaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * A builder for {@link TaskScheduler}s.
 *
 * @param <OUT> the output type of the primary output wire for this task scheduler (use {@link Void} for a scheduler
 *              with no output)
 */
public class StandardTaskSchedulerBuilder<OUT> extends AbstractTaskSchedulerBuilder<OUT> {

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param model           the wiring model
     * @param name            the name of the task scheduler. Used for metrics and debugging. Must be unique. Must only
     *                        contain alphanumeric characters and underscores.
     * @param defaultPool     the default fork join pool, if none is provided then this pool will be used
     */
    public StandardTaskSchedulerBuilder(
            @NonNull final PlatformContext platformContext,
            @NonNull final StandardWiringModel model,
            @NonNull final String name,
            @NonNull final ForkJoinPool defaultPool) {

        super(platformContext, model, name, defaultPool);
    }

    /**
     * Build a busy timer if enabled.
     *
     * @return the busy timer, or null if not enabled
     */
    @NonNull
    private FractionalTimer buildBusyTimer() {
        if (!busyFractionMetricEnabled || type == NO_OP) {
            return NoOpFractionalTimer.getInstance();
        }
        if (type == TaskSchedulerType.CONCURRENT) {
            throw new IllegalStateException("Busy fraction metric is not compatible with concurrent schedulers");
        }
        return new StandardFractionalTimer(platformContext.getTime());
    }

    /**
     * Register all configured metrics.
     *
     * @param unhandledTaskCounter the counter that is used to track the number of scheduled tasks
     * @param busyFractionTimer    the timer that is used to track the fraction of the time that the underlying thread
     *                             is busy
     */
    private void registerMetrics(
            @Nullable final ObjectCounter unhandledTaskCounter, @NonNull final FractionalTimer busyFractionTimer) {

        if (type == NO_OP) {
            return;
        }

        if (unhandledTaskMetricEnabled) {
            Objects.requireNonNull(unhandledTaskCounter);

            final FunctionGauge.Config<Long> config = new FunctionGauge.Config<>(
                            "platform", name + "_unhandled_task_count", Long.class, unhandledTaskCounter::getCount)
                    .withDescription(
                            "The number of scheduled tasks that have not been fully handled for the scheduler " + name);
            platformContext.getMetrics().getOrCreate(config);
        }

        if (busyFractionMetricEnabled) {
            busyFractionTimer.registerMetric(
                    platformContext.getMetrics(),
                    "platform",
                    name + "_busy_fraction",
                    "Fraction (out of 1.0) of time spent processing tasks for the task scheduler " + name);
        }
    }

    /**
     * Ensures that direct schedulers do not have an unhandled task capacity set.
     *
     * <p>If the scheduler type is {@link TaskSchedulerType#DIRECT} or {@link TaskSchedulerType#DIRECT_THREADSAFE}
     * and the unhandled task capacity is not 1, an {@link IllegalArgumentException} is thrown.
     *
     * @throws IllegalArgumentException if the type is direct or direct threadsafe and the unhandled task capacity is not 1
     */
    private void validateConfiguration() {
        if ((type == DIRECT || type == DIRECT_THREADSAFE) && unhandledTaskCapacity != 1) {
            throw new IllegalArgumentException("Direct schedulers cannot have an unhandled task capacity.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public TaskScheduler<OUT> build() {
        // Check to ensure unhandled task capacity is not set for direct schedulers
        validateConfiguration();
        final Counters counters = buildCounters();
        final FractionalTimer busyFractionTimer = buildBusyTimer();

        registerMetrics(counters.onRamp(), busyFractionTimer);

        final boolean insertionIsBlocking =
                ((unhandledTaskCapacity != UNLIMITED_CAPACITY) || externalBackPressure) && (type != NO_OP);

        final TaskScheduler<OUT> scheduler =
                switch (type) {
                    case CONCURRENT -> new ConcurrentTaskScheduler<>(
                            model,
                            name,
                            pool,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
                            unhandledTaskCapacity,
                            flushingEnabled,
                            squelchingEnabled,
                            insertionIsBlocking);
                    case SEQUENTIAL -> new SequentialTaskScheduler<>(
                            model,
                            name,
                            pool,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
                            busyFractionTimer,
                            unhandledTaskCapacity,
                            flushingEnabled,
                            squelchingEnabled,
                            insertionIsBlocking);
                    case SEQUENTIAL_THREAD -> new SequentialThreadTaskScheduler<>(
                            model,
                            name,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
                            busyFractionTimer,
                            sleepDuration,
                            unhandledTaskCapacity,
                            flushingEnabled,
                            squelchingEnabled,
                            insertionIsBlocking);
                    case DIRECT, DIRECT_THREADSAFE -> new DirectTaskScheduler<>(
                            model,
                            name,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
                            squelchingEnabled,
                            busyFractionTimer,
                            type == DIRECT_THREADSAFE);
                    case NO_OP -> new NoOpTaskScheduler<>(model, name, type, flushingEnabled, squelchingEnabled);
                };

        if (type != NO_OP) {
            model.registerScheduler(scheduler, hyperlink);
        }

        return scheduler;
    }
}
