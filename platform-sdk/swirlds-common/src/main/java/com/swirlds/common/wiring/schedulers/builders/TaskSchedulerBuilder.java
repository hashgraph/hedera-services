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

package com.swirlds.common.wiring.schedulers.builders;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.NoOpFractionalTimer;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.MultiObjectCounter;
import com.swirlds.common.wiring.counters.NoOpObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.counters.StandardObjectCounter;
import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.ConcurrentTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.DirectTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.SequentialTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.SequentialThreadTaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A builder for {@link TaskScheduler}s.
 *
 * @param <O> the output type of the primary output wire for this task scheduler (use {@link Void} for a scheduler with
 *            no output)
 */
public class TaskSchedulerBuilder<O> {

    private static final Logger logger = LogManager.getLogger(TaskSchedulerBuilder.class);

    public static final long UNLIMITED_CAPACITY = -1;

    private final StandardWiringModel model;

    private TaskSchedulerType type = TaskSchedulerType.SEQUENTIAL;
    private final String name;
    private TaskSchedulerMetricsBuilder metricsBuilder;
    private long unhandledTaskCapacity = UNLIMITED_CAPACITY;
    private boolean flushingEnabled = false;
    private boolean squelchingEnabled = false;
    private boolean externalBackPressure = false;
    private ObjectCounter onRamp;
    private ObjectCounter offRamp;
    private ForkJoinPool pool;
    private UncaughtExceptionHandler uncaughtExceptionHandler;
    private String hyperlink;

    private Duration sleepDuration = Duration.ofNanos(100);

    /**
     * Constructor.
     *
     * @param model       the wiring model
     * @param name        the name of the task scheduler. Used for metrics and debugging. Must be unique. Must only
     *                    contain alphanumeric characters and underscores.
     * @param defaultPool the default fork join pool, if none is provided then this pool will be used
     */
    public TaskSchedulerBuilder(
            @NonNull final StandardWiringModel model,
            @NonNull final String name,
            @NonNull final ForkJoinPool defaultPool) {
        this.model = Objects.requireNonNull(model);

        // The reason why wire names have a restricted character set is because downstream consumers of metrics
        // are very fussy about what characters are allowed in metric names.
        if (!name.matches("^[a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Illegal name: \"" + name
                    + "\". Task Schedulers name must only contain alphanumeric characters and underscores");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("TaskScheduler name must not be empty");
        }
        this.name = name;
        this.pool = Objects.requireNonNull(defaultPool);
    }

    /**
     * Set the type of task scheduler to build. Alters the semantics of the scheduler (i.e. this is not just an internal
     * implementation detail).
     *
     * @param type the type of task scheduler to build
     * @return this
     */
    public TaskSchedulerBuilder<O> withType(@NonNull final TaskSchedulerType type) {
        this.type = Objects.requireNonNull(type);
        return this;
    }

    /**
     * Set the maximum number of permitted scheduled tasks. Default is unlimited.
     *
     * @param unhandledTaskCapacity the maximum number of permitted unhandled tasks
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withUnhandledTaskCapacity(final long unhandledTaskCapacity) {
        this.unhandledTaskCapacity = unhandledTaskCapacity;
        return this;
    }

    /**
     * Set whether the task scheduler should enable flushing. Default false. Flushing a scheduler with this disabled
     * will cause the scheduler to throw an exception. Enabling flushing may add overhead.
     *
     * @param requireFlushCapability true if the wire should require flush capability, false otherwise
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withFlushingEnabled(final boolean requireFlushCapability) {
        this.flushingEnabled = requireFlushCapability;
        return this;
    }

    /**
     * Set whether the task scheduler should enable squelching. Default false. Enabling squelching may add overhead.
     * <p>
     * IMPORTANT: you must not enable squelching if the scheduler handles tasks that require special cleanup. If
     * squelching is enabled and activated, all existing and incoming tasks will be unceremoniously dumped into the
     * void!
     *
     * @param squelchingEnabled true if the scheduler should enable squelching, false otherwise.
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withSquelchingEnabled(final boolean squelchingEnabled) {
        this.squelchingEnabled = squelchingEnabled;
        return this;
    }

    /**
     * Specify an object counter that should be notified when data is added to the task scheduler. This is useful for
     * implementing backpressure that spans multiple schedulers.
     *
     * @param onRamp the object counter that should be notified when data is added to the task scheduler
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withOnRamp(@NonNull final ObjectCounter onRamp) {
        this.onRamp = Objects.requireNonNull(onRamp);
        return this;
    }

    /**
     * Specify an object counter that should be notified when data is removed from the task scheduler. This is useful
     * for implementing backpressure that spans multiple schedulers.
     *
     * @param offRamp the object counter that should be notified when data is removed from the wire
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withOffRamp(@NonNull final ObjectCounter offRamp) {
        this.offRamp = Objects.requireNonNull(offRamp);
        return this;
    }

    /**
     * If true then the framework will assume that back pressure is being applied via external mechanisms.
     * <p>
     * This method does not change the runtime behavior of the wiring framework. But it does affect cyclical back
     * pressure detection and automatically generated wiring diagrams.
     *
     * @param externalBackPressure true if back pressure is being applied externally, false otherwise
     * @return this
     */
    public TaskSchedulerBuilder<O> withExternalBackPressure(final boolean externalBackPressure) {
        this.externalBackPressure = externalBackPressure;
        return this;
    }

