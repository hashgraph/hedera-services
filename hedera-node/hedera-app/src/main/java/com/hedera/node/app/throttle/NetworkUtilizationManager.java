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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Interface which purpose is to do the work of tracking network utilization (and its impact on
 * congestion pricing).
 */
public interface NetworkUtilizationManager {

    /*
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

    /*
     * Updates the throttle usage and congestion pricing for cases where the transaction is not valid, but we want to track the fee payments related to it.
     *
     * @param payer - the payer of the transaction.
     * @param consensusNow - the consensus time of the transaction.
     * @param state - the state of the node.
     */
    void trackFeePayments(
            @NonNull AccountID payer, @NonNull final Instant consensusNow, @NonNull final HederaState state);

    /*
     * Indicates whether the last transaction was throttled by gas.
     *
     * @return true if the last transaction was throttled by gas; false otherwise.
     */
    boolean wasLastTxnGasThrottled();

    /*
     * Leaks the gas amount previously reserved for the given transaction.
     *
     * @param txnInfo the transaction to leak the gas for
     * @param value the amount of gas to leak
     */
    void leakUnusedGasPreviouslyReserved(@NonNull final TransactionInfo txnInfo, final long value);

    /*
     * Resets the throttle usage and congestion multiplier from the given state.
     *
     * @param state the state of the node
     */
    void resetFrom(@NonNull final HederaState state);

    /*
     * Saves the throttle usage and congestion multiplier to the given state.
     *
     * @param state the state of the node
     */
    void saveTo(@NonNull final HederaState state);
}
