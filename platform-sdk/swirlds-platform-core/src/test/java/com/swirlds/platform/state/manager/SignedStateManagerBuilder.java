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

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;

/**
 * Utility class for building instances of {@link SignedStateManager}.
 */
public class SignedStateManagerBuilder {

    private final StateConfig stateConfig;
    private final SignedStateMetrics metrics;
    private final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer =
            ReservedSignedState::close; // TODO WTF is this?
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer =
            ReservedSignedState::close; //  and this?
    private StateLacksSignaturesConsumer stateLacksSignaturesConsumer = ReservedSignedState::close; //   and this too?

    public SignedStateManagerBuilder(final StateConfig stateConfig) {
        this.stateConfig = stateConfig;

        this.metrics = new SignedStateMetrics(new NoOpMetrics());
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
