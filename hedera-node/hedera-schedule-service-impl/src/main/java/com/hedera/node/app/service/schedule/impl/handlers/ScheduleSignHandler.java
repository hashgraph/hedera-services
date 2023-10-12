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
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ScheduleRecordBuilder;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.SchedulingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_SIGN}.
 */
@Singleton
@SuppressWarnings("OverlyCoupledClass")
public class ScheduleSignHandler extends AbstractScheduleHandler implements TransactionHandler {

    @Inject
    public ScheduleSignHandler() {
        super();
    }

    @Override
    public void pureChecks(@Nullable final TransactionBody currentTransaction) throws PreCheckException {
        if (currentTransaction != null) {
            checkValidTransactionId(currentTransaction.transactionID());
            getValidScheduleSignBody(currentTransaction);
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_SIGN} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if the transaction cannot be handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final ReadableScheduleStore scheduleStore = context.createStore(ReadableScheduleStore.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        final TransactionBody currentTransaction = context.body();
        final ScheduleSignTransactionBody scheduleSignTransaction = getValidScheduleSignBody(currentTransaction);
        if (scheduleSignTransaction.scheduleID() != null) {
            final Schedule scheduleData =
                    preValidate(scheduleStore, isLongTermEnabled, scheduleSignTransaction.scheduleID());
            final AccountID payerAccount = scheduleData.payerAccountId();
            // Note, payer should never be null, but we have to check anyway, because Sonar doesn't know better.
            if (payerAccount != null) {
                final ReadableAccountStore accountStore = context.createStore(ReadableAccountStore.class);
                final Account payer = accountStore.getAccountById(payerAccount);
                if (payer != null) {
                    final Key payerKey = payer.key();
                    if (payerKey != null) context.optionalKey(payerKey);
                }
            }
            try {
                final Set<Key> allKeysNeeded = allKeysForTransaction(scheduleData, context);
                context.optionalKeys(allKeysNeeded);
            } catch (HandleException translated) {
                throw new PreCheckException(translated.getStatus());
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
        // context now has all of the keys required by the scheduled transaction in optional keys
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @throws HandleException if the transaction is not handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @SuppressWarnings({"FeatureEnvy", "OverlyCoupledMethod"})
    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final Instant currentConsensusTime = context.consensusNow();
        final WritableScheduleStore scheduleStore = context.writableStore(WritableScheduleStore.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        final TransactionBody currentTransaction = context.body();
        if (currentTransaction.hasScheduleSign()) {
            final ScheduleSignTransactionBody signTransaction = currentTransaction.scheduleSignOrThrow();
            final ScheduleID idToSign = signTransaction.scheduleID();
            final Schedule scheduleData = scheduleStore.get(idToSign);
            final ResponseCodeEnum validationResult = validate(scheduleData, currentConsensusTime, isLongTermEnabled);
            if (validationOk(validationResult)) {
                final Schedule scheduleToSign = scheduleStore.getForModify(idToSign);
                // ID to sign will never be null here, but sonar needs this check...
                if (scheduleToSign != null && idToSign != null) {
                    final SchedulableTransactionBody schedulableTransaction = scheduleToSign.scheduledTransaction();
                    if (schedulableTransaction != null) {
                        final ScheduleKeysResult requiredKeysResult = allKeysForTransaction(scheduleToSign, context);
                        final Set<Key> allRequiredKeys = requiredKeysResult.remainingRequiredKeys();
                        final Set<Key> updatedSignatories = requiredKeysResult.updatedSignatories();
                        if (tryToExecuteSchedule(
                                context,
                                scheduleToSign,
                                allRequiredKeys,
                                updatedSignatories,
                                validationResult,
                                isLongTermEnabled)) {
                            scheduleStore.put(HandlerUtility.replaceSignatoriesAndMarkExecuted(
                                    scheduleToSign, updatedSignatories, currentConsensusTime));
                        } else {
                            scheduleStore.put(HandlerUtility.replaceSignatories(scheduleToSign, updatedSignatories));
                        }
                        final ScheduleRecordBuilder scheduleRecords =
                                context.recordBuilder(ScheduleRecordBuilder.class);
                        scheduleRecords.scheduleID(idToSign);
                    } else {
                        // Note, this will never happen, but Sonar static analysis can't figure that out.
                        throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
                    }
                } else {
                    throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
                }
            } else {
                throw new HandleException(validationResult);
            }
        } else {
            throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    @NonNull
    private ScheduleSignTransactionBody getValidScheduleSignBody(@Nullable final TransactionBody currentTransaction)
            throws PreCheckException {
        if (currentTransaction != null) {
            final ScheduleSignTransactionBody scheduleSignTransaction = currentTransaction.scheduleSign();
            if (scheduleSignTransaction != null) {
                if (scheduleSignTransaction.scheduleID() != null) {
                    return scheduleSignTransaction;
                } else {
                    throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
                }
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }
}
