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

package com.swirlds.platform.state;

import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateNexus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class SignedStateManagerTester extends SignedStateManager {
    private final SignedStateNexus latestSignedState;

    private SignedStateManagerTester(
            @NonNull final StateConfig stateConfig,
            @NonNull final SignedStateMetrics signedStateMetrics,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final StateLacksSignaturesConsumer stateLacksSignaturesConsumer,
            @NonNull final SignedStateNexus latestSignedState) {
        super(
                stateConfig,
                signedStateMetrics,
                newLatestCompleteStateConsumer,
                stateHasEnoughSignaturesConsumer,
                stateLacksSignaturesConsumer);
        this.latestSignedState = latestSignedState;
    }

    public static SignedStateManagerTester create(
            @NonNull final StateConfig stateConfig,
            @NonNull final SignedStateMetrics signedStateMetrics,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final StateLacksSignaturesConsumer stateLacksSignaturesConsumer) {
        final SignedStateNexus latestSignedState = new SignedStateNexus();
        return new SignedStateManagerTester(
                stateConfig,
                signedStateMetrics,
                s -> {
                    newLatestCompleteStateConsumer.newLatestCompleteStateEvent(s);
                    latestSignedState.setState(s.reserve("for nexus"));
                },
                stateHasEnoughSignaturesConsumer,
                stateLacksSignaturesConsumer,
                latestSignedState);
    }

    /**
     * Get the last complete signed state
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return the latest complete signed state, or a null if there are no recent states that are complete
     */
    @Nullable
    public ReservedSignedState getLatestSignedState(@NonNull final String reason) {
        return latestSignedState.getState(reason);
    }

    public long getLastCompleteRound() {
        return latestSignedState.getRound();
    }
}
