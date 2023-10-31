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

package com.hedera.node.app.fees.congestion;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * An implementation for congestion multipliers that uses two types of multiplier implementation ({@link EntityUtilizationMultiplier} and a {@link ThrottleMultiplier})
 * to determine the current congestion multiplier.
 */
public class CongestionMultipliers {
    private final EntityUtilizationMultiplier entityUtilizationMultiplier;
    private final ThrottleMultiplier throttleMultiplier;

    public CongestionMultipliers(
            @NonNull final EntityUtilizationMultiplier entityUtilizationMultiplier,
            @NonNull final ThrottleMultiplier throttleMultiplier) {
        this.entityUtilizationMultiplier =
                requireNonNull(entityUtilizationMultiplier, "entityUtilizationMultiplier must not be null");
        this.throttleMultiplier = requireNonNull(throttleMultiplier, "throttleMultiplier must not be null");
    }

    /**
     * Updates the congestion multipliers for the given consensus time.
     *
     * @param consensusTime the consensus time
     */
    public void updateMultiplier(@NonNull final Instant consensusTime) {
        throttleMultiplier.updateMultiplier(consensusTime);
        entityUtilizationMultiplier.updateMultiplier(consensusTime);
    }

    /**
     * Returns the maximum congestion multiplier of the gas and entity utilization based multipliers.
     *
     * @param txnInfo transaction info needed for entity utilization based multiplier
     * @param state  the state needed for entity utilization based multiplier
     *
     * @return the max congestion multiplier
     */
    public long maxCurrentMultiplier(@NonNull final TransactionInfo txnInfo, @NonNull final HederaState state) {
        return Math.max(
                throttleMultiplier.currentMultiplier(), entityUtilizationMultiplier.currentMultiplier(txnInfo, state));
    }

    /**
     * Returns the congestion level starts for the entity utilization based multiplier.
     *
     * @return the  congestion level starts
     */
    @NonNull
    public Instant[] genericCongestionStarts() {
        return entityUtilizationMultiplier.congestionLevelStarts();
    }

    /**
     * Returns the congestion level starts for the throttle multiplier.
     *
     * @return the congestion level starts
     */
    @NonNull
    public Instant[] throttleMultiplierCongestionStarts() {
        return throttleMultiplier.congestionLevelStarts();
    }

    /**
     * Resets the congestion level starts for the entity utilization based multiplier.
     *
     * @param startTimes the congestion level starts
     */
    public void resetEntityUtilizationMultiplierStarts(@NonNull final Instant[] startTimes) {
        entityUtilizationMultiplier.resetCongestionLevelStarts(startTimes);
    }

    /**
     * Resets the congestion level starts for the throttle multiplier.
     *
     * @param startTimes the congestion level starts
     */
    public void resetThrottleMultiplierStarts(@NonNull final Instant[] startTimes) {
        throttleMultiplier.resetCongestionLevelStarts(startTimes);
    }

    /**
     * Resets the state of the underlying congestion multipliers.
     */
    public void resetExpectations() {
        throttleMultiplier.resetExpectations();
        entityUtilizationMultiplier.resetExpectations();
    }
}
