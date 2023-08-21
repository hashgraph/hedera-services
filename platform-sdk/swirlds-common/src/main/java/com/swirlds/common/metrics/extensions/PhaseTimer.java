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

    private final Map<T, FractionalTimer> fractionalTimers = new HashMap<>();
    private final Map<T, RunningAverageMetric> absoluteTimeMetrics = new HashMap<>();

    private T activePhase;

    /**
     * The previous time as measured by {@link Time#nanoTime()}.
     */
    private long previousTime = -1;

    private final TimeUnit absoluteTimeUnit;

    // TODO is this complex enough for a builder?

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
                fractionalTimers.put(phase, new FractionalTimer(time));
            }
        }

        absoluteTimeUnit = builder.getAbsoluteTimeUnit();
        if (absoluteTimeMetricsEnabled) {
            // TODO
        }

        registerMetrics(
                builder.getPlatformContext().getMetrics(),
                builder.getMetricsCategory(),
                builder.getMetricsNamePrefix());

        activePhase = builder.getInitialPhase();
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
            // Fractional metrics are enabled
            // TODO pass in now
            fractionalTimers.get(activePhase).deactivate();
            fractionalTimers.get(phase).activate();
        }

        if (absoluteTimeMetricsEnabled && previousTime != -1) {
            // Absolute time metrics are enabled
            final long elapsedNanos = now - previousTime;

            // TODO allow custom unit
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
        return metricsNamePrefix + "_" + phase;
    }

    /**
     * Register metrics for this object.
     *
     * @param metrics           the metrics object
     * @param metricsCategory   the category for metrics created by this object
     * @param metricsNamePrefix the base name of the metric
     */
    private void registerMetrics(
            @NonNull final Metrics metrics,
            @NonNull final String metricsCategory,
            @NonNull final String metricsNamePrefix) {

        final String description = ""; // TODO

        if (fractionalTimers != null) {
            for (final Map.Entry<T, FractionalTimer> entry : fractionalTimers.entrySet()) {
                final T phase = entry.getKey();
                final FractionalTimer timer = entry.getValue();
                timer.registerMetric(
                        metrics,
                        metricsCategory,
                        buildPhaseMetricName(metricsNamePrefix, phase),
                        description + " (phase = " + phase + ")");
            }
        }

        // TODO
    }
}
