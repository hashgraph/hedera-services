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

import com.swirlds.common.wiring.internal.AbstractObjectCounter;
import com.swirlds.common.wiring.internal.BackpressureObjectCounter;
import com.swirlds.common.wiring.internal.ConcurrentWire;
import com.swirlds.common.wiring.internal.ObjectCounter;
import com.swirlds.common.wiring.internal.SequentialWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A builder for wires.
 *
 * @param <T> the type of object that is passed through the wire
 */
public class WireBuilder<T> {

    public static final long UNLIMITED_CAPACITY = -1;

    private boolean concurrent = false;
    private final Consumer<T> consumer;
    private final String name;
    private WireMetricsBuilder metricsBuilder;
    private long scheduledTaskCapacity = UNLIMITED_CAPACITY;

    private Duration backpressureSleepDuration = Duration.ofNanos(100);

    // Future parameters:
    //  - uncaught exception handler
    //  - max concurrency (or would this be implemented by limiting the thread pool size?)

    /**
     * Constructor.
     *
     * @param name     the name of the wire. Used for metrics and debugging. Must be unique (not enforced by framework).
     *                 Must only contain alphanumeric characters, underscores, and hyphens (enforced by framework).
     * @param consumer tasks are passed to this consumer
     */
    WireBuilder(@NonNull final String name, @NonNull final Consumer<T> consumer) {
        this.consumer = Objects.requireNonNull(consumer);

        // The reason why wire names have a restricted character set is because downstream consumers of metrics
        // are very fussy about what characters are allowed in metric names.
        if (!name.matches("^[a-zA-Z0-9_-]*$")) {
            throw new IllegalArgumentException(
                    "Wire name must only contain alphanumeric characters, underscores, " + "and hyphens");
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
    public WireBuilder<T> withConcurrency(boolean concurrent) {
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
    public WireBuilder<T> withScheduledTaskCapacity(final long scheduledTaskCapacity) {
        this.scheduledTaskCapacity = scheduledTaskCapacity;
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
    public WireBuilder<T> withBackpressureSleepDuration(@Nullable final Duration backpressureSleepDuration) {
        this.backpressureSleepDuration = backpressureSleepDuration;
        return this;
    }

    /**
     * Provide a builder for metrics. If none is provided then no metrics will be enabled.
     *
     * @param metricsBuilder the metrics builder
     * @return this
     */
    public WireBuilder<T> withMetricsBuilder(@NonNull final WireMetricsBuilder metricsBuilder) {
        this.metricsBuilder = Objects.requireNonNull(metricsBuilder);
        return this;
    }

    /**
     * Build the wire.
     *
     * @return the wire
     */
    @NonNull
    public Wire<T> build() {
        final AbstractObjectCounter scheduledTaskCounter;
        if (scheduledTaskCapacity > UNLIMITED_CAPACITY) {
            scheduledTaskCounter = new BackpressureObjectCounter(scheduledTaskCapacity, backpressureSleepDuration);
        } else if (metricsBuilder != null && metricsBuilder.areScheduledTaskCountMetricEnabled()) {
            scheduledTaskCounter = new ObjectCounter();
        } else {
            scheduledTaskCounter = null;
        }

        if (metricsBuilder != null) {
            metricsBuilder.registerMetrics(name, scheduledTaskCounter);
        }

        if (concurrent) {
            return new ConcurrentWire<>(consumer, scheduledTaskCounter);
        } else {
            return new SequentialWire<>(consumer, scheduledTaskCounter);
        }
    }
}
