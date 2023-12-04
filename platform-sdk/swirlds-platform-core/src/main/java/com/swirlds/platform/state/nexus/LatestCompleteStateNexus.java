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

package com.swirlds.platform.state.nexus;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * A nexus that holds the latest complete signed state.
 */
public class LatestCompleteStateNexus extends SignedStateNexus {
    private static final RunningAverageMetric.Config AVG_ROUND_SUPERMAJORITY_CONFIG = new RunningAverageMetric.Config(
            PLATFORM_CATEGORY, "roundSup")
            .withDescription("latest round with state signed by a supermajority")
            .withUnit("round");

    private final StateConfig stateConfig;

    /**
     * Create a new nexus that holds the latest complete signed state with no metrics.
     *
     * @param stateConfig the state configuration
     */
    public LatestCompleteStateNexus(@NonNull final StateConfig stateConfig) {
        this(stateConfig, null);
    }

    /**
     * Create a new nexus that holds the latest complete signed state.
     *
     * @param stateConfig the state configuration
     * @param metrics     the metrics object to update, or null if no metrics should be updated
     */
    public LatestCompleteStateNexus(@NonNull final StateConfig stateConfig, @Nullable final Metrics metrics) {
        this.stateConfig = Objects.requireNonNull(stateConfig);
        if (metrics != null) {
            final RunningAverageMetric avgRoundSupermajority =
                    metrics.getOrCreate(AVG_ROUND_SUPERMAJORITY_CONFIG);
            metrics.addUpdater(() -> avgRoundSupermajority.update(getRound()));
        }
    }

    /**
     * Notify the nexus that a new signed state has been created. This is useful for the nexus to know when it should
     * clear the latest complete state. This is used so that we don't hold the latest complete state forever in case we
     * have trouble gathering signatures.
     *
     * @param newState a new signed state that is not yet complete
     */
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
