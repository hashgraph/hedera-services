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

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.utils.TestUtils;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.swirlds.metrics.api.Metrics;
import org.junit.jupiter.api.Test;

class HandleWorkflowMetricsTest {

    private final Metrics metrics = TestUtils.metrics();

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new HandleWorkflowMetrics(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorInitializesMetrics() {
        // when
        new HandleWorkflowMetrics(metrics);

        // then
        assertThat(metrics.findMetricsByCategory("app")).hasSize((HederaFunctionality.values().length - 1) * 2);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testUpdateWithInvalidArguments() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics);

        // when
        assertThatThrownBy(() -> handleWorkflowMetrics.update(null, 0)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInitialValue() {
        // given
        new HandleWorkflowMetrics(metrics);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(0);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(0);
    }

    @Test
    void testSingleUpdate() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics);

        // when
        handleWorkflowMetrics.update(HederaFunctionality.CRYPTO_CREATE, 42);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(42);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(42);
    }

    @Test
    void testTwoUpdates() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics);

        // when
        handleWorkflowMetrics.update(HederaFunctionality.CRYPTO_CREATE, 11);
        handleWorkflowMetrics.update(HederaFunctionality.CRYPTO_CREATE, 22);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(22);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(16);
    }

    @Test
    void testThreeUpdates() {
        // given
        final var handleWorkflowMetrics = new HandleWorkflowMetrics(metrics);

        // when
        handleWorkflowMetrics.update(HederaFunctionality.CRYPTO_CREATE, 13);
        handleWorkflowMetrics.update(HederaFunctionality.CRYPTO_CREATE, 5);
        handleWorkflowMetrics.update(HederaFunctionality.CRYPTO_CREATE, 3);

        // then
        assertThat(metrics.getMetric("app", "cryptoCreateDurationMax").get(VALUE))
                .isEqualTo(13);
        assertThat(metrics.getMetric("app", "cryptoCreateDurationAvg").get(VALUE))
                .isEqualTo(7);
    }
}
