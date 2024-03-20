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

package com.hedera.node.app.throttle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.throttle.ThrottleAccumulator.ThrottleType;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.metrics.api.DoubleGauge;
import com.swirlds.metrics.api.Metrics;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrottleMetricsTest {

    @Mock
    private Metrics metrics;

    @SuppressWarnings("DataFlowIssue")
    @Test
    void constructorWithInvalidArguments() {
        assertThatThrownBy(() -> new ThrottleMetrics(null, ThrottleType.FRONTEND_THROTTLE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThrottleMetrics(metrics, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void setupLiveMetricShouldCreateFunctionGauge(@Mock DeterministicThrottle throttle) {
        // given
        when(throttle.name()).thenReturn("throttle1");
        final var configuration = HederaTestConfigBuilder.create()
                .withValue("stats.hapiThrottlesToSample", "throttle1")
                .getOrCreateConfig();
        final var throttleMetrics = new ThrottleMetrics(metrics, ThrottleType.FRONTEND_THROTTLE);

        // when
        throttleMetrics.setupThrottles(List.of(throttle), configuration);

        // then
        verify(metrics).getOrCreate(any(FunctionGauge.Config.class));
    }

    @Test
    void setupNonTrackedLiveMetricShouldNotCreateMetric(@Mock DeterministicThrottle throttle) {
        // given
        when(throttle.name()).thenReturn("throttle1");
        final var configuration = HederaTestConfigBuilder.create()
                .withValue("stats.hapiThrottlesToSample", "")
                .getOrCreateConfig();
        final var throttleMetrics = new ThrottleMetrics(metrics, ThrottleType.FRONTEND_THROTTLE);

        // when
        throttleMetrics.setupThrottles(List.of(throttle), configuration);

        // then
        verify(metrics, never()).getOrCreate(any());
    }

    @Test
    void setupInertMetricShouldCreateDoubleGauge() {
        // given
        final var configuration = HederaTestConfigBuilder.create()
                .withValue("stats.hapiThrottlesToSample", "throttle2")
                .getOrCreateConfig();
        final var throttleMetrics = new ThrottleMetrics(metrics, ThrottleType.FRONTEND_THROTTLE);

        // when
        throttleMetrics.setupThrottles(List.of(), configuration);

        // then
        verify(metrics).getOrCreate(any(DoubleGauge.Config.class));
    }

    @Test
    void setupGasMetricShouldCreateFunctionGauge(@Mock GasLimitDeterministicThrottle throttle) {
        // given
        final var configuration = HederaTestConfigBuilder.create()
                .withValue("stats.hapiThrottlesToSample", "<GAS>")
                .getOrCreateConfig();
        final var throttleMetrics = new ThrottleMetrics(metrics, ThrottleType.FRONTEND_THROTTLE);

        // when
        throttleMetrics.setupGasThrottle(throttle, configuration);

        // then
        verify(metrics).getOrCreate(any(FunctionGauge.Config.class));
    }

    @Test
    void setupNonTrackedGasMetricShouldNotCreateMetric(@Mock GasLimitDeterministicThrottle throttle) {
        // given
        final var configuration = HederaTestConfigBuilder.create()
                .withValue("stats.hapiThrottlesToSample", "")
                .getOrCreateConfig();
        final var throttleMetrics = new ThrottleMetrics(metrics, ThrottleType.FRONTEND_THROTTLE);

        // when
        throttleMetrics.setupGasThrottle(throttle, configuration);

        // then
        verify(metrics, never()).getOrCreate(any());
    }
}
