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

import static org.junit.jupiter.api.Assertions.*;

class ScheduleCreateHandlerTest extends ScheduleHandlerTestBase {
    //    private TransactionBody txn;
    //    private ScheduleCreateHandler subject = new ScheduleCreateHandler();
    //
    //    @Test
    //    void preHandleScheduleCreateVanilla() {
    //        final var txn = scheduleCreateTransaction(payer);
    //        final var scheduledTxn =
    //                asOrdinary(
    //                        txn.getScheduleCreate().getScheduledTransactionBody(),
    //                        txn.getTransactionID());
    //        scheduledMeta =
    //                new SigTransactionMetadata(
    //                        asOrdinary(
    //                                txn.getScheduleCreate().getScheduledTransactionBody(),
    //                                txn.getTransactionID()),
    //                        scheduler,
    //                        OK,
    //                        schedulerKey,
    //                        List.of());
    //
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, dispatcher);
    //
    //        basicMetaAssertions(meta, 1, false, OK);
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(adminKey), meta.requiredNonPayerKeys());
    //        assertEquals(scheduledMeta, meta.scheduledMeta());
    //
    //        verify(dispatcher).dispatch(scheduledTxn, payer);
    //    }
    //
    //    @Test
    //    void preHandleScheduleCreateVanillaNoAdmin() {
    //        final var txn =
    //                scheduleCreateTxnWith(
    //                        null,
    //                        "",
    //                        payer,
    //                        scheduler,
    //                        MiscUtils.asTimestamp(Instant.ofEpochSecond(1L)));
    //        final var scheduledTxn =
    //                asOrdinary(
    //                        txn.getScheduleCreate().getScheduledTransactionBody(),
    //                        txn.getTransactionID());
    //        scheduledMeta =
    //                new SigTransactionMetadata(
    //                        asOrdinary(
    //                                txn.getScheduleCreate().getScheduledTransactionBody(),
    //                                txn.getTransactionID()),
    //                        scheduler,
    //                        OK,
    //                        schedulerKey,
    //                        List.of());
    //
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, dispatcher);
    //
    //        basicMetaAssertions(meta, 0, false, OK);
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(), meta.requiredNonPayerKeys());
    //        assertEquals(scheduledMeta, meta.scheduledMeta());
    //
    //        verify(dispatcher).dispatch(scheduledTxn, payer);
    //    }
    //
    //    @Test
    //    void preHandleScheduleCreateFailsOnMissingPayer() {
    //        givenSetupForScheduleCreate(payer);
    //        given(keyLookup.getKey(scheduler))
    //
    // .willReturn(KeyOrLookupFailureReason.withFailureReason(INVALID_PAYER_ACCOUNT_ID));
    //        scheduledMeta =
    //                new SigTransactionMetadata(
    //                        asOrdinary(
    //                                txn.getScheduleCreate().getScheduledTransactionBody(),
    //                                txn.getTransactionID()),
    //                        scheduler,
    //                        OK,
    //                        schedulerKey,
    //                        List.of());
    //        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, dispatcher);
    //
    //        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
    //        assertEquals(scheduledMeta, meta.scheduledMeta());
    //
    //        verify(dispatcher).dispatch(scheduledTxn, payer);
    //    }
    //
    //    @Test
    //    void preHandleScheduleCreateUsesSamePayerIfScheduledPayerNotSet() {
    //        givenSetupForScheduleCreate(null);
    //        scheduledMeta =
    //                new SigTransactionMetadata(
    //                        asOrdinary(
    //                                txn.getScheduleCreate().getScheduledTransactionBody(),
    //                                txn.getTransactionID()),
    //                        scheduler,
    //                        OK,
    //                        schedulerKey,
    //                        List.of());
    //        given(dispatcher.dispatch(eq(scheduledTxn), any())).willReturn(scheduledMeta);
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, dispatcher);
    //
    //        basicMetaAssertions(meta, 1, false, OK);
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(adminKey), meta.requiredNonPayerKeys());
    //        assertEquals(scheduledMeta, meta.scheduledMeta());
    //
    //        verify(dispatcher).dispatch(scheduledTxn, scheduler);
    //    }
    //
    //    @Test
    //    void failsWithScheduleTransactionsNotRecognized() {
    //        final var txn = scheduleTxnNotRecognized();
    //        final var scheduledTxn =
    //                asOrdinary(
    //                        txn.getScheduleCreate().getScheduledTransactionBody(),
    //                        txn.getTransactionID());
    //        scheduledMeta =
    //                new SigTransactionMetadata(
    //                        asOrdinary(
    //                                txn.getScheduleCreate().getScheduledTransactionBody(),
    //                                txn.getTransactionID()),
    //                        scheduler,
    //                        OK,
    //                        schedulerKey,
    //                        List.of());
    //
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, dispatcher);
    //
    //        basicMetaAssertions(meta, 0, false, OK);
    //        basicMetaAssertions(meta.scheduledMeta(), 0, true,
    // SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(), meta.requiredNonPayerKeys());
    //        assertEquals(null, meta.scheduledMeta().payerKey());
    //        assertEquals(List.of(), meta.scheduledMeta().requiredNonPayerKeys());
    //
    //        verify(dispatcher, never()).dispatch(scheduledTxn, payer);
    //    }
    //
    //    @Test
    //    void innerTxnFailsSetsStatus() {
    //        givenSetupForScheduleCreate(payer);
    //        scheduledMeta =
    //                new SigTransactionMetadata(
    //                        asOrdinary(
    //                                txn.getScheduleCreate().getScheduledTransactionBody(),
    //                                txn.getTransactionID()),
    //                        payer,
    //                        INVALID_ACCOUNT_ID,
    //                        schedulerKey,
    //                        List.of());
    //        given(dispatcher.dispatch(any(), any())).willReturn(scheduledMeta);
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, dispatcher);
    //
    //        basicMetaAssertions(meta, 1, false, OK);
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(adminKey), meta.requiredNonPayerKeys());
    //
    //        verify(dispatcher).dispatch(scheduledTxn, payer);
    //        assertTrue(meta.scheduledMeta() instanceof InvalidTransactionMetadata);
    //        assertEquals(UNRESOLVABLE_REQUIRED_SIGNERS, meta.scheduledMeta().status());
    //        assertEquals(payer, meta.scheduledMeta().payer());
    //        assertEquals(List.of(), meta.scheduledMeta().requiredNonPayerKeys());
    //        assertEquals(
    //                asOrdinary(
    //                        txn.getScheduleCreate().getScheduledTransactionBody(),
    //                        txn.getTransactionID()),
    //                meta.scheduledMeta().txnBody());
    //    }
    //
    //    @Test
    //    void handleNotImplemented() {
    //        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    //    }
    //
    //    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
    //        return scheduleCreateTxnWith(
    //                key,
    //                "test",
    //                payer,
    //                scheduler,
    //                Timestamp.newBuilder().setSeconds(1_234_567L).build());
    //    }
    //
    //    private void givenSetupForScheduleCreate(final AccountID payer) {
    //        txn = scheduleCreateTransaction(payer);
    //        scheduledTxn =
    //                asOrdinary(
    //                        txn.getScheduleCreate().getScheduledTransactionBody(),
    //                        txn.getTransactionID());
    //
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //        lenient().when(dispatcher.dispatch(scheduledTxn, payer)).thenReturn(scheduledMeta);
    //    }
}
