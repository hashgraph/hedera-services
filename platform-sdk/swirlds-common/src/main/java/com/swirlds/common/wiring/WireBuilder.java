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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.NoOpFractionalTimer;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.NoOpObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.counters.StandardObjectCounter;
import com.swirlds.common.wiring.internal.ConcurrentWire;
import com.swirlds.common.wiring.internal.SequentialWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A builder for wires.
 */
public class WireBuilder {

    private static final Logger logger = LogManager.getLogger(WireBuilder.class);

    public static final long UNLIMITED_CAPACITY = -1;

    private boolean concurrent = false;
    private final String name;
    private WireMetricsBuilder metricsBuilder;
    private long scheduledTaskCapacity = UNLIMITED_CAPACITY;
    private boolean flushingEnabled = false;
    private ObjectCounter onRamp;
    private ObjectCounter offRamp;
    private ForkJoinPool pool = ForkJoinPool.commonPool();
    private UncaughtExceptionHandler uncaughtExceptionHandler;

    private Duration sleepDuration = Duration.ofNanos(100);

    /**
     * Constructor.
     *
     * @param name the name of the wire. Used for metrics and debugging. Must be unique (not enforced by framework).
     *             Must only contain alphanumeric characters and underscores (enforced by framework).
     */
    WireBuilder(@NonNull final String name) {
        // The reason why wire names have a restricted character set is because downstream consumers of metrics
        // are very fussy about what characters are allowed in metric names.
        if (!name.matches("^[a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(
                    "Wire name must only contain alphanumeric characters, underscores, and hyphens");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Wire name must not be empty");
        }
        this.name = name;
    }

    /**
     * Set whether the wire should be concurrent or not. Default false.
     *
     * @param concurrent true if the wire should be concurrent, false otherwise
     * @return this
     */
    @NonNull
    public WireBuilder withConcurrency(boolean concurrent) {
        this.concurrent = concurrent;
        return this;
    }

    /**
     * Set the maximum number of permitted scheduled tasks in the wire. Default is unlimited.
     *
     * @param scheduledTaskCapacity the maximum number of permitted scheduled tasks in the wire
     * @return this
     */
    @NonNull
    public WireBuilder withScheduledTaskCapacity(final long scheduledTaskCapacity) {
        this.scheduledTaskCapacity = scheduledTaskCapacity;
        return this;
    }

    /**
     * Set whether the wire should enable flushing. Default false. Flushing a wire with this disabled will cause the
     * wire to throw an exception.
     *
     * @param requireFlushCapability true if the wire should require flush capability, false otherwise
     * @return this
     */
    @NonNull
    public WireBuilder withFlushingEnabled(final boolean requireFlushCapability) {
        this.flushingEnabled = requireFlushCapability;
        return this;
    }

    /**
     * Specify an object counter that should be notified when data is added to the wire. This is useful for implementing
     * backpressure that spans multiple wires.
     * <p>
     * Note that specifying an on ramp is incompatible with specifying a scheduled task capacity via
     * {@link #withScheduledTaskCapacity(long)} and incompatible with enabling the scheduled task metric via
     * {@link WireMetricsBuilder#withScheduledTaskCountMetricEnabled(boolean)}. Both of these configurations set up an
     * object counter that is used for both on-ramping and off-ramping data on this wire. This is not compatible with
     * providing a custom on-ramp from an external source.
     *
     * @param onRamp the object counter that should be notified when data is added to the wire
     * @return this
     */
    @NonNull
    public WireBuilder withOnRamp(@NonNull final ObjectCounter onRamp) {
        this.onRamp = Objects.requireNonNull(onRamp);
        return this;
    }

