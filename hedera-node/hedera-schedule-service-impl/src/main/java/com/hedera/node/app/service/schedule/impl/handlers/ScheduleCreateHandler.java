/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.WritableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.SchedulingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
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

    @Inject
    public ScheduleCreateHandler() {
        super();
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

    private void checkLongTermSchedulable(final ScheduleCreateTransactionBody scheduleCreate) throws PreCheckException {
        // @todo('long term schedule') HIP needed?, before enabling long term schedule, add a response code for
        //       INVALID_LONG_TERM_SCHEDULE and fix this exception.
        if (scheduleCreate.waitForExpiry() && !scheduleCreate.hasExpirationTime()) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION /*INVALID_LONG_TERM_SCHEDULE*/);
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
        final TransactionBody currentTransaction = context.body();
        final LedgerConfig config = context.configuration().getConfigData(LedgerConfig.class);
        final long maxExpiryConfig = config.scheduleTxExpiryTimeSecs();
        final ScheduleCreateTransactionBody scheduleBody = getValidScheduleCreateBody(currentTransaction);
        // validate the schedulable transaction
        getSchedulableTransaction(currentTransaction);
        // If we have an explicit payer account for the scheduled child transaction,
        //   add it to optional keys (it might not have signed yet).
        final Key payerKey = getKeyForPayerAccount(scheduleBody, context);
        if (payerKey != null) context.optionalKey(payerKey);
        if (scheduleBody.hasAdminKey()) {
            // If an admin key is present, it must sign the create transaction.
            context.requireKey(scheduleBody.adminKeyOrThrow());
        }
        final TransactionID transactionId = currentTransaction.transactionID();
        if (transactionId != null) {
            final Schedule provisionalSchedule =
                    ScheduleUtility.createProvisionalSchedule(currentTransaction, Instant.now(), maxExpiryConfig);
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
        final Instant currentConsensusTime = context.consensusNow();
        final WritableScheduleStoreImpl scheduleStore = context.writableStore(WritableScheduleStoreImpl.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        // Note: We must store the original ScheduleCreate transaction body in the Schedule so that we can compare
        //       those bytes to any new ScheduleCreate transaction for detecting duplicate ScheduleCreate
        //       transactions.  SchedulesByEquality is the virtual map for that task
        final TransactionBody currentTransaction = context.body();
        if (currentTransaction.hasScheduleCreate()) {
            final Schedule provisionalSchedule = ScheduleUtility.createProvisionalSchedule(
                    currentTransaction, currentConsensusTime, schedulingConfig.maxExpirationFutureSeconds());
            context.attributeValidator().validateMemo(provisionalSchedule.memo());
            final ResponseCodeEnum validationResult =
                    validate(provisionalSchedule, currentConsensusTime, isLongTermEnabled);
            if (validationOk(validationResult)) {
                final List<Schedule> possibleDuplicates = scheduleStore.getByEquality(provisionalSchedule);
                if (isPresentIn(possibleDuplicates, provisionalSchedule)) {
                    throw new HandleException(ResponseCodeEnum.DUPLICATE_TRANSACTION);
                }
                // Need to process the child transaction again, to get the *primitive* keys possibly required
                final ScheduleKeysResult requiredKeysResult = allKeysForTransaction(provisionalSchedule, context);
                final Set<Key> allRequiredKeys = requiredKeysResult.remainingRequiredKeys();
                final Set<Key> updatedSignatories = requiredKeysResult.updatedSignatories();
                Schedule finalSchedule = ScheduleUtility.completeProvisionalSchedule(
                        provisionalSchedule, context.newEntityNum(), updatedSignatories);
                if (tryToExecuteSchedule(
                        context,
                        finalSchedule,
                        allRequiredKeys,
                        updatedSignatories,
                        validationResult,
                        isLongTermEnabled)) {
                    finalSchedule = ScheduleUtility.markExecuted(finalSchedule, currentConsensusTime);
                }
                scheduleStore.put(finalSchedule);
            } else {
                throw new HandleException(validationResult);
            }
        } else {
            throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    private boolean isPresentIn(
            final @Nullable List<Schedule> possibleDuplicates, final @NonNull Schedule provisionalSchedule) {
        if (possibleDuplicates != null) {
            for (final Schedule candidate : possibleDuplicates) {
                if (compareForDuplicates(candidate, provisionalSchedule)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("DataFlowIssue")
    private boolean compareForDuplicates(final @NonNull Schedule candidate, final @NonNull Schedule requested) {
        return candidate.waitForExpiry() == requested.waitForExpiry()
                && Objects.equals(candidate.providedExpirationSecond(), requested.providedExpirationSecond())
                && Objects.equals(candidate.memo(), requested.memo())
                && Objects.equals(candidate.adminKey(), requested.adminKey())
                && candidate.scheduledTransaction().equals(requested.scheduledTransaction());
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
            final ReadableAccountStore accountStore = context.createStore(ReadableAccountStore.class);
            final Account accountData = accountStore.getAccountById(payerForSchedule);
            if (accountData != null && accountData.key() != null) return accountData.key();
            else throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID);
        } else {
            return null;
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
}
