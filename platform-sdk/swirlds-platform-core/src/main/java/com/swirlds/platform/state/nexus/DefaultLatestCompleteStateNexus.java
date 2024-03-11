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

package com.swirlds.platform.state.nexus;

import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * The default implementation of {@link LatestCompleteStateNexus}.
 */
public class DefaultLatestCompleteStateNexus implements LatestCompleteStateNexus {
    private static final RunningAverageMetric.Config AVG_ROUND_SUPERMAJORITY_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "roundSup")
            .withDescription("latest round with state signed by a supermajority")
            .withUnit("round");

    private final StateConfig stateConfig;
    private ReservedSignedState currentState;

    /**
     * Create a new nexus that holds the latest complete signed state.
     *
     * @param stateConfig the state configuration
     * @param metrics     the metrics object to update
     */
    public DefaultLatestCompleteStateNexus(@NonNull final StateConfig stateConfig, @NonNull final Metrics metrics) {
        this.stateConfig = Objects.requireNonNull(stateConfig);
        Objects.requireNonNull(metrics);

        final RunningAverageMetric avgRoundSupermajority = metrics.getOrCreate(AVG_ROUND_SUPERMAJORITY_CONFIG);
        metrics.addUpdater(() -> avgRoundSupermajority.update(getRound()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setState(@Nullable final ReservedSignedState reservedSignedState) {
        if (currentState != null) {
            currentState.close();
        }
        currentState = reservedSignedState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setStateIfNewer(@NonNull final ReservedSignedState reservedSignedState) {
        if (reservedSignedState.isNotNull()
                && getRound() < reservedSignedState.get().getRound()) {
            setState(reservedSignedState);
        } else {
            reservedSignedState.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void newIncompleteState(@NonNull final Long newStateRound) {
        // Any state older than this is unconditionally removed, even if it is the latest
        final long earliestPermittedRound = newStateRound - stateConfig.roundsToKeepForSigning() + 1;

        // Is the latest complete round older than the earliest permitted round?
        if (getRound() < earliestPermittedRound) {
            // Yes, so remove it
            clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public synchronized ReservedSignedState getState(@NonNull final String reason) {
        if (currentState == null) {
            return null;
        }
        return currentState.tryGetAndReserve(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized long getRound() {
        if (currentState == null) {
            return ConsensusConstants.ROUND_UNDEFINED;
        }
        return currentState.get().getRound();
    }
}
