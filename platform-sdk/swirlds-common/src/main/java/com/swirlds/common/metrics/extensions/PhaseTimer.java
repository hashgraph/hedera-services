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

package com.swirlds.common.metrics.extensions;

import static com.swirlds.common.units.TimeUnit.UNIT_MICROSECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.units.TimeUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tracks performance metrics for systems that operate in phases.
 *
 * @param <T> the type of the phase, must be an enum
 */
public class PhaseTimer<T extends Enum<T>> {

    private final Time time;

    private final boolean fractionMetricsEnabled;
    private final boolean absoluteTimeMetricsEnabled;

    /**
     * Tracks the fraction of the time, out of 1.0, spent in each phase.
     */
    private final Map<T, FractionalTimer> fractionalTimers = new HashMap<>();

    /**
     * Tracks the average time spent in a phase before switching to another phase.
     */
    private final Map<T, RunningAverageMetric> absoluteTimeMetrics = new HashMap<>();

    private T activePhase;

    /**
     * The previous time as measured by {@link Time#nanoTime()}.
     */
    private long previousTime;

    private final TimeUnit absoluteTimeUnit;

    /**
     * Create a new {@link PhaseTimer} instance. Do not call this directly, use {@link PhaseTimerBuilder#build()}.
     *
     * @param builder the builder
     */
    PhaseTimer(@NonNull final PhaseTimerBuilder<T> builder) {
        this.time = builder.getTime();

        fractionMetricsEnabled = builder.areFractionMetricsEnabled();
        absoluteTimeMetricsEnabled = builder.areAbsoluteTimeMetricsEnabled();

        if (fractionMetricsEnabled) {
            for (final T phase : builder.getPhases()) {
                fractionalTimers.put(phase, new StandardFractionalTimer(time));
            }
        }

        absoluteTimeUnit = builder.getAbsoluteUnit();

        registerMetrics(
                builder.getPlatformContext().getMetrics(),
                builder.getMetricsCategory(),
                builder.getMetricsNamePrefix(),
                builder.getPhases());

        activePhase = builder.getInitialPhase();
        previousTime = time.nanoTime();

        if (fractionMetricsEnabled) {
            fractionalTimers.get(activePhase).activate();
        }
    }

    /**
     * Activate a phase. The currently active phase, if different from the provided phase, is deactivated. Has no effect
     * if the requested phase is already active.
     * <p>
     * It is not thread safe to call this method concurrently on different threads.
     *
     * @param phase the phase to activate
     */
    public void activatePhase(@NonNull final T phase) {
        if (phase.equals(activePhase)) {
            // We are still in the same phase, intentional no-op
            return;
        }

        final long now = time.nanoTime();

        if (fractionMetricsEnabled) {
            final long microNow = (long) UNIT_NANOSECONDS.convertTo(now, UNIT_MICROSECONDS);
            fractionalTimers.get(activePhase).deactivate(microNow);
            fractionalTimers.get(phase).activate(microNow);
        }

        if (absoluteTimeMetricsEnabled) {
            final long elapsedNanos = now - previousTime;
            absoluteTimeMetrics.get(activePhase).update(UNIT_NANOSECONDS.convertTo(elapsedNanos, absoluteTimeUnit));
        }

        previousTime = now;
        activePhase = phase;
    }

    /**
     * Build the metric name for the fraction of time spent in a given phase.
     *
     * @param metricsNamePrefix the base name of the metric
     * @param phase             the phase
     * @return the metric name
     */
    private String buildPhaseMetricName(@NonNull final String metricsNamePrefix, @NonNull final T phase) {
        return metricsNamePrefix + "_fraction_" + phase;
    }

    /**
     * Build the metric name for the average absolute time spent in a given phase.
     *
     * @param metricsNamePrefix the base name of the metric
     * @param phase             the phase
     * @return the metric name
     */
    private String buildAbsoluteTimeMetricName(@NonNull final String metricsNamePrefix, @NonNull final T phase) {
        return metricsNamePrefix + "_time_" + phase;
    }

    /**
     * Register metrics for this object.
     *
     * @param metrics           the metrics object
     * @param metricsCategory   the category for metrics created by this object
     * @param metricsNamePrefix the base name of the metric
     * @param phases            the phases
     */
    private void registerMetrics(
            @NonNull final Metrics metrics,
            @NonNull final String metricsCategory,
            @NonNull final String metricsNamePrefix,
            @NonNull final Set<T> phases) {

        if (fractionMetricsEnabled) {
            for (final T phase : phases) {
                final FractionalTimer timer = fractionalTimers.get(phase);
                timer.registerMetric(
                        metrics,
                        metricsCategory,
                        buildPhaseMetricName(metricsNamePrefix, phase),
                        "Fraction (out of 1.0) of time spent in phase " + phase.name());
            }
        }

        if (absoluteTimeMetricsEnabled) {
            for (final T phase : phases) {
                final RunningAverageMetric.Config config = new RunningAverageMetric.Config(
                                metricsCategory, buildAbsoluteTimeMetricName(metricsNamePrefix, phase))
                        .withDescription("Average time spent in phase " + phase.name())
                        .withUnit(absoluteTimeUnit.getName());

                final RunningAverageMetric metric = metrics.getOrCreate(config);
                absoluteTimeMetrics.put(phase, metric);
            }
        }
    }
}
