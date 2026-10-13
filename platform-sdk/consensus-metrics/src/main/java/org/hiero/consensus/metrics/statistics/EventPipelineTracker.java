// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.metrics.statistics;

import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;
import static java.util.Objects.requireNonNull;

import com.swirlds.base.time.Time;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hiero.consensus.metrics.extensions.LatencyGauges;
import org.hiero.consensus.metrics.extensions.LatencyGauges.Config;
import org.hiero.consensus.model.event.EventOrigin;
import org.hiero.consensus.model.event.PlatformEvent;

/**
 * Tracks the delay experienced by events at each stage of the event processing pipeline.
 * <p>
 * Two families of metrics can be enabled independently: the average and maximum delay ({@code eventDelay_*}), and
 * percentiles and the maximum over a sliding window ({@code eventLatency_*}, see {@link LatencyGauges}). Both report
 * the same delays. The average and maximum delay will eventually be removed.
 * <p>
 * Metrics are split by {@link EventOrigin}, with a dedicated suffix for each origin:
 * <ul>
 *   <li>{@code _runtime} — events created by this runtime ({@link EventOrigin#RUNTIME})</li>
 *   <li>{@code _gossip} — events received through gossip ({@link EventOrigin#GOSSIP})</li>
 *   <li>{@code _storage} — events read from storage ({@link EventOrigin#STORAGE})</li>
 * </ul>
 */
public class EventPipelineTracker {

    private record MetricKey(@NonNull String name, @NonNull EventOrigin origin) {}

    /** The metrics of one stage and origin; a metric is {@code null} if its family is disabled. */
    private record StageMetrics(
            @Nullable AverageAndMaxTimeStat averageAndMax,
            @Nullable LatencyGauges latency) {

        void record(final long nanos) {
            if (averageAndMax != null) {
                averageAndMax.updateNanos(nanos);
            }
            if (latency != null) {
                latency.record(nanos);
            }
        }
    }

    private final Metrics metrics;
    private final Configuration configuration;
    private final Time time;
    private final boolean averageAndMaxEnabled;
    private final boolean latencyEnabled;
    private final Map<MetricKey, StageMetrics> metricMap = new HashMap<>();
    private final Map<EventOrigin, Integer> stepCounters = new EnumMap<>(EventOrigin.class);

    /**
     * Constructor.
     *
     * @param metrics              the metrics system
     * @param configuration        the configuration, holding the metrics snapshot interval that the latency gauges need
     * @param time                 the clock that measures the delays and advances the window of the latency gauges
     * @param averageAndMaxEnabled whether to report the average and maximum delay
     * @param latencyEnabled       whether to report percentiles and the maximum over a sliding window
     */
    public EventPipelineTracker(
            @NonNull final Metrics metrics,
            @NonNull final Configuration configuration,
            @NonNull final Time time,
            final boolean averageAndMaxEnabled,
            final boolean latencyEnabled) {
        this.metrics = requireNonNull(metrics);
        this.configuration = requireNonNull(configuration);
        this.time = requireNonNull(time);
        this.averageAndMaxEnabled = averageAndMaxEnabled;
        this.latencyEnabled = latencyEnabled;
    }

    /**
     * Registers metrics for tracking event delay after the specified stage, for all {@link EventOrigin} values.
     *
     * @param name the name of the stage
     */
    public void registerMetric(@NonNull final String name) {
        registerMetric(name, EventOrigin.values());
    }

    /**
     * Registers metrics for tracking event delay after the specified stage.
     *
     * @param name    the name of the stage
     * @param origins the event origins expected to pass through this stage
     */
    public void registerMetric(@NonNull final String name, @NonNull final EventOrigin... origins) {
        final String baseDesc = "event pipeline delay until after " + name;

        for (final EventOrigin origin : origins) {
            final int step = stepCounters.merge(origin, 1, Integer::sum);
            final String originName = origin.name().toLowerCase();
            final String desc = baseDesc + " (" + originName + ")";
            final AverageAndMaxTimeStat averageAndMax = averageAndMaxEnabled
                    ? new AverageAndMaxTimeStat(
                            metrics,
                            ChronoUnit.MICROS,
                            PLATFORM_CATEGORY,
                            "eventDelay_%d_%s_%s".formatted(step, name, originName),
                            desc)
                    : null;
            final LatencyGauges latency;
            if (latencyEnabled) {
                final String metricName = "eventLatency_%d_%s_%s".formatted(step, name, originName);
                final Config config = new Config(PLATFORM_CATEGORY, metricName).withDescription(desc);
                latency = LatencyGauges.register(metrics, configuration, time, config);
            } else {
                latency = null;
            }
            metricMap.put(new MetricKey(name, origin), new StageMetrics(averageAndMax, latency));
        }
    }

    /**
     * Records the delay experienced by a single event after the specified stage.
     * The event is routed to the appropriate metric based on its {@link EventOrigin}.
     *
     * @param name  the name of the stage
     * @param event the platform event
     */
    public void recordEvent(@NonNull final String name, @NonNull final PlatformEvent event) {
        final StageMetrics stageMetrics = metricMap.get(new MetricKey(name, event.getOrigin()));
        if (stageMetrics != null) {
            stageMetrics.record(
                    Duration.between(event.getTimeReceived(), time.now()).toNanos());
        }
    }

    /**
     * Records the delay experienced by a list of events after the specified stage.
     *
     * @param name   the name of the stage
     * @param events the list of platform events
     */
    public void recordEvents(@NonNull final String name, @NonNull final List<PlatformEvent> events) {
        for (final PlatformEvent event : events) {
            recordEvent(name, event);
        }
    }
}
