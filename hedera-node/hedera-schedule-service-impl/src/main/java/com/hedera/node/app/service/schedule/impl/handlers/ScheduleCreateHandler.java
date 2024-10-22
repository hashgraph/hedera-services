/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hedera.hapi.node.base.SubType.DEFAULT;
import static com.hedera.hapi.node.base.SubType.SCHEDULE_CREATE_CONTRACT_CALL;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.createProvisionalSchedule;
import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.functionalityForType;
import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.transactionIdForScheduled;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.schedule.ScheduleOpsUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.schedule.ScheduleStreamBuilder;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.SchedulingConfig;
import com.hederahashgraph.api.proto.java.FeeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_CREATE}.
 */
@Singleton
public class ScheduleCreateHandler extends ScheduleManager implements TransactionHandler {
    private final ScheduleOpsUsage scheduleOpsUsage = new ScheduleOpsUsage();
    private final InstantSource instantSource;

    @Inject
    public ScheduleCreateHandler(@NonNull final InstantSource instantSource) {
        this.instantSource = instantSource;
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody body) throws PreCheckException {
        requireNonNull(body);
        validateTruePreCheck(body.hasScheduleCreate(), INVALID_TRANSACTION_BODY);
        final var op = body.scheduleCreateOrThrow();
        validateTruePreCheck(op.hasScheduledTransactionBody(), INVALID_TRANSACTION);
        // (FUTURE) Add a dedicated response code for an op waiting for an unspecified expiration time
        validateFalsePreCheck(op.waitForExpiry() && !op.hasExpirationTime(), INVALID_TRANSACTION);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var body = context.body();
        // We ensure this exists in pureChecks()
        final var op = body.scheduleCreateOrThrow();
        final var config = context.configuration();
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        validateTruePreCheck(op.memo().length() <= hederaConfig.transactionMaxMemoUtf8Bytes(), MEMO_TOO_LONG);
        // For backward compatibility, use ACCOUNT_ID_DOES_NOT_EXIST for a nonexistent designated payer
        if (op.hasPayerAccountID()) {
            final var accountStore = context.createStore(ReadableAccountStore.class);
            final var payer = accountStore.getAccountById(op.payerAccountIDOrThrow());
            mustExist(payer, ACCOUNT_ID_DOES_NOT_EXIST);
        }
        final var schedulingConfig = config.getConfigData(SchedulingConfig.class);
        validateTruePreCheck(
                isAllowedFunction(op.scheduledTransactionBodyOrThrow(), schedulingConfig),
                SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        // If an admin key is present, it must sign
        if (op.hasAdminKey()) {
            context.requireKey(op.adminKeyOrThrow());
        }
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final long maxLifetime = schedulingConfig.longTermEnabled()
                ? schedulingConfig.maxExpirationFutureSeconds()
                : ledgerConfig.scheduleTxExpiryTimeSecs();
        final var schedule = createProvisionalSchedule(body, instantSource.instant(), maxLifetime);
        final var transactionKeys = getRequiredKeys(schedule, context::allKeysForTransaction);
        // If the schedule payer inherits from the ScheduleCreate, it is already in the required keys
        if (op.hasPayerAccountID()) {
            context.optionalKey(transactionKeys.payerKey());
        }
        // Any required non-payer key may optionally provide its signature with the ScheduleCreate
        context.optionalKeys(transactionKeys.requiredNonPayerKeys());
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);

        final var schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var expirationSeconds = isLongTermEnabled
                ? schedulingConfig.maxExpirationFutureSeconds()
                : ledgerConfig.scheduleTxExpiryTimeSecs();
        final var consensusNow = context.consensusNow();
        final var provisionalSchedule = createProvisionalSchedule(context.body(), consensusNow, expirationSeconds, isLongTermEnabled);
        validateTrue(
                isAllowedFunction(provisionalSchedule.scheduledTransactionOrThrow(), schedulingConfig),
                SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        context.attributeValidator().validateMemo(provisionalSchedule.memo());
        context.attributeValidator()
                .validateMemo(provisionalSchedule.scheduledTransactionOrThrow().memo());
        if (provisionalSchedule.hasAdminKey()) {
            try {
                context.attributeValidator().validateKey(provisionalSchedule.adminKeyOrThrow());
            } catch (HandleException e) {
                throw new HandleException(INVALID_ADMIN_KEY);
            }
        }
        final var validationResult = validate(provisionalSchedule, consensusNow, isLongTermEnabled);
        validateTrue(isMaybeExecutable(validationResult), validationResult);

        // Note that we must store the original ScheduleCreate transaction body in the Schedule so
        // we can compare those bytes to any new ScheduleCreate transaction for detecting duplicate
        // ScheduleCreate transactions. SchedulesByEquality is the virtual map for that task.
        final var scheduleStore = context.storeFactory().writableStore(WritableScheduleStore.class);
        final var possibleDuplicates = scheduleStore.getByEquality(provisionalSchedule);
        final var duplicate = maybeDuplicate(provisionalSchedule, possibleDuplicates);
        if (duplicate != null) {
            final var scheduledTxnId = duplicate
                    .originalCreateTransactionOrThrow()
                    .transactionIDOrThrow()
                    .copyBuilder()
                    .scheduled(true)
                    .build();
            context.savepointStack()
                    .getBaseBuilder(ScheduleStreamBuilder.class)
                    .scheduleID(duplicate.scheduleId())
                    .scheduledTransactionID(scheduledTxnId);
            throw new HandleException(IDENTICAL_SCHEDULE_ALREADY_CREATED);
        }
        validateTrue(
                scheduleStore.numSchedulesInState() + 1 <= schedulingConfig.maxNumber(),
                MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        // With all validations done, we check if the new schedule is already executable
        final var transactionKeys = getTransactionKeysOrThrow(provisionalSchedule, context::allKeysForTransaction);
        final var requiredKeys = allRequiredKeys(transactionKeys);
        final var signatories =
                newSignatories(context.keyVerifier().signingCryptoKeys(), Collections.emptyList(), requiredKeys);
        final var schedulingTxnId =
                provisionalSchedule.originalCreateTransactionOrThrow().transactionIDOrThrow();
        final var schedulerId = schedulingTxnId.accountIDOrThrow();
        final var scheduleId = ScheduleID.newBuilder()
                .shardNum(schedulerId.shardNum())
                .realmNum(schedulerId.realmNum())
                .scheduleNum(context.entityNumGenerator().newEntityNum())
                .build();
        var schedule = provisionalSchedule
                .copyBuilder()
                .scheduleId(scheduleId)
                .schedulerAccountId(schedulerId)
                .signatories(signatories)
                .build();
        if (tryToExecuteSchedule(context, schedule, requiredKeys, validationResult, isLongTermEnabled)) {
            schedule = markedExecuted(schedule, consensusNow);
        }
        scheduleStore.put(schedule);
        context.savepointStack()
                .getBaseBuilder(ScheduleStreamBuilder.class)
                .scheduleID(schedule.scheduleId())
                .scheduledTransactionID(transactionIdForScheduled(schedule));
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var body = feeContext.body();
        final var config = feeContext.configuration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final var schedulingConfig = config.getConfigData(SchedulingConfig.class);
        final var subType = body.scheduleCreateOrElse(ScheduleCreateTransactionBody.DEFAULT)
                        .scheduledTransactionBodyOrElse(SchedulableTransactionBody.DEFAULT)
                        .hasContractCall()
                ? SCHEDULE_CREATE_CONTRACT_CALL
                : DEFAULT;
        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(subType)
                .legacyCalculate(sigValueObj -> usageGiven(
                        fromPbj(body),
                        sigValueObj,
                        schedulingConfig.longTermEnabled(),
                        ledgerConfig.scheduleTxExpiryTimeSecs()));
    }

    private @NonNull FeeData usageGiven(
            @NonNull final com.hederahashgraph.api.proto.java.TransactionBody txn,
            @NonNull final SigValueObj svo,
            final boolean longTermEnabled,
            final long scheduledTxExpiryTimeSecs) {
        final var op = txn.getScheduleCreate();
        final var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        final long lifetimeSecs;
        if (op.hasExpirationTime() && longTermEnabled) {
            lifetimeSecs = Math.max(
                    0L,
                    op.getExpirationTime().getSeconds()
                            - txn.getTransactionID().getTransactionValidStart().getSeconds());
        } else {
            lifetimeSecs = scheduledTxExpiryTimeSecs;
        }
        return scheduleOpsUsage.scheduleCreateUsage(txn, sigUsage, lifetimeSecs);
    }

    private @Nullable Schedule maybeDuplicate(
            @NonNull final Schedule schedule, @Nullable final List<Schedule> duplicates) {
        if (duplicates == null) {
            return null;
        }
        for (final var duplicate : duplicates) {
            if (areIdentical(duplicate, schedule)) {
                return duplicate;
            }
        }
        return null;
    }

    private boolean areIdentical(@NonNull final Schedule candidate, @NonNull final Schedule requested) {
        return candidate.waitForExpiry() == requested.waitForExpiry()
                && candidate.providedExpirationSecond() == requested.providedExpirationSecond()
                && Objects.equals(candidate.memo(), requested.memo())
                && Objects.equals(candidate.adminKey(), requested.adminKey())
                // @note We should check scheduler here, but mono doesn't, so we cannot either, yet.
                && Objects.equals(candidate.scheduledTransaction(), requested.scheduledTransaction());
    }

    private boolean isAllowedFunction(
            @NonNull final SchedulableTransactionBody body, @NonNull final SchedulingConfig config) {
        final var scheduledFunctionality = functionalityForType(body.data().kind());
        return config.whitelist().functionalitySet().contains(scheduledFunctionality);
    }
}
