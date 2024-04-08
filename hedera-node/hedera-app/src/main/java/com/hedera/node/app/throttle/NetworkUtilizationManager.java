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

package com.hedera.node.app.throttle;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;

/**
 * Interface which purpose is to do the work of tracking network utilization (and its impact on
 * congestion pricing).
 */
public interface NetworkUtilizationManager {

    /**
     * Updates the throttle usage and congestion pricing using the given transaction.
     *
     * @param txnInfo - the transaction to use for updating the network utilization.
     * @param consensusTime - the consensus time of the transaction.
     * @param state - the state of the node.
     */
    void trackTxn(
            @NonNull final TransactionInfo txnInfo,
            @NonNull final Instant consensusTime,
            @NonNull final HederaState state);

    /**
     * Updates the throttle usage and congestion pricing for cases where the transaction is not valid, but we want to track the fee payments related to it.
     *
     * @param payer - the payer of the transaction.
     * @param consensusNow - the consensus time of the transaction.
     * @param state - the state of the node.
     */
    void trackFeePayments(@NonNull final Instant consensusNow, @NonNull final HederaState state);

    /**
     * Indicates whether the last transaction was throttled by gas.
     *
     * @return true if the last transaction was throttled by gas; false otherwise.
     */
    boolean wasLastTxnGasThrottled();

    /**
     * Leaks the gas amount previously reserved for the given transaction.
     *
     * @param txnInfo the transaction to leak the gas for
     * @param value the amount of gas to leak
     */
    void leakUnusedGasPreviouslyReserved(@NonNull final TransactionInfo txnInfo, final long value);

    /**
     * Updates the throttle requirements for the given transaction and returns whether the transaction
     * should be throttled for the current time(Instant.now).
     *
     * @param txnInfo the transaction to update the throttle requirements for
     * @param state the current state of the node
     * @param consensusTime the consensus time
     * @return whether the transaction should be throttled
     */
    boolean shouldThrottle(
            @NonNull final TransactionInfo txnInfo,
            @NonNull final HederaState state,
            @NonNull final Instant consensusTime);

    /**
     * Verifies if the throttle in this operation context has enough capacity to handle the given number of the
     * given function at the given time. (The time matters because we want to consider how much
     * will have leaked between now and that time.)
     *
     * @param n the number of the given function
     * @param function the function
     * @return true if the system should throttle the given number of the given function
     * at the instant for which throttling should be calculated
     */
    boolean shouldThrottleNOfUnscaled(int n, @NonNull HederaFunctionality function, @NonNull Instant consensusTime);

    /**
     * Returns a list of snapshots of the current usage of all active throttles.
     * @return the active snapshots
     */
    List<DeterministicThrottle.UsageSnapshot> getUsageSnapshots();

    /**
     * Resets the current usage of all active throttles to the given snapshots.
     *
     * @param snapshots the snapshots to reset to
     */
    void resetUsageThrottlesTo(List<DeterministicThrottle.UsageSnapshot> snapshots);
}
