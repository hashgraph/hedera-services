package com.swirlds.platform.state;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateNexus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

public class SignedStateManagerTester extends SignedStateManager {
    private final SignedStateNexus latestSignedState;

    private SignedStateManagerTester(
            @NonNull final StateConfig stateConfig,
            @NonNull final SignedStateMetrics signedStateMetrics,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final StateLacksSignaturesConsumer stateLacksSignaturesConsumer,
            @NonNull final SignedStateNexus latestSignedState) {
        super(stateConfig,
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
        return new SignedStateManagerTester(stateConfig,
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
