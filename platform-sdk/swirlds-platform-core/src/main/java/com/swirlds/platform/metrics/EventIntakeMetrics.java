/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_1;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_14_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;
import static com.swirlds.common.units.UnitConstants.NANOSECONDS_TO_SECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.StaleEventObserver;
import java.util.Objects;

/**
 * Collection of metrics related to event intake
 */
public class EventIntakeMetrics implements StaleEventObserver {

    private final SpeedometerMetric rescuedEventsPerSecond;

    private final SpeedometerMetric duplicateEventsPerSecond;

    private final RunningAverageMetric avgDuplicatePercent;

    private final SpeedometerMetric timeFracAdd;

    private final RunningAverageMetric shouldCreateEvent;

    private final Counter staleEventsTotal;

    private final SpeedometerMetric staleEventsPerSecond;

    private final Time time;

    /**
     * Constructor of {@code EventIntakeMetrics}
     *
     * @param metricsConfig
     *      configuration for the metrics
     * @param metrics
     * 		a reference to the metrics-system
     * @param time
     * 		provides wall clock time
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public EventIntakeMetrics(final MetricsConfig metricsConfig, final Metrics metrics, final Time time) {
        Objects.requireNonNull(metricsConfig, "metricsConfig required");
        Objects.requireNonNull(metrics, "metrics required");
        this.time = time;

        rescuedEventsPerSecond =
                metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, INTERNAL_CATEGORY, "rescuedEv/sec")
                        .withDescription("number of events per second generated to prevent stale events")
                        .withFormat(FORMAT_16_2));
        duplicateEventsPerSecond =
                metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, INTERNAL_CATEGORY, "dupEv/sec")
                        .withDescription("number of events received per second that are already known")
                        .withFormat(FORMAT_14_2));
        avgDuplicatePercent =
                metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig, PLATFORM_CATEGORY, "dupEv%")
                        .withDescription("percentage of events received that are already known")
                        .withFormat(FORMAT_10_2));
        timeFracAdd = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, INTERNAL_CATEGORY, "timeFracAdd")
                .withDescription(
                        "fraction of each second spent adding an event to the hashgraph and " + "finding consensus")
                .withFormat(FORMAT_9_6));
        shouldCreateEvent = metrics.getOrCreate(
                new RunningAverageMetric.Config(metricsConfig, INTERNAL_CATEGORY, "shouldCreateEvent")
                        .withFormat(FORMAT_10_1));
        staleEventsTotal = metrics.getOrCreate(new Counter.Config(INTERNAL_CATEGORY, "staleEvTot")
                .withDescription("total number of stale events ever"));
        staleEventsPerSecond =
                metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, INTERNAL_CATEGORY, "staleEv/sec")
                        .withDescription("number of stale events per second")
                        .withFormat(FORMAT_16_2));
    }

    /**
     * Update a statistics accumulator whenever this node creates an event with
     * an other-parent that has no children. (The OP is "rescued".)
     */
    public void rescuedEvent() {
        rescuedEventsPerSecond.cycle();
    }

    /**
     * Update a statistics accumulator when a duplicate event has been detected.
     */
    public void duplicateEvent() {
        duplicateEventsPerSecond.cycle();
        // move toward 100%
        avgDuplicatePercent.update(100);
    }

    /**
     * Update a statistics accumulator when a non-duplicate event has been detected
     */
    public void nonDuplicateEvent() {
        // move toward 0%
        avgDuplicatePercent.update(0);
    }

    /**
     * Update event task statistics
     *
     * @param startTime
     * 		a start time, in nanoseconds
     */
    public void processedEventTask(final long startTime) {
        // nanoseconds spent adding to hashgraph
        timeFracAdd.update(((double) time.nanoTime() - startTime) * NANOSECONDS_TO_SECONDS);
    }

    /**
     * Notifies the stats that the event creation phase has entered
     *
     * @param shouldCreateEvent
     * 		did the sync manager tell us to create an event?
     */
    public void eventCreation(final boolean shouldCreateEvent) {
        this.shouldCreateEvent.update(shouldCreateEvent ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void staleEvent(final EventImpl event) {
        staleEventsTotal.increment();
        staleEventsPerSecond.cycle();
    }
}
