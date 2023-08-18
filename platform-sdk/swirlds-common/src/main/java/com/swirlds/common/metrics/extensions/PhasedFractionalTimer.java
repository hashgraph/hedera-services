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

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Similar to {@link FractionalTimer}, but for systems that have more than two phases.
 *
 * @param <T> the type of the phase. Note that the toString() is used to generate metric names, and so the toString()
 *            methods must be unique for each phase and should be suitable for use in a metric name.
 */
public class PhasedFractionalTimer<T> { // TODO can I force the types to be an enum?

    private final Map<T, FractionalTimer> phaseTimers = new HashMap<>();
    private T activePhase;

    /**
     * Create a new {@link PhasedFractionalTimer} instance.
     *
     * @param time   provides wall clock time
     * @param phases the set of phases to track
     */
    public PhasedFractionalTimer(
            @NonNull final Time time, @NonNull final Set<T> phases, @NonNull final T initialPhase) {

        Objects.requireNonNull(time);
        Objects.requireNonNull(phases);
        Objects.requireNonNull(initialPhase);

        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Must have at least one phase");
        }
        if (!phases.contains(initialPhase)) {
            throw new IllegalArgumentException("Initial phase must be in the set of phases");
        }

        for (final T phase : phases) {
            phaseTimers.put(phase, new FractionalTimer(time));
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
        phaseTimers.get(activePhase).deactivate();

        final FractionalTimer phaseTimer = phaseTimers.get(phase);
        if (phaseTimer == null) {
            throw new IllegalArgumentException("Unknown phase: " + phase);
        }
        phaseTimer.activate();
        activePhase = phase;
    }

    private static String buildPhaseMetricName(final String name, final Object phase) {
        return name + "_" + phase.toString();
    }

    /**
     * Registers {@link FunctionGauge}s for each phase, tracking the fraction of time that each phase has been active
     * (out of 1.0).
     *
     * @param metrics     the metrics instance to add the metric to
     * @param category    the kind of {@code Metric} (metrics are grouped or filtered by this)
     * @param name        a short name for the {@code Metric}
     * @param description a one-sentence description of the {@code Metric}
     */
    public void registerMetrics(
            @NonNull final Metrics metrics,
            @NonNull final String category,
            @NonNull final String name,
            @NonNull final String description) {

        for (final Map.Entry<T, FractionalTimer> entry : phaseTimers.entrySet()) {
            final T phase = entry.getKey();
            final FractionalTimer timer = entry.getValue();
            timer.registerMetric(
                    metrics, category, buildPhaseMetricName(name, phase), description + " (phase = " + phase + ")");
        }
    }
}
