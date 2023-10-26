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

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ScheduleRecordBuilder;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.SchedulingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_DELETE}.
 */
@Singleton
public class ScheduleDeleteHandler extends AbstractScheduleHandler implements TransactionHandler {

    @Inject
    public ScheduleDeleteHandler() {
        super();
    }

    @Override
    public void pureChecks(@Nullable final TransactionBody currentTransaction) throws PreCheckException {
        getValidScheduleDeleteBody(currentTransaction);
    }

    @NonNull
    private ScheduleDeleteTransactionBody getValidScheduleDeleteBody(@Nullable final TransactionBody currentTransaction)
            throws PreCheckException {
        if (currentTransaction != null) {
            final ScheduleDeleteTransactionBody scheduleDeleteTransaction = currentTransaction.scheduleDelete();
            if (scheduleDeleteTransaction != null) {
                if (scheduleDeleteTransaction.scheduleID() != null) {
                    return scheduleDeleteTransaction;
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

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final ReadableScheduleStore scheduleStore = context.createStore(ReadableScheduleStore.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        final TransactionBody currentTransaction = context.body();
        final ScheduleDeleteTransactionBody scheduleDeleteTransaction = getValidScheduleDeleteBody(currentTransaction);
        if (scheduleDeleteTransaction.scheduleID() != null) {
            final Schedule scheduleData =
                    preValidate(scheduleStore, isLongTermEnabled, scheduleDeleteTransaction.scheduleID());
            final Key adminKey = scheduleData.adminKey();
            if (adminKey != null) context.requireKey(adminKey);
            else throw new PreCheckException(ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE);
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        Objects.requireNonNull(context, NULL_CONTEXT_MESSAGE);
        final WritableScheduleStore scheduleStore = context.writableStore(WritableScheduleStore.class);
        final TransactionBody currentTransaction = context.body();
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        try {
            final ScheduleDeleteTransactionBody scheduleToDelete = getValidScheduleDeleteBody(currentTransaction);
            final ScheduleID idToDelete = scheduleToDelete.scheduleID();
            if (idToDelete != null) {
                final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
                final Schedule scheduleData = reValidate(scheduleStore, isLongTermEnabled, idToDelete);
                if (scheduleData.hasAdminKey()) {
                    final SignatureVerification verificationResult =
                            context.verificationFor(scheduleData.adminKeyOrThrow());
                    if (verificationResult.passed()) {
                        scheduleStore.delete(idToDelete, context.consensusNow());
                        final ScheduleRecordBuilder scheduleRecords =
                                context.recordBuilder(ScheduleRecordBuilder.class);
                        scheduleRecords.scheduleID(idToDelete);
                    } else {
                        throw new HandleException(ResponseCodeEnum.UNAUTHORIZED);
                    }
                } else {
                    throw new HandleException(ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE);
                }
            } else {
                throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
            }
        } catch (final IllegalStateException translate) {
            throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
        } catch (final PreCheckException translate) {
            throw new HandleException(translate.responseCode());
        }
    }

    /**
     * Verify that the transaction and schedule still meet the validation criteria expressed in the
     * {@link AbstractScheduleHandler#preValidate(ReadableScheduleStore, boolean, ScheduleID)} method.
     * @param scheduleStore a Readable source of Schedule data from state
     * @param isLongTermEnabled a flag indicating if long term scheduling is enabled in configuration.
     * @param idToDelete the Schedule ID of the item to mark as deleted.
     * @return a schedule metadata read from state for the ID given, if all validation checks pass.
     * @throws HandleException if any validation check fails.
     */
    @NonNull
    protected Schedule reValidate(
            @NonNull final ReadableScheduleStore scheduleStore,
            final boolean isLongTermEnabled,
            @Nullable final ScheduleID idToDelete)
            throws HandleException {
        try {
            return preValidate(scheduleStore, isLongTermEnabled, idToDelete);
        } catch (final PreCheckException translated) {
            throw new HandleException(translated.responseCode());
        }
    }
}
