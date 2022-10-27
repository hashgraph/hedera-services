/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.Platform;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrottleGaugesTest {
    @Mock private Platform platform;
    @Mock private DeterministicThrottle aThrottle;
    @Mock private DeterministicThrottle bThrottle;
    @Mock private GasLimitDeterministicThrottle hapiGasThrottle;
    @Mock private GasLimitDeterministicThrottle consGasThrottle;
    @Mock private FunctionalityThrottling hapiThrottling;
    @Mock private FunctionalityThrottling handleThrottling;
    @Mock private NodeLocalProperties nodeProperties;
    @Mock private DoubleGauge pretendGauge;

    @Mock private Metrics metrics;

    private ThrottleGauges subject;

    @BeforeEach
    void setUp() {
        subject = new ThrottleGauges(handleThrottling, hapiThrottling, nodeProperties);
    }

    @Test
    void initializesMetricsAsExpected() {
        givenThrottleMocksWithGas();
        givenThrottleCollabs();
        given(platform.getMetrics()).willReturn(metrics);

        subject.registerWith(platform);

        verify(metrics, times(6)).getOrCreate(any());
    }

    @Test
    void updatesAsExpectedWithGasThrottles() {
        givenThrottleMocksWithGas();
        givenThrottleCollabs();
        given(hapiThrottling.gasLimitThrottle()).willReturn(hapiGasThrottle);
        given(handleThrottling.gasLimitThrottle()).willReturn(consGasThrottle);
        given(aThrottle.percentUsed(any())).willReturn(10.0);
        given(bThrottle.percentUsed(any())).willReturn(50.0);
        given(consGasThrottle.percentUsed(any())).willReturn(33.0);
        given(hapiGasThrottle.percentUsed(any())).willReturn(13.0);
        given(platform.getMetrics()).willReturn(metrics);
        given(metrics.getOrCreate(any())).willReturn(pretendGauge);

        subject.registerWith(platform);
        subject.updateAll();

        verify(aThrottle, times(2)).percentUsed(any());
        verify(bThrottle, times(1)).percentUsed(any());
        verify(consGasThrottle).percentUsed(any());
        verify(hapiGasThrottle).percentUsed(any());
    }

    @Test
    void updatesAsExpectedWithNoGasThrottles() {
        givenThrottleMocksWithoutGas();
        givenThrottleCollabs();
        given(aThrottle.percentUsed(any())).willReturn(10.0);
        given(bThrottle.percentUsed(any())).willReturn(50.0);
        given(platform.getMetrics()).willReturn(metrics);
        given(metrics.getOrCreate(any())).willReturn(pretendGauge);

        subject.registerWith(platform);
        subject.updateAll();

        verify(aThrottle, times(2)).percentUsed(any());
        verify(bThrottle, times(1)).percentUsed(any());
    }

    @Test
    void initializesWithoutGasMetricsAsExpected() {
        givenThrottleMocksWithoutGas();
        givenThrottleCollabs();
        given(platform.getMetrics()).willReturn(metrics);

        subject.registerWith(platform);

        verify(metrics, times(3)).getOrCreate(any());
    }

    private void givenThrottleMocksWithGas() {
        given(nodeProperties.consThrottlesToSample()).willReturn(List.of("A", "<GAS>"));
        given(nodeProperties.hapiThrottlesToSample()).willReturn(List.of("A", "B", "C", "<GAS>"));
        given(hapiThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
        given(handleThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
    }

    private void givenThrottleMocksWithoutGas() {
        given(nodeProperties.consThrottlesToSample()).willReturn(List.of("A"));
        given(nodeProperties.hapiThrottlesToSample()).willReturn(List.of("A", "B"));
        given(hapiThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
        given(handleThrottling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
    }

    private void givenThrottleCollabs() {
        final var names = List.of("A", "B");
        final var mocks = List.of(aThrottle, bThrottle);
        for (int i = 0; i < 2; i++) {
            final var mockThrottle = mocks.get(i);
            final var mockName = names.get(i);
            given(mockThrottle.name()).willReturn(mockName);
        }
    }
}
