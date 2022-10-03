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
package com.hedera.services.fees.charging;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.logic.AwareNodeDiligenceScreen;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Uses a (non-triggered) transaction's duplicate classification and node due diligence screen to
 * pick one of three charging policies to use for the fees due for the active transaction.
 *
 * <p>Please see {@link FeeChargingPolicy} for details.
 */
@Singleton
public class TxnChargingPolicyAgent {
    private final FeeCalculator feeCalc;
    private final FeeChargingPolicy chargingPolicy;
    private final TransactionContext txnCtx;
    private final Supplier<StateView> currentView;
    private final AwareNodeDiligenceScreen nodeDiligenceScreen;
    private final Map<TransactionID, TxnIdRecentHistory> txnHistories;

    @Inject
    public TxnChargingPolicyAgent(
            FeeCalculator feeCalc,
            FeeChargingPolicy chargingPolicy,
            TransactionContext txnCtx,
            Supplier<StateView> currentView,
            AwareNodeDiligenceScreen nodeDiligenceScreen,
            Map<TransactionID, TxnIdRecentHistory> txnHistories) {
        this.feeCalc = feeCalc;
        this.txnCtx = txnCtx;
        this.currentView = currentView;
        this.txnHistories = txnHistories;
        this.chargingPolicy = chargingPolicy;
        this.nodeDiligenceScreen = nodeDiligenceScreen;
    }

    /**
     * Returns {@code true} if {@code handleTransaction} can continue after policy application;
     * {@code false} otherwise.
     *
     * @param accessor the transaction accessor.
     * @return whether or not handleTransaction can continue after policy application.
     */
    public boolean applyPolicyFor(SwirldsTxnAccessor accessor) {
        final var now = txnCtx.consensusTime();
        var fees = feeCalc.computeFee(accessor, txnCtx.activePayerKey(), currentView.get(), now);
        final var recentHistory = txnHistories.get(accessor.getTxnId());
        var duplicity =
                (recentHistory == null)
                        ? BELIEVED_UNIQUE
                        : recentHistory.currentDuplicityFor(txnCtx.submittingSwirldsMember());

        if (nodeDiligenceScreen.nodeIgnoredDueDiligence(duplicity)) {
            chargingPolicy.applyForIgnoredDueDiligence(fees);
            return false;
        }

        if (duplicity == DUPLICATE) {
            chargingPolicy.applyForDuplicate(fees);
            txnCtx.setStatus(DUPLICATE_TRANSACTION);
            return false;
        }

        var chargingOutcome = chargingPolicy.apply(fees);
        if (chargingOutcome != OK) {
            txnCtx.setStatus(chargingOutcome);
            return false;
        }

        return true;
    }

    /**
     * Refund the service fees already charged to the payer, because the user-submitted transaction
     * was fully valid but network capacity was unavailable to satisfy it.
     */
    public void refundPayerServiceFee() {
        chargingPolicy.refundPayerServiceFee();
    }
}
