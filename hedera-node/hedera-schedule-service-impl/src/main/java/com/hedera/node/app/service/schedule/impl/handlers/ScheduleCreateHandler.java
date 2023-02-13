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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asOrdinary;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleCreate}.
 */
@Singleton
public class ScheduleCreateHandler extends AbstractScheduleHandler implements TransactionHandler {
    @Inject
    public ScheduleCreateHandler() {}

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleCreate} transaction, returning
     * the metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param context the {@link PrehandleHandlerContext} which collects all information that will
     *     be passed to {@link #handle(TransactionMetadata)}
     * @param dispatcher the {@link PreHandleDispatcher} that can be used to pre-handle the inner
     *     txn
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(
            @NonNull final PrehandleHandlerContext context,
            @NonNull final PreHandleDispatcher dispatcher) {
        requireNonNull(context);
        final var txn = context.getTxn();
        final var op = txn.getScheduleCreate();

        if (op.hasAdminKey()) {
            final var key = asHederaKey(op.getAdminKey());
            key.ifPresent(context::addToReqNonPayerKeys);
        }

        final var scheduledTxn =
                asOrdinary(op.getScheduledTransactionBody(), txn.getTransactionID());

        /* We need to always add the custom payer to the sig requirements even if it equals the to level transaction
        payer. It is still part of the "other" parties, and we need to know to store it's key with the
        schedule in all cases. This fixes a case where the ScheduleCreate payer and the custom payer are
        the same payer, which would cause the custom payers signature to not get stored and then a ScheduleSign
        would not execute the transaction without and extra signature from the custom payer.*/
        final var payerForNested =
                op.hasPayerAccountID()
                        ? op.getPayerAccountID()
                        : txn.getTransactionID().getAccountID();

        // FUTURE: Once we allow schedule transactions to be scheduled inside, we need a check here
        // to see
        // if provided payer is same as payer in the inner transaction.

        final var innerMeta = preHandleScheduledTxn(scheduledTxn, payerForNested, dispatcher);
        context.handlerMetadata(innerMeta);
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
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }
}