    /**
     * If a method needs to block, this is the amount of time that is slept while waiting for the needed condition.
     *
     * @param backpressureSleepDuration the length of time to sleep when backpressure needs to be applied
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withSleepDuration(@NonNull final Duration backpressureSleepDuration) {
        if (backpressureSleepDuration.isNegative()) {
            throw new IllegalArgumentException("Backpressure sleep duration must not be negative");
        }
        this.sleepDuration = backpressureSleepDuration;
        return this;
    }

    /**
     * Provide a builder for metrics. If none is provided then no metrics will be enabled.
     *
     * @param metricsBuilder the metrics builder
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withMetricsBuilder(@NonNull final TaskSchedulerMetricsBuilder metricsBuilder) {
        this.metricsBuilder = Objects.requireNonNull(metricsBuilder);
        return this;
    }

    /**
     * Provide a custom thread pool for this task scheduler. If none is provided then the common fork join pool will be
     * used.
     *
     * @param pool the thread pool
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withPool(@NonNull final ForkJoinPool pool) {
        this.pool = Objects.requireNonNull(pool);
        return this;
    }

    /**
     * Provide a custom uncaught exception handler for this task scheduler. If none is provided then the default
     * uncaught exception handler will be used. The default handler will write a message to the log.
     *
     * @param uncaughtExceptionHandler the uncaught exception handler
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withUncaughtExceptionHandler(
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        return this;
    }

    /**
     * Provide a hyperlink to documentation for this task scheduler. If none is provided then no hyperlink will be
     * generated. Used only for the automatically generated wiring diagram.
     * @param hyperlink the hyperlink to the documentation for this task scheduler
     * @return this
     */
    @NonNull
    public TaskSchedulerBuilder<O> withHyperlink(@Nullable final String hyperlink) {
        this.hyperlink = hyperlink;
        return this;
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
        //  - the scheduler is concurrent and flushing is enabled. This is because the concurrent scheduler's
        //    flush implementation requires a counter that is not a no-op counter.
        //
        // In all other cases, better to use a no-op counter. Counters have overhead, and if we don't need one
        // then we shouldn't use one.

        if (unhandledTaskCapacity != UNLIMITED_CAPACITY) {
            innerCounter = new BackpressureObjectCounter(name, unhandledTaskCapacity, sleepDuration);
        } else if ((metricsBuilder != null && metricsBuilder.isUnhandledTaskMetricEnabled())
                || (type == TaskSchedulerType.CONCURRENT && flushingEnabled)) {
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
        if (metricsBuilder == null || !metricsBuilder.isBusyFractionMetricEnabled()) {
            return NoOpFractionalTimer.getInstance();
        }
        if (type == TaskSchedulerType.CONCURRENT) {
            throw new IllegalStateException("Busy fraction metric is not compatible with concurrent schedulers");
        }
        return metricsBuilder.buildBusyTimer();
    }

    /**
     * Build the task scheduler.
     *
     * @return the task scheduler
     */
    @NonNull
    public TaskScheduler<O> build() {
        final Counters counters = buildCounters();
        final FractionalTimer busyFractionTimer = buildBusyTimer();

        if (metricsBuilder != null) {
            metricsBuilder.registerMetrics(name, counters.onRamp());
        }

        final boolean insertionIsBlocking = unhandledTaskCapacity != UNLIMITED_CAPACITY || externalBackPressure;

        final TaskScheduler<O> scheduler =
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
