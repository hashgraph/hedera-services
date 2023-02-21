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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.ScheduleTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#SCHEDULE_SIGN}.
 */
public class ScheduleSignHandler extends AbstractScheduleHandler implements TransactionHandler {
    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_SIGN} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @param keyLookup the {@link AccountKeyLookup} to use for key resolution
     * @param scheduleStore the {@link ReadableScheduleStore} to use for schedule resolution
     * @param dispatcher the {@link PreHandleDispatcher} that can be used to pre-handle the inner
     *     txn
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public ScheduleTransactionMetadata preHandle(
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup keyLookup,
            @NonNull final ReadableScheduleStore scheduleStore,
            @NonNull final PreHandleDispatcher dispatcher) {
        //        final var op = txn.scheduleSign();
        //        final var id = op.scheduleID();
        //
        //        final var scheduleLookupResult = scheduleStore.get(id);
        //        if (scheduleLookupResult.isEmpty()) {
        //            return new InvalidTransactionMetadata(txn, payer, INVALID_SCHEDULE_ID);
        //        }
        //
        //        final var meta =
        //                new ScheduleSigTransactionMetadataBuilder(keyLookup)
        //                        .txnBody(txn)
        //                        .payerKeyFor(payer);
        //
        //        final var scheduledTxn = scheduleLookupResult.get().scheduledTxn();
        //        final var optionalPayer = scheduleLookupResult.get().designatedPayer();
        //        final var payerForNested =
        //                optionalPayer.orElse(scheduledTxn.getTransactionID().getAccountID());
        //
        //        final var innerMeta = preHandleScheduledTxn(scheduledTxn, payerForNested,
        // dispatcher);
        //        meta.scheduledMeta(innerMeta);
        //        return meta.build();
        return null;
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
