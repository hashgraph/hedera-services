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

class ScheduleSignHandlerTest extends ScheduleHandlerTestBase {
    //    private ScheduleID scheduleID = ScheduleID.newBuilder().setScheduleNum(100L).build();
    //    @Mock protected JKey adminJKey;
    //    @Mock protected ScheduleVirtualValue schedule;
    //
    //    @Mock protected ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;
    //    protected ReadableScheduleStore scheduleStore;
    //
    //    @BeforeEach
    //    void setUp() {
    //        given(states.<Long,
    // ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
    //        scheduleStore = new ReadableScheduleStore(states);
    //    }
    //
    //    private ScheduleSignHandler subject = new ScheduleSignHandler();
    //
    //    @Test
    //    void scheduleSignVanillaNoExplicitPayer() {
    //        final var txn = scheduleSignTransaction();
    //        givenSetupForScheduleSign(txn);
    //        given(dispatcher.dispatch(scheduledTxn, scheduler)).willReturn(scheduledMeta);
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore,
    // dispatcher);
    //        assertEquals(scheduler, meta.payer());
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(), meta.requiredNonPayerKeys());
    //        assertEquals(scheduledMeta, meta.scheduledMeta());
    //        assertEquals(OK, meta.status());
    //    }
    //
    //    @Test
    //    void scheduleSignFailsIfScheduleMissing() {
    //        final var txn = scheduleSignTransaction();
    //        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(null);
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore,
    // dispatcher);
    //        assertEquals(scheduler, meta.payer());
    //        assertEquals(null, meta.payerKey());
    //        assertEquals(null, meta.scheduledMeta());
    //        assertEquals(INVALID_SCHEDULE_ID, meta.status());
    //        assertTrue(meta instanceof InvalidTransactionMetadata);
    //    }
    //
    //    @Test
    //    void scheduleSignVanillaWithOptionalPayerSet() {
    //        final var txn = scheduleSignTransaction();
    //        givenSetupForScheduleSign(txn);
    //        scheduledMeta = new SigTransactionMetadata(scheduledTxn, payer, OK, adminKey,
    // List.of());
    //
    //        given(schedule.hasExplicitPayer()).willReturn(true);
    //        given(schedule.payer()).willReturn(EntityId.fromGrpcAccountId(payer));
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore,
    // dispatcher);
    //
    //        assertEquals(scheduler, meta.payer());
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(scheduledMeta, meta.scheduledMeta());
    //        assertEquals(adminKey, meta.scheduledMeta().payerKey());
    //        assertEquals(OK, meta.status());
    //        verify(dispatcher).dispatch(scheduledTxn, payer);
    //    }
    //
    //    @Test
    //    void scheduleSignForNotSchedulableFails() {
    //        final var txn = scheduleSignTransaction();
    //
    //        scheduledTxn =
    //                TransactionBody.newBuilder()
    //                        .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
    //                        .setScheduleCreate(ScheduleCreateTransactionBody.newBuilder().build())
    //                        .build();
    //
    //        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(schedule);
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(scheduledTxn);
    //        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
    //        given(schedule.hasExplicitPayer()).willReturn(false);
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore,
    // dispatcher);
    //        assertEquals(scheduler, meta.payer());
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(), meta.requiredNonPayerKeys());
    //        assertTrue(meta.scheduledMeta() instanceof InvalidTransactionMetadata);
    //        assertTrue(meta.scheduledMeta().txnBody().hasScheduleCreate());
    //        assertEquals(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST, meta.scheduledMeta().status());
    //        assertEquals(scheduler, meta.scheduledMeta().payer());
    //        assertEquals(OK, meta.status());
    //    }
    //
    //    @Test
    //    void scheduleSignNotInWhiteList() {
    //        final var txn = scheduleTxnNotRecognized();
    //        TransactionMetadata result =
    //                subject.preHandle(txn, payer, keyLookup, scheduleStore, dispatcher);
    //        assertTrue(result instanceof InvalidTransactionMetadata);
    //        assertEquals(txn, result.txnBody());
    //        assertEquals(payer, result.payer());
    //        assertEquals(INVALID_SCHEDULE_ID, result.status());
    //    }
    //
    //    @Test
    //    void handleNotImplemented() {
    //        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    //    }
    //
    //    private TransactionBody givenSetupForScheduleSign(TransactionBody txn) {
    //        scheduledTxn =
    //                TransactionBody.newBuilder()
    //                        .setTransactionID(
    //                                TransactionID.newBuilder().setAccountID(scheduler).build())
    //
    // .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
    //                        .build();
    //        scheduledMeta =
    //                new SigTransactionMetadata(
    //                        asOrdinary(
    //                                txn.getScheduleCreate().getScheduledTransactionBody(),
    //                                txn.getTransactionID()),
    //                        scheduler,
    //                        OK,
    //                        schedulerKey,
    //                        List.of());
    //        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(schedule);
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(scheduledTxn);
    //        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
    //        return scheduledTxn;
    //    }
    //
    //    private TransactionBody scheduleSignTransaction() {
    //        return TransactionBody.newBuilder()
    //                .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
    //
    // .setScheduleSign(ScheduleSignTransactionBody.newBuilder().setScheduleID(scheduleID))
    //                .build();
    //    }
}
