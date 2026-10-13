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
import org.hiero.consensus.metrics.config.EventPipelineMetricsConfig;
import org.hiero.consensus.metrics.extensions.LatencyGauges;
import org.hiero.consensus.metrics.extensions.LatencyGauges.Config;
import org.hiero.consensus.model.event.EventOrigin;
import org.hiero.consensus.model.event.PlatformEvent;

/**
 * Tracks the delay experienced by events at each stage of the event processing pipeline.
 * <p>
 * Two families of metrics can be enabled independently in {@link EventPipelineMetricsConfig}: the average and maximum
 * delay ({@code eventDelay_*}), and percentiles and the maximum over a sliding window ({@code eventLatency_*}, see
 * {@link LatencyGauges}). Both report the same delays. The average and maximum delay will eventually be removed.
 * <p>
 * Metrics are split by {@link EventOrigin}, with a dedicated suffix for each origin:
 * <ul>
 *   <li>{@code _runtime} — events created by this runtime ({@link EventOrigin#RUNTIME})</li>
 *   <li>{@code _gossip} — events received through gossip ({@link EventOrigin#GOSSIP})</li>
 *   <li>{@code _storage} — events read from storage ({@link EventOrigin#STORAGE}), only the average and maximum
 *   delay: they arrive only during the replay at startup, so a sliding window would stay empty afterwards</li>
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
    private final Duration runtimeWindow;
    private final Duration gossipWindow;
    private final Map<MetricKey, StageMetrics> metricMap = new HashMap<>();
    private final Map<EventOrigin, Integer> stepCounters = new EnumMap<>(EventOrigin.class);

    /**
     * Creates a tracker, unless the {@link EventPipelineMetricsConfig} disables both families of metrics.
     *
     * @param metrics       the metrics system
     * @param configuration the configuration
     * @param time          the clock that measures the delays and advances the window of the latency gauges
     * @return the tracker, or {@code null} if both families of metrics are disabled
     */
    @Nullable
    public static EventPipelineTracker create(
            @NonNull final Metrics metrics, @NonNull final Configuration configuration, @NonNull final Time time) {
        final EventPipelineMetricsConfig config = configuration.getConfigData(EventPipelineMetricsConfig.class);
        return config.averageAndMaxEnabled() || config.latencyEnabled()
                ? new EventPipelineTracker(metrics, configuration, time, config)
                : null;
    }

    private EventPipelineTracker(
            @NonNull final Metrics metrics,
            @NonNull final Configuration configuration,
            @NonNull final Time time,
            @NonNull final EventPipelineMetricsConfig config) {
        this.metrics = requireNonNull(metrics);
        this.configuration = requireNonNull(configuration);
        this.time = requireNonNull(time);
        this.averageAndMaxEnabled = config.averageAndMaxEnabled();
        this.latencyEnabled = config.latencyEnabled();
        this.runtimeWindow = config.latencyRuntimeWindow();
        this.gossipWindow = config.latencyGossipWindow();
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
            if (latencyEnabled && origin != EventOrigin.STORAGE) {
                final Config config = latencyConfig(origin, "eventLatency_%d_%s_%s".formatted(step, name, originName));
                latency = LatencyGauges.register(metrics, configuration, time, config.withDescription(desc));
            } else {
                latency = null;
            }
            metricMap.put(new MetricKey(name, origin), new StageMetrics(averageAndMax, latency));
        }
    }

    /**
     * Returns the parameters of the latency gauges of an origin. A node receives many more events through gossip than
     * it creates, so the window over gossiped events holds enough of them for p99.9.
     *
     * @param origin the event origin
     * @param name   the base name of the gauges
     * @return the parameters of the latency gauges
     * @throws IllegalArgumentException if the origin has no latency gauges
     */
    @NonNull
    private Config latencyConfig(@NonNull final EventOrigin origin, @NonNull final String name) {
        return switch (origin) {
            case RUNTIME ->
                new Config(PLATFORM_CATEGORY, name).withWindow(runtimeWindow).withPercentiles(50, 90, 99);
            case GOSSIP ->
                new Config(PLATFORM_CATEGORY, name).withWindow(gossipWindow).withPercentiles(50, 90, 99, 99.9);
            case STORAGE -> throw new IllegalArgumentException("no latency gauges for events read from storage");
        };
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
