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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScheduleDeleteHandlerTest extends ScheduleHandlerTestBase {
    private final ScheduleID scheduleID = ScheduleID.newBuilder().setScheduleNum(100L).build();
    @Mock protected JKey adminJKey;
    //    @Mock protected ScheduleVirtualValue schedule;
    //    public static SchedulableTransactionBody scheduledTxn;

    @Mock protected ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;
    protected ReadableScheduleStore scheduleStore;

    @BeforeEach
    void setUp() {
        given(states.<Long, ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStore(states);
    }

    private final ScheduleDeleteHandler subject = new ScheduleDeleteHandler();

    @Test
    void scheduleDeleteHappyPath() {
        final var txn = scheduleDeleteTransaction();
        givenSetupForScheduleDelete(txn);
        given(dispatcher.dispatch(scheduledTxn, scheduler)).willReturn(scheduledMeta);
        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore);
        assertEquals(scheduler, meta.payer());
        //        assertEquals(schedulerKey, meta.payerKey());
        assertEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(OK, meta.status());
    }

    //    @Test
    //    void scheduleDeleteFailsIfScheduleMissing() {
    //        final var txn = scheduleSignTransaction();
    //        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(Optional.empty());
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore);
    //        assertEquals(scheduler, meta.payer());
    //        assertEquals(null, meta.payerKey());
    //        assertEquals(null, meta.scheduledMeta());
    //        assertEquals(INVALID_SCHEDULE_ID, meta.status());
    //        assertTrue(meta instanceof InvalidTransactionMetadata);
    //    }

    //    @Test
    //    void scheduleDeleteVanillaWithOptionalPayerSet() {
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
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore);
    //
    //        assertEquals(scheduler, meta.payer());
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(scheduledMeta, meta.scheduledMeta());
    //        assertEquals(adminKey, meta.scheduledMeta().payerKey());
    //        assertEquals(OK, meta.status());
    //        verify(dispatcher).dispatch(scheduledTxn, payer);
    //    }

    //    @Test
    //    void scheduleDeleteForNotSchedulableFails() {
    //        final var txn = scheduleSignTransaction();
    //
    //        scheduledTxn =
    //                TransactionBody.newBuilder()
    //                        .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
    //                        .setScheduleCreate(ScheduleCreateTransactionBody.newBuilder().build())
    //                        .build();
    //
    //
    // given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(Optional.of(schedule));
    //        given(keyLookup.getKey(scheduler))
    //                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
    //        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(scheduledTxn);
    //        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
    //        given(schedule.hasExplicitPayer()).willReturn(false);
    //
    //        final var meta = subject.preHandle(txn, scheduler, keyLookup, scheduleStore);
    //        assertEquals(scheduler, meta.payer());
    //        assertEquals(schedulerKey, meta.payerKey());
    //        assertEquals(List.of(), meta.requiredNonPayerKeys());
    //        assertTrue(meta.scheduledMeta() instanceof InvalidTransactionMetadata);
    //        assertTrue(meta.scheduledMeta().txnBody().hasScheduleCreate());
    //        assertEquals(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST, meta.scheduledMeta().status());
    //        assertEquals(scheduler, meta.scheduledMeta().payer());
    //        assertEquals(OK, meta.status());
    //    }

    //    @Test
    //    void scheduleDeleteNotInWhiteList() {
    //        final var txn = scheduleTxnNotRecognized();
    //        TransactionMetadata result =
    //                subject.preHandle(txn, payer, keyLookup, scheduleStore);
    //        assertTrue(result instanceof InvalidTransactionMetadata);
    //        assertEquals(txn, result.txnBody());
    //        assertEquals(payer, result.payer());
    //        assertEquals(INVALID_SCHEDULE_ID, result.status());
    //    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    }

    private void givenSetupForScheduleDelete(TransactionBody txn) {
        //        scheduledTxn = SchedulableTransactionBody.newBuilder()
        //                .setTransactionFee(123L)
        //                .setCryptoDelete(
        //                        CryptoDeleteTransactionBody.newBuilder()
        //                                .setDeleteAccountID(IdUtils.asAccount("0.0.2"))
        //                                .setTransferAccountID(IdUtils.asAccount("0.0.75231")))
        //                .build();
        scheduledTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(TransactionID.newBuilder().setAccountID(payer).build())
                        .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
                        .build();
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        adminKey,
                        List.of());
        //        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(schedule);
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        //        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(scheduledTxn);
        //        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
    }

    private TransactionBody scheduleDeleteTransaction() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
                .setScheduleDelete(
                        ScheduleDeleteTransactionBody.newBuilder().setScheduleID(scheduleID))
                .build();
    }

    //    public static TransactionBody scheduleCreateTxnWith(
    //            final Key scheduleAdminKey,
    //            final AccountID payer,
    //            final AccountID scheduler,
    //            final Timestamp validStart,
    //            final Timestamp expirationTime,
    //            final Boolean waitForExpiry) {
    //        final var creation =
    //                ScheduleCreateTransactionBody.newBuilder()
    //                        .setScheduledTransactionBody(scheduledTxn);
    //        if (scheduleAdminKey != null) {
    //            creation.setAdminKey(scheduleAdminKey);
    //        }
    //        if (payer != null) {
    //            creation.setPayerAccountID(payer);
    //        }
    //        if (expirationTime != null) {
    //            creation.setExpirationTime(expirationTime);
    //        }
    //        if (waitForExpiry != null) {
    //            creation.setWaitForExpiry(waitForExpiry);
    //        }
    //        return TransactionBody.newBuilder()
    //                .setTransactionID(
    //                        TransactionID.newBuilder()
    //                                .setTransactionValidStart(validStart)
    //                                .setAccountID(scheduler)
    //                                .build())
    //                .setScheduleCreate(creation)
    //                .build();
    //    }
}
