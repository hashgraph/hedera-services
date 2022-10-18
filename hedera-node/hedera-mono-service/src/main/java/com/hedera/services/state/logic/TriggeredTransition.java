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

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.utils.EntityNum.fromScheduleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.FeeChargingPolicy;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.store.schedule.ScheduleStore;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TriggeredTransition implements Runnable {
    private static final Logger log = LogManager.getLogger(TriggeredTransition.class);

    private final StateView currentView;
    private final FeeCalculator fees;
    private final FeeChargingPolicy chargingPolicy;
    private final NetworkCtxManager networkCtxManager;
    private final RequestedTransition requestedTransition;
    private final TransactionContext txnCtx;
    private final NetworkUtilization networkUtilization;
    private final SigImpactHistorian sigImpactHistorian;
    private final ScheduleStore scheduleStore;

    @Inject
    public TriggeredTransition(
            final StateView currentView,
            final FeeCalculator fees,
            final FeeChargingPolicy chargingPolicy,
            final TransactionContext txnCtx,
            final SigImpactHistorian sigImpactHistorian,
            final NetworkCtxManager networkCtxManager,
            final RequestedTransition requestedTransition,
            final ScheduleStore scheduleStore,
            final NetworkUtilization networkUtilization) {
        this.currentView = currentView;
        this.fees = fees;
        this.chargingPolicy = chargingPolicy;
        this.txnCtx = txnCtx;
        this.networkCtxManager = networkCtxManager;
        this.networkUtilization = networkUtilization;
        this.scheduleStore = scheduleStore;
        this.requestedTransition = requestedTransition;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void run() {
        final var accessor = txnCtx.accessor();
        final var now = txnCtx.consensusTime();

        networkCtxManager.advanceConsensusClockTo(now);

        var payerKeyForFeeCompute = txnCtx.activePayerKey();

        if (accessor.isTriggeredTxn() && accessor.getScheduleRef() != null) {
            var markExecutedOutcome = scheduleStore.markAsExecuted(accessor.getScheduleRef(), now);
            if (markExecutedOutcome != OK) {
                txnCtx.setStatus(markExecutedOutcome);
                log.error(
                        "Marking schedule {} as executed failed! {}",
                        accessor.getScheduleRef(),
                        markExecutedOutcome);
                return;
            }
            sigImpactHistorian.markEntityChanged(
                    fromScheduleId(accessor.getScheduleRef()).longValue());

            // for scheduled transactions, we have always validated the payer key before we get here
            txnCtx.payerSigIsKnownActive();

            // for scheduled transactions the payer key size/price is already paid before we get
            // here
            payerKeyForFeeCompute = EMPTY_KEY;
        }

        networkUtilization.trackUserTxn(accessor, now);

        final var fee = fees.computeFee(accessor, payerKeyForFeeCompute, currentView, now);
        final var chargingOutcome = chargingPolicy.applyForTriggered(fee);
        if (chargingOutcome != OK) {
            txnCtx.setStatus(chargingOutcome);
            return;
        }

        if (networkUtilization.screenForAvailableCapacity()) {
            requestedTransition.finishFor(accessor);
        }
    }
}
