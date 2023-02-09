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
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#SCHEDULE_DELETE}.
 */
public class ScheduleDeleteHandler implements TransactionHandler {
    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleDelete} transaction, returning
     * the metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @param keyLookup the {@link AccountKeyLookup} to use for key resolution
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup keyLookup,
            @NonNull final ReadableScheduleStore scheduleStore) {
        final var metaBuilder =
                new SigTransactionMetadataBuilder(keyLookup).txnBody(txn).payerKeyFor(payer);
        final var op = txn.getScheduleDelete();
        final var id = op.getScheduleID();

        // check for a missing schedule. A schedule with this id could have never existed,
        // or it could have already been executed or deleted
        final var scheduleLookupResult = scheduleStore.get(id);
        if (scheduleLookupResult.isEmpty()) {
            return new InvalidTransactionMetadata(txn, payer, INVALID_SCHEDULE_ID);
        }

        // No need to check for SCHEDULE_PENDING_EXPIRATION, SCHEDULE_ALREADY_DELETED,
        // SCHEDULE_ALREADY_EXECUTED
        // if any of these are the case then the scheduled tx would not be present in scheduleStore

        // check whether schedule was created with an admin key
        // if it wasn't, the schedule can't be deleted
        final var adminKey = scheduleLookupResult.get().adminKey();
        if (adminKey.isEmpty()) {
            return new InvalidTransactionMetadata(txn, payer, SCHEDULE_IS_IMMUTABLE);
        }

        // add admin key of the original ScheduleCreate tx
        // to the list of keys required to execute this ScheduleDelete tx
        metaBuilder.addToReqNonPayerKeys(adminKey.get());

        return metaBuilder.build();
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
