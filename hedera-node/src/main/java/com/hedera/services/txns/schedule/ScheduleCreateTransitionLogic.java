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

import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.txns.validation.PureValidation;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ScheduleCreateTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ScheduleCreateTransitionLogic.class);

    private static final EnumSet<ResponseCodeEnum> ACCEPTABLE_SIGNING_OUTCOMES =
            EnumSet.of(OK, NO_NEW_VALID_SIGNATURES);

    private final UsageLimits usageLimits;
    private final GlobalDynamicProperties properties;
    private final OptionValidator validator;
    private final SigImpactHistorian sigImpactHistorian;
    private final InHandleActivationHelper activationHelper;
    private final ScheduleExecutor executor;
    private final ScheduleStore store;
    private final TransactionContext txnCtx;
    private final ScheduleProcessing scheduleProcessing;

    SigMapScheduleClassifier classifier;
    SignatoryUtils.ScheduledSigningsWitness signingsWitness;

    @Inject
    public ScheduleCreateTransitionLogic(
            final UsageLimits usageLimits,
            final GlobalDynamicProperties properties,
            final ScheduleStore store,
            final TransactionContext txnCtx,
            final InHandleActivationHelper activationHelper,
            final OptionValidator validator,
            final ScheduleExecutor executor,
            final SigImpactHistorian sigImpactHistorian,
            final ScheduleProcessing scheduleProcessing) {
        this.usageLimits = usageLimits;
        this.properties = properties;
        this.store = store;
        this.txnCtx = txnCtx;
        this.activationHelper = activationHelper;
        this.validator = validator;
        this.executor = executor;
        this.sigImpactHistorian = sigImpactHistorian;
        this.scheduleProcessing = scheduleProcessing;
        classifier = scheduleProcessing.classifier;
        signingsWitness = scheduleProcessing.signingsWitness;
    }

    @Override
    public void doStateTransition() {
        try {
            final var accessor = txnCtx.accessor();
            transitionFor(accessor.getTxnBytes(), accessor.getSigMap());
        } catch (Exception e) {
            log.warn(
                    "Unhandled error while processing :: {}!",
                    txnCtx.accessor().getSignedTxnWrapper(),
                    e);
            abortWith(FAIL_INVALID);
        }
    }

    private void transitionFor(final byte[] bodyBytes, final SignatureMap sigMap)
            throws InvalidProtocolBufferException {
        final var idSchedulePair = store.lookupSchedule(bodyBytes);
        @Nullable final var existingScheduleId = idSchedulePair.getLeft();
        final var schedule = idSchedulePair.getRight();
        if (null != existingScheduleId) {
            completeContextWith(existingScheduleId, schedule, IDENTICAL_SCHEDULE_ALREADY_CREATED);
            return;
        }
        if (!usageLimits.areCreatableSchedules(1)) {
            abortWith(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
            return;
        }

        final var result = store.createProvisionally(schedule, fromJava(txnCtx.consensusTime()));
        final var scheduleId = result.created();
        if (null == scheduleId) {
            abortWith(result.status());
            return;
        }

        final var throttleResult =
                scheduleProcessing.checkFutureThrottlesForCreate(scheduleId, schedule);

        if (throttleResult != OK) {
            abortWith(throttleResult);
            return;
        }

        final var payerKey = txnCtx.activePayerKey();
        final var topLevelKeys =
                schedule.adminKey().map(ak -> List.of(payerKey, ak)).orElse(List.of(payerKey));
        final var validScheduleKeys =
                classifier.validScheduleKeys(
                        topLevelKeys,
                        sigMap,
                        activationHelper.currentSigsFn(),
                        activationHelper::visitScheduledCryptoSigs);
        final var signingOutcome =
                signingsWitness.observeInScope(
                        scheduleId,
                        store,
                        validScheduleKeys,
                        activationHelper,
                        properties.schedulingLongTermEnabled()
                                && schedule.calculatedWaitForExpiry());

        if (!ACCEPTABLE_SIGNING_OUTCOMES.contains(signingOutcome.getLeft())) {
            abortWith(signingOutcome.getLeft());
            return;
        }

        if (store.isCreationPending()) {
            store.commitCreation();
        }

        var finalOutcome = OK;
        if (Boolean.TRUE.equals(signingOutcome.getRight())) {
            finalOutcome = executor.processImmediateExecution(scheduleId, store, txnCtx);
        }
        completeContextWith(scheduleId, schedule, finalOutcome == OK ? SUCCESS : finalOutcome);
        sigImpactHistorian.markEntityChanged(scheduleId.getScheduleNum());
        usageLimits.refreshSchedules();
    }

    private void completeContextWith(
            ScheduleID scheduleID, ScheduleVirtualValue schedule, ResponseCodeEnum finalOutcome) {
        txnCtx.setCreated(scheduleID);
        txnCtx.setScheduledTxnId(schedule.scheduledTransactionId());
        txnCtx.setStatus(finalOutcome);
    }

    private void abortWith(ResponseCodeEnum cause) {
        if (store.isCreationPending()) {
            store.rollbackCreation();
        }
        txnCtx.setStatus(cause);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasScheduleCreate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(final TransactionBody txnBody) {
        var validity = OK;
        final var op = txnBody.getScheduleCreate();
        if (op.hasAdminKey()) {
            validity = PureValidation.checkKey(op.getAdminKey(), INVALID_ADMIN_KEY);
        }
        if (validity != OK) {
            return validity;
        }

        validity = validator.memoCheck(op.getMemo());
        if (validity != OK) {
            return validity;
        }
        validity = validator.memoCheck(op.getScheduledTransactionBody().getMemo());
        if (validity != OK) {
            return validity;
        }

        return OK;
    }
}
