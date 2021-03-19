package com.hedera.services.txns.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleSignTransitionLogic extends ScheduleReadyForExecution implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ScheduleSignTransitionLogic.class);

    private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

    private final InHandleActivationHelper activationHelper;

    ExecutionProcessor executor = this::processExecution;
    SigMapScheduleClassifier classifier = new SigMapScheduleClassifier();
    SignatoryUtils.ScheduledSigningsWitness replSigningsWitness = SignatoryUtils::witnessScoped;

    public ScheduleSignTransitionLogic(
            ScheduleStore store,
            TransactionContext txnCtx,
            InHandleActivationHelper activationHelper
    ) {
        super(store, txnCtx);
        this.activationHelper = activationHelper;
    }

    @Override
    public void doStateTransition() {
        try {
        	var accessor = txnCtx.accessor();
            transitionFor(accessor.getSigMap(), accessor.getTxn().getScheduleSign());
        } catch (Exception e) {
            log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private void transitionFor(
            SignatureMap sigMap,
            ScheduleSignTransactionBody op
    ) throws InvalidProtocolBufferException {
        var validScheduleKeys = classifier.validScheduleKeys(
                txnCtx.activePayerKey(),
                sigMap,
                activationHelper.currentSigsFn(),
                activationHelper::visitScheduledCryptoSigs);
        var scheduleId = op.getScheduleID();
        var signingOutcome = replSigningsWitness.observeInScope(scheduleId, store, validScheduleKeys, activationHelper);

        var outcome = signingOutcome.getLeft();
        if (outcome == OK) {
            var updatedSchedule = store.get(scheduleId);
            txnCtx.setScheduledTxnId(updatedSchedule.scheduledTransactionId());
            if (signingOutcome.getRight()) {
                outcome = executor.doProcess(scheduleId);
            }
        }
		txnCtx.setStatus(outcome == OK ? SUCCESS : outcome);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasScheduleSign;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
        return SYNTAX_CHECK;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        ScheduleSignTransactionBody op = txnBody.getScheduleSign();

        return (op.hasScheduleID()) ? OK : INVALID_SCHEDULE_ID;
    }
}
