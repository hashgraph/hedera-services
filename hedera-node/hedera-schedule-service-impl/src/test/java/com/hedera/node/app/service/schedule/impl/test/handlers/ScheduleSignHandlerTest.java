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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScheduleSignHandlerTest extends ScheduleHandlerTestBase {
    private ScheduleID scheduleID = ScheduleID.newBuilder().setScheduleNum(100L).build();
    @Mock protected JKey adminJKey;
    @Mock protected ScheduleVirtualValue schedule;

    @Mock protected ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;
    protected ReadableScheduleStore scheduleStore;

    @BeforeEach
    void setUp() {
        given(states.<Long, ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStore(states);
    }

    private ScheduleSignHandler subject = new ScheduleSignHandler();

    @Test
    void scheduleSignVanillaNoExplicitPayer() {
        final var txn = scheduleSignTransaction();
        givenSetupForScheduleSign(txn);
        given(dispatcher.dispatch(scheduledTxn, scheduler)).willReturn(scheduledMeta);
        final var context = new PrehandleHandlerContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(scheduler, context.getPayer());
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());
        assertEquals(scheduledMeta, context.getHandlerMetadata());
        assertEquals(OK, context.getStatus());
    }

    @Test
    void scheduleSignFailsIfScheduleMissing() {
        final var txn = scheduleSignTransaction();
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(null);
        final var context = new PrehandleHandlerContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(scheduler, context.getPayer());
        assertEquals(null, context.getHandlerMetadata());
        assertEquals(INVALID_SCHEDULE_ID, context.getStatus());
    }

    @Test
    void scheduleSignVanillaWithOptionalPayerSet() {
        final var txn = scheduleSignTransaction();
        givenSetupForScheduleSign(txn);
        scheduledMeta =
                new TransactionMetadata(
                        scheduledTxn, payer, OK, adminKey, List.of(), null, List.of());

        given(schedule.hasExplicitPayer()).willReturn(true);
        given(schedule.payer()).willReturn(EntityId.fromGrpcAccountId(payer));
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);

        final var context = new PrehandleHandlerContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);

        assertEquals(scheduler, context.getPayer());
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(scheduledMeta, context.getHandlerMetadata());
        assertEquals(adminKey, ((TransactionMetadata) context.getHandlerMetadata()).payerKey());
        assertEquals(OK, context.getStatus());
        verify(dispatcher).dispatch(scheduledTxn, payer);
    }

    @Test
    void scheduleSignForNotSchedulableFails() {
        final var txn = scheduleSignTransaction();

        scheduledTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
                        .setScheduleCreate(ScheduleCreateTransactionBody.newBuilder().build())
                        .build();

        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(schedule);
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(scheduledTxn);
        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
        given(schedule.hasExplicitPayer()).willReturn(false);

        final var context = new PrehandleHandlerContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(scheduler, context.getPayer());
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());
        assertTrue(context.getHandlerMetadata() instanceof TransactionMetadata);
        assertTrue(
                ((TransactionMetadata) context.getHandlerMetadata()).txnBody().hasScheduleCreate());
        assertEquals(
                SCHEDULED_TRANSACTION_NOT_IN_WHITELIST,
                ((TransactionMetadata) context.getHandlerMetadata()).status());
        assertEquals(scheduler, ((TransactionMetadata) context.getHandlerMetadata()).payer());
        assertEquals(OK, context.getStatus());
    }

    @Test
    void scheduleSignNotInWhiteList() {
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        final var txn = scheduleTxnNotRecognized();
        final var context = new PrehandleHandlerContext(keyLookup, txn, payer);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(txn, context.getTxn());
        assertEquals(payer, context.getPayer());
        assertEquals(INVALID_SCHEDULE_ID, context.getStatus());
    }

    private TransactionBody givenSetupForScheduleSign(TransactionBody txn) {
        scheduledTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder().setAccountID(scheduler).build())
                        .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
                        .build();
        scheduledMeta =
                new TransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        schedulerKey,
                        List.of(),
                        null,
                        List.of());
        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(schedule);
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(scheduledTxn);
        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
        return scheduledTxn;
    }

    private TransactionBody scheduleSignTransaction() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
                .setScheduleSign(ScheduleSignTransactionBody.newBuilder().setScheduleID(scheduleID))
                .build();
    }
}
