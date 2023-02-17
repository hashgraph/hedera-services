/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.test.handlers;

import static com.hedera.node.app.service.mono.utils.MiscUtils.asOrdinary;
import static com.hedera.test.factories.txns.ScheduledTxnFactory.scheduleCreateTxnWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleCreateHandlerTest extends ScheduleHandlerTestBase {
    private TransactionBody txn;
    private ScheduleCreateHandler subject = new ScheduleCreateHandler();

    @Test
    void preHandleScheduleCreateVanilla() {
        final var txn = scheduleCreateTransaction(payer);
        final var scheduledTxn =
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID());
        scheduledMeta = new TransactionMetadata(
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID()),
                scheduler,
                OK,
                schedulerKey,
                List.of(),
                null,
                List.of());

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(adminKey), context.getRequiredNonPayerKeys());
        assertEquals(scheduledMeta, context.getHandlerMetadata());

        verify(dispatcher).dispatch(scheduledTxn, payer);
    }

    @Test
    void preHandleScheduleCreateVanillaNoAdmin() {
        final var txn =
                scheduleCreateTxnWith(null, "", payer, scheduler, MiscUtils.asTimestamp(Instant.ofEpochSecond(1L)));
        final var scheduledTxn =
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID());
        scheduledMeta = new TransactionMetadata(
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID()),
                scheduler,
                OK,
                schedulerKey,
                List.of(),
                null,
                List.of());

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());
        assertEquals(scheduledMeta, context.getHandlerMetadata());

        verify(dispatcher).dispatch(scheduledTxn, payer);
    }

    @Test
    void preHandleScheduleCreateFailsOnMissingPayer() {
        givenSetupForScheduleCreate(payer);
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(INVALID_PAYER_ACCOUNT_ID));
        scheduledMeta = new TransactionMetadata(
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID()),
                scheduler,
                OK,
                schedulerKey,
                List.of(),
                null,
                List.of());
        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertEquals(scheduledMeta, context.getHandlerMetadata());

        verify(dispatcher).dispatch(scheduledTxn, payer);
    }

    @Test
    void preHandleScheduleCreateUsesSamePayerIfScheduledPayerNotSet() {
        givenSetupForScheduleCreate(null);
        scheduledMeta = new TransactionMetadata(
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID()),
                scheduler,
                OK,
                schedulerKey,
                List.of(),
                null,
                List.of());
        given(dispatcher.dispatch(eq(scheduledTxn), any())).willReturn(scheduledMeta);
        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(adminKey), context.getRequiredNonPayerKeys());
        assertEquals(scheduledMeta, context.getHandlerMetadata());

        verify(dispatcher).dispatch(scheduledTxn, scheduler);
    }

    @Test
    void failsWithScheduleTransactionsNotRecognized() {
        final var txn = scheduleTxnNotRecognized();
        final var scheduledTxn =
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID());
        scheduledMeta = new TransactionMetadata(
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID()),
                scheduler,
                OK,
                schedulerKey,
                List.of(),
                null,
                List.of());

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0, false, OK);
        basicMetaAssertions(
                (TransactionMetadata) context.getHandlerMetadata(), 0, true, SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());
        assertEquals(null, ((TransactionMetadata) context.getHandlerMetadata()).payerKey());
        assertEquals(List.of(), ((TransactionMetadata) context.getHandlerMetadata()).requiredNonPayerKeys());

        verify(dispatcher, never()).dispatch(scheduledTxn, payer);
    }

    @Test
    void innerTxnFailsSetsStatus() {
        givenSetupForScheduleCreate(payer);
        scheduledMeta = new TransactionMetadata(
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID()),
                payer,
                INVALID_ACCOUNT_ID,
                schedulerKey,
                List.of(),
                null,
                List.of());
        given(dispatcher.dispatch(any(), any())).willReturn(scheduledMeta);

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(adminKey), context.getRequiredNonPayerKeys());

        verify(dispatcher).dispatch(scheduledTxn, payer);
        assertEquals(UNRESOLVABLE_REQUIRED_SIGNERS, ((TransactionMetadata) context.getHandlerMetadata()).status());
        assertEquals(payer, ((TransactionMetadata) context.getHandlerMetadata()).payer());
        assertEquals(List.of(), ((TransactionMetadata) context.getHandlerMetadata()).requiredNonPayerKeys());
        assertEquals(
                asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID()),
                ((TransactionMetadata) context.getHandlerMetadata()).txnBody());
    }

    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
        return scheduleCreateTxnWith(
                key,
                "test",
                payer,
                scheduler,
                Timestamp.newBuilder().setSeconds(1_234_567L).build());
    }

    private void givenSetupForScheduleCreate(final AccountID payer) {
        txn = scheduleCreateTransaction(payer);
        scheduledTxn = asOrdinary(txn.getScheduleCreate().getScheduledTransactionBody(), txn.getTransactionID());

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        lenient().when(dispatcher.dispatch(scheduledTxn, payer)).thenReturn(scheduledMeta);
    }
}
