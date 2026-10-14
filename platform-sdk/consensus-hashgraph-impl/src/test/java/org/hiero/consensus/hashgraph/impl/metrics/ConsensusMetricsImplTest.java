// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.metrics;

import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Metric.ValueType;
import com.swirlds.metrics.api.Metrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import org.hiero.consensus.metrics.config.ConsensusLayerMetricsConfig_;
import org.hiero.consensus.metrics.config.MetricsConfig;
import org.hiero.consensus.metrics.platform.DefaultPlatformMetrics;
import org.hiero.consensus.metrics.platform.MetricKeyRegistry;
import org.hiero.consensus.metrics.platform.PlatformMetricsFactoryImpl;
import org.hiero.consensus.model.event.EventOrigin;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.hashgraph.ConsensusRound;
import org.hiero.consensus.model.hashgraph.EventWindow;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.consensus.model.test.fixtures.event.TestingEventBuilder;
import org.hiero.consensus.model.test.fixtures.roster.RosterWrapperFactory;
import org.hiero.consensus.test.fixtures.Randotron;
import org.junit.jupiter.api.Test;

/**
 * Checks the latency gauges of {@link ConsensusMetricsImpl}.
 */
class ConsensusMetricsImplTest {

    /** The step of the gauges over the default window of 5 minutes: 300 s in 20 snapshots, at the interval of 3 s. */
    private static final Duration STEP = Duration.ofSeconds(15);

    private final Random random = Randotron.create();
    private final FakeTime time = new FakeTime();

    @Test
    void receivedToConsensusCoversOnlyGossipedEvents() {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final Metrics metrics = newMetrics(configuration);
        final ConsensusMetrics consensusMetrics = new ConsensusMetricsImpl(NodeId.of(0), metrics, configuration, time);
        final PlatformEvent gossiped = newEvent(EventOrigin.GOSSIP, time.now());
        final PlatformEvent own = newEvent(EventOrigin.RUNTIME, time.now().minusSeconds(1));
        final PlatformEvent replayed = newEvent(EventOrigin.STORAGE, time.now().minusSeconds(1));

        time.tick(Duration.ofMillis(2));
        consensusMetrics.consensusRoundEmitted(newRound(gossiped, own, replayed));
        time.tick(STEP);

        assertThat((Double) metrics.getMetric(PLATFORM_CATEGORY, "eventLatency_R2C_max")
                        .get(ValueType.VALUE))
                .as("from receiving the gossiped event, without the own and the replayed event")
                .isBetween(2_000.0, 2_000.0 * (1 + 1.0 / 128));
        assertThat(metrics.getMetric(PLATFORM_CATEGORY, "eventLatency_R2C_max").getDescription())
                .endsWith("over the last 300 s");
    }

    @Test
    void noLatencyGaugesIfTheyAreDisabled() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(ConsensusLayerMetricsConfig_.LATENCY_ENABLED, false)
                .getOrCreateConfig();
        final Metrics metrics = newMetrics(configuration);
        final ConsensusMetrics consensusMetrics = new ConsensusMetricsImpl(NodeId.of(0), metrics, configuration, time);

        consensusMetrics.consensusRoundEmitted(newRound(newEvent(EventOrigin.GOSSIP, time.now())));

        assertThat(metrics.getMetric(PLATFORM_CATEGORY, "eventLatency_R2C_max")).isNull();
    }

    private PlatformEvent newEvent(final EventOrigin origin, final Instant received) {
        final PlatformEvent event =
                new TestingEventBuilder(random).setOrigin(origin).build();
        event.setTimeReceived(received);
        return event;
    }

    private ConsensusRound newRound(final PlatformEvent... events) {
        return new ConsensusRound(
                RosterWrapperFactory.randomRoster(random, 1),
                List.of(events),
                EventWindow.getGenesisEventWindow(),
                ConsensusSnapshot.DEFAULT,
                false,
                time.now());
    }

    private static Metrics newMetrics(final Configuration configuration) {
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        return new DefaultPlatformMetrics(
                null,
                new MetricKeyRegistry(),
                Executors.newSingleThreadScheduledExecutor(),
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
    }
}
