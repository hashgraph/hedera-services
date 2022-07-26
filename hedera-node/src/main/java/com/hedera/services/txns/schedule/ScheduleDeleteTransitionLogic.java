/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.schedule;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class ScheduleDeleteTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ScheduleDeleteTransitionLogic.class);

    private final ScheduleStore store;
    private final TransactionContext txnCtx;
    private final SigImpactHistorian sigImpactHistorian;

    @Inject
    public ScheduleDeleteTransitionLogic(
            final ScheduleStore store,
            final TransactionContext txnCtx,
            final SigImpactHistorian sigImpactHistorian) {
        this.store = store;
        this.txnCtx = txnCtx;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void doStateTransition() {
        try {
            transitionFor(txnCtx.accessor().getTxn().getScheduleDelete(), txnCtx.consensusTime());
        } catch (final Exception e) {
            log.warn(
                    "Unhandled error while processing :: {}!",
                    txnCtx.accessor().getSignedTxnWrapper(),
                    e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private void transitionFor(
            final ScheduleDeleteTransactionBody op, final Instant consensusTime) {
        final var target = op.getScheduleID();
        final var outcome = store.deleteAt(target, consensusTime);
        if (outcome == OK) {
            sigImpactHistorian.markEntityChanged(target.getScheduleNum());
        }
        txnCtx.setStatus((outcome == OK) ? SUCCESS : outcome);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasScheduleDelete;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(final TransactionBody txnBody) {
        final var op = txnBody.getScheduleDelete();
        if (!op.hasScheduleID()) {
            return INVALID_SCHEDULE_ID;
        }
        return OK;
    }
}
