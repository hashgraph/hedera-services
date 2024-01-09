/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A SignedStateManager that is used for unit testing. Since the SignedStateManager is in the process of being broken up
 * into smaller components, this class is a temporary solution to allow unit tests function. In the future, these unit
 * tests should become small integration tests that test multiple components.
 */
public class SignedStateManagerTester extends SignedStateManager {
    private final LatestCompleteStateNexus latestSignedState;
    private final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer;
    private final StateLacksSignaturesConsumer stateLacksSignaturesConsumer;
    
    private SignedStateManagerTester(
            @NonNull final StateConfig stateConfig,
            @NonNull final SignedStateMetrics signedStateMetrics,
            @NonNull final LatestCompleteStateNexus latestSignedState,
            @NonNull final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final StateLacksSignaturesConsumer stateLacksSignaturesConsumer) {
        super(stateConfig, signedStateMetrics);
        this.latestSignedState = latestSignedState;
        this.stateHasEnoughSignaturesConsumer = stateHasEnoughSignaturesConsumer;
        this.stateLacksSignaturesConsumer = stateLacksSignaturesConsumer;
    }

    public static SignedStateManagerTester create(
            @NonNull final StateConfig stateConfig,
            @NonNull final SignedStateMetrics signedStateMetrics,
            @NonNull final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final StateLacksSignaturesConsumer stateLacksSignaturesConsumer) {
        final LatestCompleteStateNexus latestSignedState = new LatestCompleteStateNexus(stateConfig, new NoOpMetrics());
        return new SignedStateManagerTester(
                stateConfig,
                signedStateMetrics,
                latestSignedState,
                stateHasEnoughSignaturesConsumer,
                stateLacksSignaturesConsumer);
    }

    @Override
    public List<ReservedSignedState> addReservedState(
            @NonNull final ReservedSignedState reservedSignedState) {
        return processStates(super.addReservedState(reservedSignedState));
    }

    @Override
    public List<ReservedSignedState> handlePreconsensusScopedSystemTransactions(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        return processStates(super.handlePreconsensusScopedSystemTransactions(transactions));
    }

    public void handlePreconsensusSignatureTransaction(@NonNull final NodeId signerId,
            @NonNull final StateSignatureTransaction signatureTransaction) {
        handlePreconsensusScopedSystemTransactions(
                List.of(new ScopedSystemTransaction<>(signerId, signatureTransaction))
        );
    }

    @Override
    public List<ReservedSignedState> handlePostconsensusScopedSystemTransactions(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        return super.handlePostconsensusScopedSystemTransactions(transactions);
    }

    public void handlePostconsensusSignatureTransaction(@NonNull final NodeId signerId,
            @NonNull final StateSignatureTransaction transaction) {
        handlePostconsensusScopedSystemTransactions(
                List.of(new ScopedSystemTransaction<>(signerId, transaction))
        );
    }

    private List<ReservedSignedState> processStates(@Nullable final List<ReservedSignedState> states) {
        for (final ReservedSignedState state : Optional.ofNullable(states).orElse(List.of())) {
            try (state) {
                processState(state);
            }
        }
        return states;
    }

    private void processState(@NonNull final ReservedSignedState rs) {
        if (rs.get().isComplete()) {
            latestSignedState.setStateIfNewer(rs.getAndReserve("LatestCompleteStateNexus.setState"));
            stateHasEnoughSignaturesConsumer.stateHasEnoughSignatures(rs.get());
        } else {
            latestSignedState.newIncompleteState(rs.get().getRound());
            stateLacksSignaturesConsumer.stateLacksSignatures(rs.get());
        }
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