    /**
     * Specify an object counter that should be notified when data is removed from the wire. This is useful for
     * implementing backpressure that spans multiple wires.
     * <p>
     * Note that specifying an off ramp is incompatible with specifying a scheduled task capacity via
     * {@link #withScheduledTaskCapacity(long)} and incompatible with enabling the scheduled task metric via
     * {@link WireMetricsBuilder#withScheduledTaskCountMetricEnabled(boolean)}. Both of these configurations set up an
     * object counter that is used for both on-ramping and off-ramping data on this wire. This is not compatible with
     * providing a custom off ramp to an external source.
     *
     * @param offRamp the object counter that should be notified when data is removed from the wire
     * @return this
     */
    @NonNull
    public WireBuilder withOffRamp(@NonNull final ObjectCounter offRamp) {
        this.offRamp = Objects.requireNonNull(offRamp);
        return this;
    }

    /**
     * If a method needs to block, this is the amount of time that is slept while waiting for the needed condition.
     *
     * @param backpressureSleepDuration the length of time to sleep when backpressure needs to be applied
     * @return this
     */
    @NonNull
    public WireBuilder withSleepDuration(@NonNull final Duration backpressureSleepDuration) {
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
    public WireBuilder withMetricsBuilder(@NonNull final WireMetricsBuilder metricsBuilder) {
        this.metricsBuilder = Objects.requireNonNull(metricsBuilder);
        return this;
    }

    /**
     * Provide a custom thread pool for this wire. If none is provided then the common fork join pool will be used.
     *
     * @param pool the thread pool
     * @return this
     */
    @NonNull
    public WireBuilder withPool(@NonNull final ForkJoinPool pool) {
        this.pool = Objects.requireNonNull(pool);
        return this;
    }

    /**
     * Provide a custom uncaught exception handler for this wire. If none is provided then the default uncaught
     * exception handler will be used. The default handler will write a message to the log.
     *
     * @param uncaughtExceptionHandler the uncaught exception handler
     * @return this
     */
    @NonNull
    public WireBuilder withUncaughtExceptionHandler(@NonNull final UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
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
                    logger.error(EXCEPTION.getMarker(), "Uncaught exception in wire {}", name, throwable);
        }
    }

    /**
     * Figure out which counters to use for this wire (if any), constructing them if they need to be constructed.
     */
    private void buildCounters() {
        if (scheduledTaskCapacity != UNLIMITED_CAPACITY) {
            if (onRamp != null) {
                throw new IllegalStateException("Cannot specify both an on ramp and a scheduled task capacity");
            }
            if (offRamp != null) {
                throw new IllegalStateException("Cannot specify both an off ramp and a scheduled task capacity");
            }
            final ObjectCounter counter = new BackpressureObjectCounter(scheduledTaskCapacity, sleepDuration);
            this.onRamp = counter;
            this.offRamp = counter;
        }

        if (metricsBuilder != null && metricsBuilder.isScheduledTaskCountMetricEnabled()) {
            if (onRamp != null) {
                throw new IllegalStateException("Cannot specify both an on ramp and a scheduled task metric");
            }
            if (offRamp != null) {
                throw new IllegalStateException("Cannot specify both an off ramp and a scheduled task metric");
            }
            final ObjectCounter counter = new StandardObjectCounter(sleepDuration);
            this.onRamp = counter;
            this.offRamp = counter;
        }

        if (onRamp == null) {
            onRamp = NoOpObjectCounter.getInstance();
        }
        if (offRamp == null) {
            offRamp = NoOpObjectCounter.getInstance();
        }
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
        } else {
            return metricsBuilder.buildBusyTimer();
        }
    }

    /**
     * Build the wire.
     *
     * @return the wire
     */
    @NonNull
    public Wire build() {
        buildCounters();
        if (concurrent) {
            // TODO enable flushing on concurrent wire
            return new ConcurrentWire(name, pool, buildUncaughtExceptionHandler(), onRamp, offRamp);
        } else {
            return new SequentialWire(
                    name, pool, buildUncaughtExceptionHandler(), onRamp, offRamp, buildBusyTimer(), flushingEnabled);
        }
    }
}
