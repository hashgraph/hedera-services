/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.manager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateWrapper;

/**
 * Utility class for building instances of {@link SignedStateManager}.
 */
public class SignedStateManagerBuilder {

    private final StateConfig stateConfig;
    private final SignedStateMetrics metrics;
    private NewLatestCompleteStateConsumer newLatestCompleteStateConsumer = SignedStateWrapper::release;
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer = SignedStateWrapper::release;
    private StateLacksSignaturesConsumer stateLacksSignaturesConsumer = SignedStateWrapper::release;

    public SignedStateManagerBuilder(final AddressBook addressBook, final StateConfig stateConfig, final long selfId) {
        this.stateConfig = stateConfig;

        this.metrics = mock(SignedStateMetrics.class);
        when(metrics.getFreshStatesMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStaleStatesMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getTotalUnsignedStatesMetric()).thenReturn(mock(Counter.class));
        when(metrics.getStateSignaturesGatheredPerSecondMetric()).thenReturn(mock(SpeedometerMetric.class));
        when(metrics.getStatesSignedPerSecondMetric()).thenReturn(mock(SpeedometerMetric.class));
        when(metrics.getAverageTimeToFullySignStateMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getSignedStateHashingTimeMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateDeletionQueueAvgMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateDeletionTimeAvgMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateArchivalTimeAvgMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateSignatureAge()).thenReturn(mock(RunningAverageMetric.class));
    }

    public SignedStateManagerBuilder newLatestCompleteStateConsumer(final NewLatestCompleteStateConsumer consumer) {
        this.newLatestCompleteStateConsumer = consumer;
        return this;
    }

    public SignedStateManagerBuilder stateHasEnoughSignaturesConsumer(final StateHasEnoughSignaturesConsumer consumer) {
        this.stateHasEnoughSignaturesConsumer = consumer;
        return this;
    }

    public SignedStateManagerBuilder stateLacksSignaturesConsumer(final StateLacksSignaturesConsumer consumer) {
        this.stateLacksSignaturesConsumer = consumer;
        return this;
    }

    public SignedStateManager build() {
        return new SignedStateManager(
                stateConfig,
                metrics,
                newLatestCompleteStateConsumer,
                stateHasEnoughSignaturesConsumer,
                stateLacksSignaturesConsumer);
    }
}
