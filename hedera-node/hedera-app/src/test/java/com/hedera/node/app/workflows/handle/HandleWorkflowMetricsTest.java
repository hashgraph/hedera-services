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

package com.hedera.node.app.workflows.handle;

import static com.swirlds.metrics.api.Metric.ValueType.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.utils.TestUtils;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.metrics.api.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandleWorkflowMetricsTest {

    private final Metrics metrics = TestUtils.metrics();
    private ConfigProvider configProvider;

    @BeforeEach
    void setUp() {
        configProvider = () -> new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), 1);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new HandleWorkflowMetrics(null, configProvider))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflowMetrics(metrics, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorInitializesMetrics() {
        // when
        new HandleWorkflowMetrics(metrics, configProvider);

        // then
        final int transactionMetricsCount = (HederaFunctionality.values().length - 1) * 2;
        assertThat(metrics.findMetricsByCategory("app")).hasSize(transactionMetricsCount + 1);
    }

    @Test
    void testInitialValue() {
        // given
        new HandleWorkflowMetrics(metrics, configProvider);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(0);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(0);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testUpdateTransactionDurationWithInvalidArguments() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics, configProvider);

        // when
        assertThatThrownBy(() -> handleWorkflowMetrics.updateTransactionDuration(null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testUpdateTransactionDurationSingleUpdate() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics, configProvider);

        // when
        handleWorkflowMetrics.updateTransactionDuration(HederaFunctionality.CRYPTO_CREATE, 42);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(42);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(42);
    }

    @Test
    void testUpdateTransactionDurationTwoUpdates() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics, configProvider);

        // when
        handleWorkflowMetrics.updateTransactionDuration(HederaFunctionality.CRYPTO_CREATE, 11);
        handleWorkflowMetrics.updateTransactionDuration(HederaFunctionality.CRYPTO_CREATE, 22);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(22);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(16);
    }

    @Test
    void testUpdateTransactionDurationThreeUpdates() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics, configProvider);

        // when
        handleWorkflowMetrics.updateTransactionDuration(HederaFunctionality.CRYPTO_CREATE, 13);
        handleWorkflowMetrics.updateTransactionDuration(HederaFunctionality.CRYPTO_CREATE, 5);
        handleWorkflowMetrics.updateTransactionDuration(HederaFunctionality.CRYPTO_CREATE, 3);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(13);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(7);
    }

    @Test
    void testInitialStartConsensusRound() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics, configProvider);

        // when
        handleWorkflowMetrics.switchConsensusSecond();

        // then
        assertThat((Double) metrics.getMetric("app", "gasPerConsSec").get(VALUE))
                .isCloseTo(0.0, offset(1e-6));
    }

    @Test
    void testUpdateGasZero() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics, configProvider);

        // when
        handleWorkflowMetrics.addGasUsed(0L);
        handleWorkflowMetrics.switchConsensusSecond();

        // then
        assertThat((Double) metrics.getMetric("app", "gasPerConsSec").get(VALUE))
                .isCloseTo(0.0, offset(1e-6));
    }

    @Test
    void testUpdateGas() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics, configProvider);

        // when
        handleWorkflowMetrics.addGasUsed(1_000_000L);
        handleWorkflowMetrics.switchConsensusSecond();

        // then
        assertThat((Double) metrics.getMetric("app", "gasPerConsSec").get(VALUE))
                .isGreaterThan(0.0);
    }
}
