/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.schedule.impl;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.MiscUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

import com.hedera.node.app.service.mono.exceptions.UnknownHederaFunctionality;
import com.hedera.node.app.service.schedule.SchedulePreTransactionHandler;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.*;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A {@link SchedulePreTransactionHandler} implementation that pre-computes the required signing
 * keys (but not the candidate signatures) for each schedule operation.
 */
public class SchedulePreTransactionHandlerImpl implements SchedulePreTransactionHandler {
    private final AccountKeyLookup keyLookup;

    public SchedulePreTransactionHandlerImpl(final AccountKeyLookup keyLookup) {
        this.keyLookup = keyLookup;
    }

    @Override
    public ScheduleTransactionMetadata preHandleCreateSchedule(
            final TransactionBody txn,
            final AccountID payer,
            final PreHandleDispatcher dispatcher) {
        final var op = txn.getScheduleCreate();
        final var meta =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer);

        if (op.hasAdminKey()) {
            final var key = asHederaKey(op.getAdminKey());
            key.ifPresent(meta::addToReqKeys);
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

        final var innerMeta = preHandleInnerTxn(scheduledTxn, payerForNested, dispatcher);
        meta.scheduledMeta(innerMeta);
        return meta.build();
    }

    private TransactionMetadata preHandleInnerTxn(
            final TransactionBody scheduledTxn,
            final AccountID payerForNested,
            PreHandleDispatcher dispatcher) {
        final HederaFunctionality scheduledFunction;
        try {
            scheduledFunction = functionOf(scheduledTxn);
        } catch (UnknownHederaFunctionality ex) {
            return failedMeta(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST, scheduledTxn, payerForNested);
        }

        if (!isSchedulable(scheduledFunction)) {
            return failedMeta(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST, scheduledTxn, payerForNested);
        }

        final var meta = dispatcher.dispatch(scheduledTxn, payerForNested);
        if (meta.failed()) {
            return new InvalidTransactionMetadata(
                    scheduledTxn, payerForNested, UNRESOLVABLE_REQUIRED_SIGNERS);
        }
        return meta;
    }

    @Override
    public ScheduleTransactionMetadata preHandleSignSchedule(
            TransactionBody txn, AccountID payer, PreHandleDispatcher dispatcher) {
        throw new NotImplementedException();
    }

    @Override
    public ScheduleTransactionMetadata preHandleDeleteSchedule(
            TransactionBody txn, AccountID payer, PreHandleDispatcher dispatcher) {
        throw new NotImplementedException();
    }

    private TransactionMetadata failedMeta(
            final ResponseCodeEnum response, final TransactionBody txn, final AccountID payer) {
        final var meta =
                new SigTransactionMetadataBuilder<>(keyLookup).payerKeyFor(payer).txnBody(txn);
        meta.status(response);
        return meta.build();
    }
}
