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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ScheduleSignTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ScheduleSignTransitionLogic.class);

    private final InHandleActivationHelper activationHelper;

    private final GlobalDynamicProperties properties;
    private final ScheduleExecutor executor;
    private final ScheduleStore store;
    private final TransactionContext txnCtx;

    SigMapScheduleClassifier classifier;
    SignatoryUtils.ScheduledSigningsWitness replSigningsWitness;

    @Inject
    public ScheduleSignTransitionLogic(
            final GlobalDynamicProperties properties,
            final ScheduleStore store,
            final TransactionContext txnCtx,
            final InHandleActivationHelper activationHelper,
            final ScheduleExecutor executor,
            final ScheduleProcessing scheduleProcessing) {
        this.properties = properties;
        this.store = store;
        this.txnCtx = txnCtx;
        this.executor = executor;
        this.activationHelper = activationHelper;
        classifier = scheduleProcessing.classifier;
        replSigningsWitness = scheduleProcessing.signingsWitness;
    }

    @Override
    public void doStateTransition() {
        try {
            var accessor = txnCtx.accessor();
            transitionFor(accessor.getSigMap(), accessor.getTxn().getScheduleSign());
        } catch (Exception e) {
            log.warn(
                    "Unhandled error while processing :: {}!",
                    txnCtx.accessor().getSignedTxnWrapper(),
                    e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private void transitionFor(SignatureMap sigMap, ScheduleSignTransactionBody op)
            throws InvalidProtocolBufferException {
        var scheduleId = op.getScheduleID();
        var origSchedule =
                properties.schedulingLongTermEnabled()
                        ? store.getNoError(scheduleId)
                        : store.get(scheduleId);
        if (origSchedule == null) {
            txnCtx.setStatus(INVALID_SCHEDULE_ID);
            return;
        }
        if (origSchedule.isExecuted()) {
            txnCtx.setStatus(SCHEDULE_ALREADY_EXECUTED);
            return;
        }
        if (origSchedule.isDeleted()) {
            txnCtx.setStatus(SCHEDULE_ALREADY_DELETED);
            return;
        }
        if (txnCtx.consensusTime().isAfter(origSchedule.calculatedExpirationTime().toJava())) {
            if (!properties.schedulingLongTermEnabled()) {
                txnCtx.setStatus(INVALID_SCHEDULE_ID);
                return;
            }
            txnCtx.setStatus(SCHEDULE_PENDING_EXPIRATION);
            return;
        }

        var validScheduleKeys =
                classifier.validScheduleKeys(
                        List.of(txnCtx.activePayerKey()),
                        sigMap,
                        activationHelper.currentSigsFn(),
                        activationHelper::visitScheduledCryptoSigs);
        var signingOutcome =
                replSigningsWitness.observeInScope(
                        scheduleId,
                        store,
                        validScheduleKeys,
                        activationHelper,
                        properties.schedulingLongTermEnabled()
                                && origSchedule.calculatedWaitForExpiry());

        var outcome = signingOutcome.getLeft();
        if (outcome == OK) {
            var updatedSchedule = store.get(scheduleId);
            txnCtx.setScheduledTxnId(updatedSchedule.scheduledTransactionId());

            if (Boolean.TRUE.equals(signingOutcome.getRight())) {
                outcome = executor.processImmediateExecution(scheduleId, store, txnCtx);
            }
        }
        txnCtx.setStatus(outcome == OK ? SUCCESS : outcome);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasScheduleSign;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        ScheduleSignTransactionBody op = txnBody.getScheduleSign();

        if (!op.hasScheduleID()) {
            return INVALID_SCHEDULE_ID;
        }

        // If long term scheduled transactions are enabled, the schedule must exist at the HAPI
        // level to allow deep throttle checks
        if (properties.schedulingLongTermEnabled()
                && store.getNoError(op.getScheduleID()) == null) {
            return INVALID_SCHEDULE_ID;
        }

        return OK;
    }
}
