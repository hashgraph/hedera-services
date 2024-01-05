/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.platform.system.Platform;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrottleGaugesTest {
    @Mock
    private Platform platform;

    @Mock
    private DeterministicThrottle aThrottle;

    @Mock
    private DeterministicThrottle bThrottle;

    @Mock
    private GasLimitDeterministicThrottle hapiGasThrottle;

    @Mock
    private GasLimitDeterministicThrottle consGasThrottle;

    @Mock
    private FunctionalityThrottling hapiThrottling;

    @Mock
    private FunctionalityThrottling handleThrottling;

    @Mock
    private NodeLocalProperties nodeProperties;

    @Mock
    private DoubleGauge pretendGauge;

    @Mock
    private Metrics metrics;

    private ThrottleGauges subject;

    @BeforeEach
    void setup() {
        subject = new ThrottleGauges(handleThrottling, hapiThrottling, nodeProperties);
    }

    @Test
    void initializesMetricsAsExpected() {
        givenThrottleMocksWithGas();
        givenThrottleCollabs();
        final var platformContext = mock(PlatformContext.class);
        given(platform.getContext()).willReturn(platformContext);
        given(platformContext.getMetrics()).willReturn(metrics);

        subject.registerWith(platform);

        verify(metrics, times(6)).getOrCreate(any());
    }

    @Test
    void updatesAsExpectedWithGasThrottles() {
        givenThrottleMocksWithGas();
        givenThrottleCollabs();
        given(hapiThrottling.gasLimitThrottle()).willReturn(hapiGasThrottle);
        given(handleThrottling.gasLimitThrottle()).willReturn(consGasThrottle);
        given(aThrottle.instantaneousPercentUsed()).willReturn(10.0);
        given(bThrottle.instantaneousPercentUsed()).willReturn(50.0);
        given(consGasThrottle.instantaneousPercentUsed()).willReturn(33.0);
        given(hapiGasThrottle.instantaneousPercentUsed()).willReturn(13.0);
        final var platformContext = mock(PlatformContext.class);
        given(platform.getContext()).willReturn(platformContext);
        given(platformContext.getMetrics()).willReturn(metrics);
        given(metrics.getOrCreate(any())).willReturn(pretendGauge);

        subject.registerWith(platform);
        subject.updateAll();

        verify(aThrottle, times(2)).instantaneousPercentUsed();
        verify(bThrottle, times(1)).instantaneousPercentUsed();
        verify(consGasThrottle).instantaneousPercentUsed();
        verify(hapiGasThrottle).instantaneousPercentUsed();
    }

    @Test
    void updatesAsExpectedWithNoGasThrottles() {
        givenThrottleMocksWithoutGas();
        givenThrottleCollabs();
        given(aThrottle.instantaneousPercentUsed()).willReturn(10.0);
        given(bThrottle.instantaneousPercentUsed()).willReturn(50.0);
        final var platformContext = mock(PlatformContext.class);
        given(platform.getContext()).willReturn(platformContext);
        given(platformContext.getMetrics()).willReturn(metrics);
        given(metrics.getOrCreate(any())).willReturn(pretendGauge);

        subject.registerWith(platform);
        subject.updateAll();

        verify(aThrottle, times(2)).instantaneousPercentUsed();
        verify(bThrottle, times(1)).instantaneousPercentUsed();
    }

    @Test
    void initializesWithoutGasMetricsAsExpected() {
        givenThrottleMocksWithoutGas();
        givenThrottleCollabs();
        final var platformContext = mock(PlatformContext.class);
        given(platform.getContext()).willReturn(platformContext);
        given(platformContext.getMetrics()).willReturn(metrics);

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
