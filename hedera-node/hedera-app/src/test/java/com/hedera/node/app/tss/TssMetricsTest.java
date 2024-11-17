/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss;

import static com.swirlds.metrics.api.Metric.ValueType.VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricType;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TssMetricsTest {
    private static final long DEFAULT_NODE_ID = 3;
    private final Metrics metrics = fakeMetrics();
    private TssMetrics tssMetrics;

    @BeforeEach
    void setUp() {
        tssMetrics = getTssMetrics();
    }

    @Test
    public void aggregationTimeGetUpdated() {
        final long aggregationTime = InstantSource.system().instant().getEpochSecond();
        tssMetrics.updateAggregationTime(aggregationTime);
        assertThat(tssMetrics.getAggregationTime()).isEqualTo(aggregationTime);
    }

    @Test
    public void ledgerSignatureTimeGetsUpdated() {
        final long aggregationTime = InstantSource.system().instant().getEpochSecond();
        tssMetrics.updateLedgerSignatureTime(aggregationTime);
        assertThat(tssMetrics.getTssLedgerSignatureTime()).isEqualTo(aggregationTime);
    }

    @Test
    public void candidateRosterLifecycleGetUpdated() {
        final Instant candidateRosterLifecycleStart = InstantSource.system().instant();
        tssMetrics.trackCandidateRosterLifecycleStart(candidateRosterLifecycleStart);
        final Instant candidateRosterLifecycleEnd =
                InstantSource.system().instant().plusMillis(5000);
        tssMetrics.updateCandidateRosterLifecycle(candidateRosterLifecycleEnd);

        final long lifecycleDuration = Duration.between(candidateRosterLifecycleStart, candidateRosterLifecycleEnd)
                .toMillis();
        assertThat(tssMetrics.getCandidateRosterLifecycle()).isEqualTo(lifecycleDuration);
    }

    @Test
    public void messagesPerCandidateRosterGetUpdated() {
        final Bytes candidateRosterHash = Bytes.EMPTY;

        // register first occurrence of message per candidate roster
        // (this should create a new Counter metric for this specific roster)
        tssMetrics.updateMessagesPerCandidateRoster(candidateRosterHash);
        assertThat(tssMetrics.getMessagesPerCandidateRoster(candidateRosterHash).getMetricType())
                .isEqualTo(MetricType.COUNTER);
        assertThat(tssMetrics.getMessagesPerCandidateRoster(candidateRosterHash).getDataType())
                .isEqualTo(Metric.DataType.INT);
        assertThat(tssMetrics.getMessagesPerCandidateRoster(candidateRosterHash).getValueTypes())
                .isEqualTo(EnumSet.of(VALUE));
        assertThat(tssMetrics.getMessagesPerCandidateRoster(candidateRosterHash).get())
                .isEqualTo(1L);

        // check whether the metric is incremented (increased by 1)
        tssMetrics.updateMessagesPerCandidateRoster(candidateRosterHash);
        assertThat(tssMetrics.getMessagesPerCandidateRoster(candidateRosterHash).get())
                .isEqualTo(2L);
    }

    @Test
    public void votesPerCandidateRosterGetUpdated() {
        final Bytes candidateRosterHash = Bytes.EMPTY;

        // register first occurrence of vote per candidate roster
        // (this should create a new Counter metric for this specific roster)
        tssMetrics.updateVotesPerCandidateRoster(candidateRosterHash);
        assertThat(tssMetrics.getVotesPerCandidateRoster(candidateRosterHash).getMetricType())
                .isEqualTo(MetricType.COUNTER);
        assertThat(tssMetrics.getVotesPerCandidateRoster(candidateRosterHash).getDataType())
                .isEqualTo(Metric.DataType.INT);
        assertThat(tssMetrics.getVotesPerCandidateRoster(candidateRosterHash).getValueTypes())
                .isEqualTo(EnumSet.of(VALUE));
        assertThat(tssMetrics.getVotesPerCandidateRoster(candidateRosterHash).get())
                .isEqualTo(1L);

        // check whether the metric is incremented (increased by 1)
        tssMetrics.updateVotesPerCandidateRoster(candidateRosterHash);
        assertThat(tssMetrics.getVotesPerCandidateRoster(candidateRosterHash).get())
                .isEqualTo(2L);
    }

    private @NonNull TssMetrics getTssMetrics() {
        return new TssMetrics(metrics);
    }

    private static Metrics fakeMetrics() {
        final MetricsConfig metricsConfig =
                HederaTestConfigBuilder.createConfig().getConfigData(MetricsConfig.class);

        return new DefaultPlatformMetrics(
                NodeId.of(DEFAULT_NODE_ID),
                new MetricKeyRegistry(),
                Executors.newSingleThreadScheduledExecutor(),
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
    }
}
