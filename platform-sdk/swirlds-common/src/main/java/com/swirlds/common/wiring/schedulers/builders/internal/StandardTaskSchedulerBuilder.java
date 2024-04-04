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
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.NoOpFractionalTimer;
import com.swirlds.common.metrics.extensions.StandardFractionalTimer;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.MultiObjectCounter;
import com.swirlds.common.wiring.counters.NoOpObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.counters.StandardObjectCounter;
import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.schedulers.internal.ConcurrentTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.DirectTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.SequentialTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.SequentialThreadTaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A builder for {@link TaskScheduler}s.
 *
 * @param <OUT> the output type of the primary output wire for this task scheduler (use {@link Void} for a scheduler with
 *            no output)
 */
public class StandardTaskSchedulerBuilder<OUT> extends AbstractTaskSchedulerBuilder<OUT> {

    private static final Logger logger = LogManager.getLogger(StandardTaskSchedulerBuilder.class);

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
     * Build an uncaught exception handler if one was not provided.
     *
     * @return the uncaught exception handler
     */
    @NonNull
    private UncaughtExceptionHandler buildUncaughtExceptionHandler() {
        if (uncaughtExceptionHandler != null) {
            return uncaughtExceptionHandler;
        } else {
            return (thread, throwable) ->
                    logger.error(EXCEPTION.getMarker(), "Uncaught exception in scheduler {}", name, throwable);
        }
    }

    private record Counters(@NonNull ObjectCounter onRamp, @NonNull ObjectCounter offRamp) {}

    /**
     * Combine two counters into one.
     *
     * @param innerCounter the counter needed for internal implementation details, or null if not needed
     * @param outerCounter the counter provided by the outer scope, or null if not provided
     * @return the combined counter, or a no op counter if both are null
     */
    @NonNull
    private static ObjectCounter combineCounters(
            @Nullable final ObjectCounter innerCounter, @Nullable final ObjectCounter outerCounter) {
        if (innerCounter == null) {
            if (outerCounter == null) {
                return NoOpObjectCounter.getInstance();
            } else {
                return outerCounter;
            }
        } else {
            if (outerCounter == null) {
                return innerCounter;
            } else {
                return new MultiObjectCounter(innerCounter, outerCounter);
            }
        }
    }

    /**
     * Figure out which counters to use for this task scheduler (if any), constructing them if they need to be
     * constructed.
     */
    @NonNull
    private Counters buildCounters() {
        final ObjectCounter innerCounter;

        // If we need to enforce a maximum capacity, we have no choice but to use a backpressure object counter.
        //
        // If we don't need to enforce a maximum capacity, we need to use a standard object counter if any
        // of the following conditions are true:
        //  - we have unhandled task metrics enabled
        //  - flushing is enabled. This is because our flush implementation is not
        //    compatible with a no-op counter.
        //
        // In all other cases, better to use a no-op counter. Counters have overhead, and if we don't need one
        // then we shouldn't use one.

        if (unhandledTaskCapacity != UNLIMITED_CAPACITY && type != DIRECT && type != DIRECT_THREADSAFE) {
            innerCounter = new BackpressureObjectCounter(name, unhandledTaskCapacity, sleepDuration);
        } else if (unhandledTaskMetricEnabled || flushingEnabled) {
            innerCounter = new StandardObjectCounter(sleepDuration);
        } else {
            innerCounter = null;
        }

        return new Counters(combineCounters(innerCounter, onRamp), combineCounters(innerCounter, offRamp));
    }

    /**
     * Build a busy timer if enabled.
     *
     * @return the busy timer, or null if not enabled
     */
    @NonNull
    private FractionalTimer buildBusyTimer() {
        if (!busyFractionMetricEnabled) {
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
     */
    private void registerMetrics(
            @Nullable final ObjectCounter unhandledTaskCounter, @NonNull final FractionalTimer busyFractionTimer) {

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
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public TaskScheduler<OUT> build() {
        final Counters counters = buildCounters();
        final FractionalTimer busyFractionTimer = buildBusyTimer();

        registerMetrics(counters.onRamp(), busyFractionTimer);

        final boolean insertionIsBlocking = unhandledTaskCapacity != UNLIMITED_CAPACITY || externalBackPressure;

        final TaskScheduler<OUT> scheduler =
                switch (type) {
                    case CONCURRENT -> new ConcurrentTaskScheduler<>(
                            model,
                            name,
                            pool,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
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
                            flushingEnabled,
                            squelchingEnabled,
                            insertionIsBlocking);
                    case DIRECT -> new DirectTaskScheduler<>(
                            model,
                            name,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
                            squelchingEnabled,
                            busyFractionTimer,
                            false);
                    case DIRECT_THREADSAFE -> new DirectTaskScheduler<>(
                            model,
                            name,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
                            squelchingEnabled,
                            busyFractionTimer,
                            true);
                };

        model.registerScheduler(scheduler, hyperlink);

        return scheduler;
    }
}
