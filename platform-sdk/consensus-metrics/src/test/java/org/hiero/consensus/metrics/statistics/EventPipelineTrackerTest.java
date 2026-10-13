// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.metrics.statistics;

import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.consensus.metrics.config.EventPipelineMetricsConfig_.AVERAGE_AND_MAX_ENABLED;
import static org.hiero.consensus.metrics.config.EventPipelineMetricsConfig_.LATENCY_ENABLED;
import static org.hiero.consensus.metrics.config.EventPipelineMetricsConfig_.LATENCY_GOSSIP_WINDOW;
import static org.hiero.consensus.metrics.config.EventPipelineMetricsConfig_.LATENCY_RUNTIME_WINDOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.Metric.ValueType;
import com.swirlds.metrics.api.Metrics;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import org.hiero.consensus.metrics.config.MetricsConfig;
import org.hiero.consensus.metrics.platform.DefaultPlatformMetrics;
import org.hiero.consensus.metrics.platform.MetricKeyRegistry;
import org.hiero.consensus.metrics.platform.PlatformMetricsFactoryImpl;
import org.hiero.consensus.model.event.EventOrigin;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.node.NodeId;
import org.junit.jupiter.api.Test;

/**
 * Checks which metrics {@link EventPipelineTracker} registers and that one event updates both families.
 */
class EventPipelineTrackerTest {

    private static final String STAGE = "hashing";
    /** Differs from the default, so the tests show the window comes from the configuration. */
    private static final String RUNTIME_WINDOW = "20m";
    /** Differs from the default, so the tests show the window comes from the configuration. */
    private static final String GOSSIP_WINDOW = "2m";
    /** The step of the gossip gauges: their window in 20 snapshots, at the default interval of 3 s. */
    private static final Duration GOSSIP_STEP = Duration.ofSeconds(6);

    private final FakeTime time = new FakeTime();
    private final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
    private final Metrics metrics = newMetrics(configuration);

    @Test
    void noTrackerIfBothFamiliesAreDisabled() {
        assertThat(EventPipelineTracker.create(metrics, trackerConfiguration(false, false), time))
                .isNull();
    }

    @Test
    void defaultWindowsAreTenAndFiveMinutes() {
        requireNonNull(EventPipelineTracker.create(metrics, configuration, time))
                .registerMetric(STAGE, EventOrigin.RUNTIME, EventOrigin.GOSSIP);
        assertThat(metrics.getMetric(PLATFORM_CATEGORY, "eventLatency_1_hashing_runtime_max")
                        .getDescription())
                .endsWith("over the last 600 s");
        assertThat(metrics.getMetric(PLATFORM_CATEGORY, "eventLatency_1_hashing_gossip_max")
                        .getDescription())
                .endsWith("over the last 300 s");
    }

    @Test
    void bothFamiliesAreRegistered() {
        newTracker(true, true).registerMetric(STAGE, EventOrigin.GOSSIP);
        assertThat(metrics.getAll())
                .extracting(Metric::getName)
                .containsExactlyInAnyOrder(
                        "eventDelay_1_hashing_gossip",
                        "eventDelay_1_hashing_gossipMAX",
                        "eventLatency_1_hashing_gossip_p50",
                        "eventLatency_1_hashing_gossip_p90",
                        "eventLatency_1_hashing_gossip_p99",
                        "eventLatency_1_hashing_gossip_p99_9",
                        "eventLatency_1_hashing_gossip_max");
        assertThat(metrics.getMetric(PLATFORM_CATEGORY, "eventLatency_1_hashing_gossip_max")
                        .getDescription())
                .endsWith("over the last 120 s");
    }

    @Test
    void runtimeReportsUpToP99() {
        newTracker(false, true).registerMetric(STAGE, EventOrigin.RUNTIME);
        assertThat(metrics.getAll())
                .extracting(Metric::getName)
                .containsExactlyInAnyOrder(
                        "eventLatency_1_hashing_runtime_p50",
                        "eventLatency_1_hashing_runtime_p90",
                        "eventLatency_1_hashing_runtime_p99",
                        "eventLatency_1_hashing_runtime_max");
        assertThat(metrics.getMetric(PLATFORM_CATEGORY, "eventLatency_1_hashing_runtime_max")
                        .getDescription())
                .endsWith("over the last 1200 s");
    }

    @Test
    void storageGetsNoLatencyGauges() {
        newTracker(true, true).registerMetric(STAGE, EventOrigin.STORAGE);
        assertThat(metrics.getAll())
                .extracting(Metric::getName)
                .containsExactlyInAnyOrder("eventDelay_1_hashing_storage", "eventDelay_1_hashing_storageMAX");
    }

