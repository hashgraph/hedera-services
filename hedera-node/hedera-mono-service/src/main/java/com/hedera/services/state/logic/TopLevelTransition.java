/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Applies a series of screens to the user transaction submitted via HAPI and now initialized at
 * consensus in the {@link TransactionContext}, aborting the requested state transition if any
 * screen fails. Accumulates various side-effects in other infrastructure components along the way.
 * (For example, the payer solvency screen charges fees as a side-effect.)
 *
 * <p>In order, the screens roughly test for:
 *
 * <ol>
 *   <li>Validity of the supplied cryptographic signatures and activation of the payer's Hedera key.
 *   <li>Node due diligence, transaction chronology, and payer solvency.
 *   <li>Activation of non-payer Hedera keys linked to the transaction.
 *   <li>Availability of network capacity (for smart contract operations).
 * </ol>
 */
@Singleton
public class TopLevelTransition implements Runnable {
    private final NetworkCtxManager networkCtxManager;
    private final TransactionContext txnCtx;
    private final NonPayerKeysScreen nonPayerKeysScreen;
    private final RequestedTransition requestedTransition;
    private final SigsAndPayerKeyScreen sigsAndPayerKeyScreen;
    private final TxnChargingPolicyAgent chargingPolicyAgent;
    private final NetworkUtilization networkUtilization;

    @Inject
    public TopLevelTransition(
            final SigsAndPayerKeyScreen sigsAndPayerKeyScreen,
            final NetworkCtxManager networkCtxManager,
            final RequestedTransition requestedTransition,
            final TransactionContext txnCtx,
            final NonPayerKeysScreen nonPayerKeysScreen,
            final NetworkUtilization networkUtilization,
            final TxnChargingPolicyAgent chargingPolicyAgent) {
        this.txnCtx = txnCtx;
        this.networkCtxManager = networkCtxManager;
        this.networkUtilization = networkUtilization;
        this.chargingPolicyAgent = chargingPolicyAgent;
        this.sigsAndPayerKeyScreen = sigsAndPayerKeyScreen;
        this.nonPayerKeysScreen = nonPayerKeysScreen;
        this.requestedTransition = requestedTransition;
    }

    @Override
    public void run() {
        final var accessor = txnCtx.swirldsTxnAccessor();
        final var now = txnCtx.consensusTime();
        networkCtxManager.advanceConsensusClockTo(now);

        final var sigStatus = sigsAndPayerKeyScreen.applyTo(accessor);
        // We update the network utilization before we compute and charge fees b/c
        // network utilization determines the congestion pricing multiplier; so this
        // is the simplest way to guarantee a reconnected node will apply the same
        // multiplier as nodes that were never disconnected
        // (c.f. https://github.com/hashgraph/hedera-services/issues/2936)
        if (sigStatus == OK) {
            networkUtilization.trackUserTxn(accessor, now);
        } else {
            // If the signature status isn't ok, only work done will be fee charging
            networkUtilization.trackFeePayments(now);
        }
        if (!chargingPolicyAgent.applyPolicyFor(accessor)) {
            return;
        }
        if (!nonPayerKeysScreen.reqKeysAreActiveGiven(sigStatus)) {
            return;
        }
        if (networkUtilization.screenForAvailableCapacity()) {
            requestedTransition.finishFor(accessor);
        }
    }
}
