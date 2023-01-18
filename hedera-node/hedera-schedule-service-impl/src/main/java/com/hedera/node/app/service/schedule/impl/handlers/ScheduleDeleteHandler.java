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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.meta.*;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleDelete}.
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
        final var op = txn.getScheduleDelete();
        final var id = op.getScheduleID();

        // check for INVALID_SCHEDULE_ID
        final var scheduleLookupResult = scheduleStore.get(id);
        if (scheduleLookupResult.isEmpty()) {
            return new InvalidTransactionMetadata(txn, payer, INVALID_SCHEDULE_ID);
        }

        //        // check for SCHEDULE_PENDING_EXPIRATION
        //        // maybe do nothing in this case?
        //
        //        // check for SCHEDULE_ALREADY_DELETED, SCHEDULE_ALREADY_EXECUTED errors

        // check whether schedule was created with an admin key
        // if it wasn't, the schedule can't be deleted
        final var adminKey = scheduleLookupResult.get().adminKey();
        if (adminKey.isEmpty()) {
            return new InvalidTransactionMetadata(txn, payer, SCHEDULE_IS_IMMUTABLE);
        }

        // check whether payer key for this tx matches the admin key for underlying scheduled
        // transaction
        final KeyOrLookupFailureReason payerKeyOrLookupFailure = keyLookup.getKey(payer);
        if (payerKeyOrLookupFailure.failed()) {
            return new InvalidTransactionMetadata(
                    txn, payer, payerKeyOrLookupFailure.failureReason());
        } else {
            var payerKey = payerKeyOrLookupFailure.key();
            if (!adminKey.get().equals(payerKey))
                return new InvalidTransactionMetadata(
                        txn,
                        payer,
                        INVALID_SIGNATURE); // I'm not sure whether this is the correct code to
            // return here
        }

        final var meta =
                new SigTransactionMetadataBuilder(keyLookup).txnBody(txn).payerKeyFor(payer);

        return meta.build();
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