    @Test
    void onlyTheAverageAndMaxAreRegistered() {
        newTracker(true, false).registerMetric(STAGE, EventOrigin.GOSSIP);
        assertThat(metrics.getAll())
                .extracting(Metric::getName)
                .containsExactlyInAnyOrder("eventDelay_1_hashing_gossip", "eventDelay_1_hashing_gossipMAX");
    }

    @Test
    void onlyTheLatencyGaugesAreRegistered() {
        newTracker(false, true).registerMetric(STAGE, EventOrigin.GOSSIP);
        assertThat(metrics.getAll())
                .extracting(Metric::getName)
                .allMatch(name -> name.startsWith("eventLatency_1_hashing_gossip_"))
                .hasSize(5);
    }

    @Test
    void stepsAreNumberedPerOrigin() {
        final EventPipelineTracker tracker = newTracker(false, true);
        tracker.registerMetric("eventCreation", EventOrigin.RUNTIME);
        tracker.registerMetric(STAGE);
        assertThat(metrics.getAll())
                .extracting(Metric::getName)
                .contains(
                        "eventLatency_1_eventCreation_runtime_max",
                        "eventLatency_2_hashing_runtime_max",
                        "eventLatency_1_hashing_gossip_max");
    }

    @Test
    void oneEventUpdatesBothFamilies() {
        final EventPipelineTracker tracker = newTracker(true, true);
        tracker.registerMetric(STAGE, EventOrigin.GOSSIP);
        tracker.recordEvent(STAGE, eventReceivedAgo(Duration.of(200, ChronoUnit.MICROS)));
        time.tick(GOSSIP_STEP);

        assertThat(valueOf("eventDelay_1_hashing_gossip")).isEqualTo(200.0);
        assertThat(valueOf("eventDelay_1_hashing_gossipMAX")).isEqualTo(200.0);
        assertThat(valueOf("eventLatency_1_hashing_gossip_p50"))
                .as("at most 2^-7 above the exact value")
                .isBetween(200.0, 200.0 * (1 + 1.0 / 128));
        assertThat(valueOf("eventLatency_1_hashing_gossip_max"))
                .isEqualTo(valueOf("eventLatency_1_hashing_gossip_p50"));
    }

    @Test
    void negativeDelaysCountAsZeroOnlyInTheLatencyGauges() {
        final EventPipelineTracker tracker = newTracker(true, true);
        tracker.registerMetric(STAGE, EventOrigin.GOSSIP);
        tracker.recordEvent(STAGE, eventReceivedAgo(Duration.ofMillis(-1)));
        tracker.recordEvent(STAGE, eventReceivedAgo(Duration.ofMillis(1)));
        time.tick(GOSSIP_STEP);

        assertThat(valueOf("eventLatency_1_hashing_gossip_p50"))
                .as("the negative delay is the lower of the two values")
                .isEqualTo(0.0);
        assertThat(valueOf("eventLatency_1_hashing_gossip_max")).isGreaterThanOrEqualTo(1_000.0);
        assertThat(valueOf("eventDelay_1_hashing_gossip"))
                .as("the average keeps recording negative delays")
                .isNegative();
    }

    private EventPipelineTracker newTracker(final boolean averageAndMaxEnabled, final boolean latencyEnabled) {
        return requireNonNull(
                EventPipelineTracker.create(metrics, trackerConfiguration(averageAndMaxEnabled, latencyEnabled), time));
    }

    private static Configuration trackerConfiguration(
            final boolean averageAndMaxEnabled, final boolean latencyEnabled) {
        return new TestConfigBuilder()
                .withValue(AVERAGE_AND_MAX_ENABLED, averageAndMaxEnabled)
                .withValue(LATENCY_ENABLED, latencyEnabled)
                .withValue(LATENCY_RUNTIME_WINDOW, RUNTIME_WINDOW)
                .withValue(LATENCY_GOSSIP_WINDOW, GOSSIP_WINDOW)
                .getOrCreateConfig();
    }

    private PlatformEvent eventReceivedAgo(final Duration delay) {
        final Instant received = time.now().minus(delay);
        final PlatformEvent event = mock(PlatformEvent.class);
        when(event.getOrigin()).thenReturn(EventOrigin.GOSSIP);
        when(event.getTimeReceived()).thenReturn(received);
        return event;
    }

    private double valueOf(final String name) {
        return (Double) metrics.getMetric(PLATFORM_CATEGORY, name).get(ValueType.VALUE);
    }

    private static Metrics newMetrics(final Configuration configuration) {
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        return new DefaultPlatformMetrics(
                NodeId.of(1L),
                registry,
                mock(ScheduledExecutorService.class),
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
    }
}
