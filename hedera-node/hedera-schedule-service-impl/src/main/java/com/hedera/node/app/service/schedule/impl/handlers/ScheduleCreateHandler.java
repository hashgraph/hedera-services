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

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody.DataOneOfType;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
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
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_CREATE}.
 */
@Singleton
public class ScheduleCreateHandler extends AbstractScheduleHandler implements TransactionHandler {
    private final ScheduleOpsUsage scheduleOpsUsage = new ScheduleOpsUsage();
    private final InstantSource instantSource;

    @Inject
    public ScheduleCreateHandler(@NonNull final InstantSource instantSource) {
        super();
        this.instantSource = instantSource;
    }

    @Override
    public void pureChecks(@Nullable final TransactionBody currentTransaction) throws PreCheckException {
        if (currentTransaction != null) {
            checkValidTransactionId(currentTransaction.transactionID());
            checkLongTermSchedulable(getValidScheduleCreateBody(currentTransaction));
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_CREATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required and optional signing keys.
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if the transaction cannot be handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final TransactionBody currentTransaction = context.body();
        final LedgerConfig ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final HederaConfig hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final long maxExpireConfig = schedulingConfig.longTermEnabled()
                ? schedulingConfig.maxExpirationFutureSeconds()
                : ledgerConfig.scheduleTxExpiryTimeSecs();
        final ScheduleCreateTransactionBody scheduleBody = getValidScheduleCreateBody(currentTransaction);
        if (scheduleBody.memo() != null && scheduleBody.memo().length() > hederaConfig.transactionMaxMemoUtf8Bytes())
            throw new PreCheckException(ResponseCodeEnum.MEMO_TOO_LONG);
        // @todo('future') add whitelist check here; mono checks very late, so we cannot check that here yet.
        // validate the schedulable transaction
        getSchedulableTransaction(currentTransaction);
        // @todo('future') This key/account validation should move to handle once we finish and validate
        //                 modularization; mono does this check too early, and may reject transactions
        //                 that should succeed.
        validatePayerAndScheduler(context, scheduleBody);
        // If we have an explicit payer account for the scheduled child transaction,
        //   add it to optional keys (it might not have signed yet).
        final Key payerKey = getKeyForPayerAccount(scheduleBody, context);
        if (payerKey != null) context.optionalKey(payerKey);
        if (scheduleBody.hasAdminKey()) {
            // If an admin key is present, it must sign the create transaction.
            context.requireKey(scheduleBody.adminKeyOrThrow());
        }
        checkSchedulableWhitelist(scheduleBody, schedulingConfig);
        final TransactionID transactionId = currentTransaction.transactionID();
        if (transactionId != null) {
            final Schedule provisionalSchedule = HandlerUtility.createProvisionalSchedule(
                    currentTransaction, instantSource.instant(), maxExpireConfig);
            final Set<Key> allRequiredKeys = allKeysForTransaction(provisionalSchedule, context);
            context.optionalKeys(allRequiredKeys);
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @throws HandleException if the transaction is not handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final Instant currentConsensusTime = context.consensusNow();
        final WritableScheduleStore scheduleStore = context.storeFactory().writableStore(WritableScheduleStore.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final LedgerConfig ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        // Note: We must store the original ScheduleCreate transaction body in the Schedule so that we can compare
        //       those bytes to any new ScheduleCreate transaction for detecting duplicate ScheduleCreate
        //       transactions.  SchedulesByEquality is the virtual map for that task
        final TransactionBody currentTransaction = context.body();
        if (currentTransaction.hasScheduleCreate()) {
            final var expirationSeconds = isLongTermEnabled
                    ? schedulingConfig.maxExpirationFutureSeconds()
                    : ledgerConfig.scheduleTxExpiryTimeSecs();
            final Schedule provisionalSchedule = HandlerUtility.createProvisionalSchedule(
                    currentTransaction, currentConsensusTime, expirationSeconds);
            checkSchedulableWhitelistHandle(provisionalSchedule, schedulingConfig);
            context.attributeValidator().validateMemo(provisionalSchedule.memo());
            context.attributeValidator()
                    .validateMemo(provisionalSchedule.scheduledTransaction().memo());
            if (provisionalSchedule.hasAdminKey()) {
                try {
                    context.attributeValidator().validateKey(provisionalSchedule.adminKeyOrThrow());
                } catch (HandleException e) {
                    throw new HandleException(ResponseCodeEnum.INVALID_ADMIN_KEY);
                }
            }
            final ResponseCodeEnum validationResult =
                    validate(provisionalSchedule, currentConsensusTime, isLongTermEnabled);
            if (validationOk(validationResult)) {
                final List<Schedule> possibleDuplicates = scheduleStore.getByEquality(provisionalSchedule);
                if (isPresentIn(context, possibleDuplicates, provisionalSchedule))
                    throw new HandleException(ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED);
                if (scheduleStore.numSchedulesInState() + 1 > schedulingConfig.maxNumber())
                    throw new HandleException(ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
                // Need to process the child transaction again, to get the *primitive* keys possibly required
                final ScheduleKeysResult requiredKeysResult = allKeysForTransaction(provisionalSchedule, context);
                final Set<Key> allRequiredKeys = requiredKeysResult.remainingRequiredKeys();
                final Set<Key> updatedSignatories = requiredKeysResult.updatedSignatories();
                final long nextId = context.entityNumGenerator().newEntityNum();
                Schedule finalSchedule =
                        HandlerUtility.completeProvisionalSchedule(provisionalSchedule, nextId, updatedSignatories);
                if (tryToExecuteSchedule(
                        context,
                        finalSchedule,
                        allRequiredKeys,
                        updatedSignatories,
                        validationResult,
                        isLongTermEnabled)) {
                    finalSchedule = HandlerUtility.markExecuted(finalSchedule, currentConsensusTime);
                }
                scheduleStore.put(finalSchedule);
                final ScheduleStreamBuilder scheduleRecords =
                        context.savepointStack().getBaseBuilder(ScheduleStreamBuilder.class);
                scheduleRecords
                        .scheduleID(finalSchedule.scheduleId())
                        .scheduledTransactionID(HandlerUtility.transactionIdForScheduled(finalSchedule));
            } else {
                throw new HandleException(validationResult);
            }
        } else {
            throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    private boolean isPresentIn(
            @NonNull final HandleContext context,
            @Nullable final List<Schedule> possibleDuplicates,
            @NonNull final Schedule provisionalSchedule) {
        if (possibleDuplicates != null)
            for (final Schedule candidate : possibleDuplicates) {
                if (compareForDuplicates(candidate, provisionalSchedule)) {
                    // Do not forget to set the ID of the existing duplicate in the receipt...
                    TransactionID scheduledTransactionID = candidate
                            .originalCreateTransaction()
                            .transactionID()
                            .copyBuilder()
                            .scheduled(true)
                            .build();
                    context.savepointStack()
                            .getBaseBuilder(ScheduleStreamBuilder.class)
                            .scheduleID(candidate.scheduleId())
                            .scheduledTransactionID(scheduledTransactionID);
                    return true;
                }
            }
        return false;
    }

    private boolean compareForDuplicates(@NonNull final Schedule candidate, @NonNull final Schedule requested) {
        return candidate.waitForExpiry() == requested.waitForExpiry()
                // @todo('9447') This should be modified to use calculated expiration once
                //               differential testing completes
                && candidate.providedExpirationSecond() == requested.providedExpirationSecond()
                && Objects.equals(candidate.memo(), requested.memo())
                && Objects.equals(candidate.adminKey(), requested.adminKey())
                // @note We should check scheduler here, but mono doesn't, so we cannot either, yet.
                && Objects.equals(candidate.scheduledTransaction(), requested.scheduledTransaction());
    }

    @NonNull
    private ScheduleCreateTransactionBody getValidScheduleCreateBody(@Nullable final TransactionBody currentTransaction)
            throws PreCheckException {
        if (currentTransaction != null) {
            final ScheduleCreateTransactionBody scheduleCreateTransaction = currentTransaction.scheduleCreate();
            if (scheduleCreateTransaction != null) {
                if (scheduleCreateTransaction.hasScheduledTransactionBody()) {
                    // this validates the schedulable transaction.
                    getSchedulableTransaction(currentTransaction);
                    return scheduleCreateTransaction;
                } else {
                    throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
                }
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    @Nullable
    private Key getKeyForPayerAccount(
            @NonNull final ScheduleCreateTransactionBody scheduleBody, @NonNull final PreHandleContext context)
            throws PreCheckException {
        if (scheduleBody.hasPayerAccountID()) {
            final AccountID payerForSchedule = scheduleBody.payerAccountIDOrThrow();
            return getKeyForAccount(context, payerForSchedule);
        } else {
            return null;
        }
    }

    @NonNull
    private static Key getKeyForAccount(@NonNull final PreHandleContext context, final AccountID accountToQuery)
            throws PreCheckException {
        final ReadableAccountStore accountStore = context.createStore(ReadableAccountStore.class);
        final Account accountData = accountStore.getAccountById(accountToQuery);
        if (accountData != null && accountData.key() != null) return accountData.key();
        else throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID);
    }

    @SuppressWarnings("DataFlowIssue")
    private void checkSchedulableWhitelistHandle(final Schedule provisionalSchedule, final SchedulingConfig config)
            throws HandleException {
        final Set<HederaFunctionality> whitelist = config.whitelist().functionalitySet();
        final SchedulableTransactionBody scheduled =
                provisionalSchedule.originalCreateTransaction().scheduleCreate().scheduledTransactionBody();
        final DataOneOfType transactionType = scheduled.data().kind();
        final HederaFunctionality functionType = HandlerUtility.functionalityForType(transactionType);
        if (!whitelist.contains(functionType)) {
            throw new HandleException(ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        }
    }

    private void validatePayerAndScheduler(
            final PreHandleContext context, final ScheduleCreateTransactionBody scheduleBody) throws PreCheckException {
        final ReadableAccountStore accountStore = context.createStore(ReadableAccountStore.class);
        final AccountID payerForSchedule = scheduleBody.payerAccountID();
        if (payerForSchedule != null) {
            final Account payer = accountStore.getAccountById(payerForSchedule);
            if (payer == null) {
                throw new PreCheckException(ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST);
            }
        }
        final AccountID schedulerId = context.payer();
        if (schedulerId != null) {
            final Account scheduler = accountStore.getAccountById(schedulerId);
            if (scheduler == null) {
                throw new PreCheckException(ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST);
            }
        }
    }

    private void checkSchedulableWhitelist(
            @NonNull final ScheduleCreateTransactionBody scheduleCreate, @NonNull final SchedulingConfig config)
            throws PreCheckException {
        final Set<HederaFunctionality> whitelist = config.whitelist().functionalitySet();
        final DataOneOfType transactionType =
                scheduleCreate.scheduledTransactionBody().data().kind();
        final HederaFunctionality functionType = HandlerUtility.functionalityForType(transactionType);
        if (!whitelist.contains(functionType)) {
            throw new PreCheckException(ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        }
    }

    private void checkLongTermSchedulable(final ScheduleCreateTransactionBody scheduleCreate) throws PreCheckException {
        // @todo('long term schedule') HIP needed?, before enabling long term schedules, add a response code for
        //       INVALID_LONG_TERM_SCHEDULE and fix this exception.
        if (scheduleCreate.waitForExpiry() && !scheduleCreate.hasExpirationTime()) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION /*INVALID_LONG_TERM_SCHEDULE*/);
        }
    }

    @NonNull
    private SchedulableTransactionBody getSchedulableTransaction(@NonNull final TransactionBody currentTransaction)
            throws PreCheckException {
        final ScheduleCreateTransactionBody scheduleBody = currentTransaction.scheduleCreate();
        if (scheduleBody != null) {
            final SchedulableTransactionBody scheduledTransaction = scheduleBody.scheduledTransactionBody();
            if (scheduledTransaction != null) {
                return scheduledTransaction;
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var op = feeContext.body();
        final var config = feeContext.configuration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final var schedulingConfig = config.getConfigData(SchedulingConfig.class);
        final var subType = (op.scheduleCreateOrThrow().hasScheduledTransactionBody()
                        && op.scheduleCreateOrThrow().scheduledTransactionBody().hasContractCall())
                ? SubType.SCHEDULE_CREATE_CONTRACT_CALL
                : SubType.DEFAULT;

        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(subType)
                .legacyCalculate(sigValueObj -> usageGiven(
                        fromPbj(op),
                        sigValueObj,
                        schedulingConfig.longTermEnabled(),
                        ledgerConfig.scheduleTxExpiryTimeSecs()));
    }

    public FeeData usageGiven(
            final com.hederahashgraph.api.proto.java.TransactionBody txn,
            final SigValueObj svo,
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
}
