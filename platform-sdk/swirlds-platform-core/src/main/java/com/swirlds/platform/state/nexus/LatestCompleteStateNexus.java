package com.swirlds.platform.state.nexus;

import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateNexus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class LatestCompleteStateNexus extends SignedStateNexus {
    private final StateConfig stateConfig;

    public LatestCompleteStateNexus(@NonNull final StateConfig stateConfig) {
        this.stateConfig = Objects.requireNonNull(stateConfig);
    }

    public void newIncompleteState(@NonNull final ReservedSignedState newState) {
        try (newState) {
            // NOTE: This logic is duplicated in SignedStateManager, but will be removed from the signed state manager
            // once its refactor is done

            // Any state older than this is unconditionally removed, even if it is the latest
            final long earliestPermittedRound = newState.get().getRound() - stateConfig.roundsToKeepForSigning() + 1;

            // Is the latest complete round older than the earliest permitted round?
            if (getRound() < earliestPermittedRound) {
                // Yes, so remove it
                clear();
            }
        }
    }
}
