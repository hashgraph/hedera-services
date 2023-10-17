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

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.counters.StandardObjectCounter;
import com.swirlds.common.wiring.internal.ConcurrentWire;
import com.swirlds.common.wiring.internal.SequentialWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * A builder for wires.
 */
public class WireBuilder {

    public static final long UNLIMITED_CAPACITY = -1;

    private boolean concurrent = false;
    private final String name;
    private WireMetricsBuilder metricsBuilder;
    private long scheduledTaskCapacity = UNLIMITED_CAPACITY;
    private ObjectCounter onRamp;
    private ObjectCounter offRamp;
    private ForkJoinPool pool = ForkJoinPool.commonPool();

    private Duration backpressureSleepDuration = Duration.ofNanos(100);

    // Future parameters:
    //  - uncaught exception handler

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
    public WireBuilder withOffRamp(@NonNull final ObjectCounter offRamp) {
        this.offRamp = Objects.requireNonNull(offRamp);
        return this;
    }

    /**
     * If backpressure is enabled via {@link #withScheduledTaskCapacity(long)}, then sleep this length of time whenever
     * backpressure needs to be applied. If null then do not sleep (i.e. busy wait). Default 100 nanoseconds.
     *
     * @param backpressureSleepDuration the length of time to sleep when backpressure needs to be applied
     * @return this
     */
    @NonNull
    public WireBuilder withBackpressureSleepDuration(@Nullable final Duration backpressureSleepDuration) {
        this.backpressureSleepDuration = backpressureSleepDuration;
        return this;
    }

    /**
     * Provide a builder for metrics. If none is provided then no metrics will be enabled.
     *
     * @param metricsBuilder the metrics builder
     * @return this
     */
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
    public WireBuilder withPool(@NonNull final ForkJoinPool pool) {
        this.pool = Objects.requireNonNull(pool);
        return this;
    }

    /**
     * Describes the counters to be used by this wire.
     *
     * @param onRamp  the on ramp counter
     * @param offRamp the off ramp counter
     */
    private record Counters(@Nullable ObjectCounter onRamp, @Nullable ObjectCounter offRamp) {}

    /**
     * Figure out which counters to use for this wire (if any), constructing them if they need to be constructed.
     */
    @NonNull
    private Counters buildCounters() {
        if (scheduledTaskCapacity != UNLIMITED_CAPACITY) {
            if (onRamp != null) {
                throw new IllegalStateException("Cannot specify both an on ramp and a scheduled task capacity");
            }
            if (offRamp != null) {
                throw new IllegalStateException("Cannot specify both an off ramp and a scheduled task capacity");
            }
            final ObjectCounter counter =
                    new BackpressureObjectCounter(scheduledTaskCapacity, backpressureSleepDuration);
            return new Counters(counter, counter);
        }

        if (metricsBuilder != null && metricsBuilder.isScheduledTaskCountMetricEnabled()) {
            if (onRamp != null) {
                throw new IllegalStateException("Cannot specify both an on ramp and a scheduled task metric");
            }
            if (offRamp != null) {
                throw new IllegalStateException("Cannot specify both an off ramp and a scheduled task metric");
            }
            final ObjectCounter counter = new StandardObjectCounter();
            return new Counters(counter, counter);
        }

        return new Counters(onRamp, offRamp);
    }

    /**
     * Build a busy timer if enabled.
     *
     * @return the busy timer, or null if not enabled
     */
    @Nullable
    private FractionalTimer buildBusyTimer() {
        if (concurrent || metricsBuilder == null || !metricsBuilder.isBusyFractionMetricEnabled()) {
            return null;
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
        final Counters counters = buildCounters();
        final FractionalTimer busyTimer = buildBusyTimer();

        if (concurrent) {
            return new ConcurrentWire(pool, name, counters.onRamp(), counters.offRamp());
        } else {
            return new SequentialWire(pool, name, counters.onRamp(), counters.offRamp(), busyTimer);
        }
    }
}
