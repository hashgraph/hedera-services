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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScheduleSignHandlerTest extends ScheduleHandlerTestBase {
    private final ScheduleID scheduleID =
            ScheduleID.newBuilder().scheduleNum(100L).build();

    @Mock
    protected JKey adminJKey;

    @Mock
    protected ScheduleVirtualValue schedule;

    @Mock
    protected ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;

    protected ReadableScheduleStore scheduleStore;
    private final ScheduleSignHandler subject = new ScheduleSignHandler();

    private TransactionBody scheduledTxn;

    @BeforeEach
    void setUp() {
        given(states.<Long, ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStore(states);
    }

    @Test
    void scheduleSignVanillaNoExplicitPayer() {
        final var txn = scheduleSignTransaction();
        givenSetupForScheduleSign(txn);
        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(scheduler, context.getPayer());
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());

        PreHandleContext innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, false, OK);
        assertEquals(scheduler, innerContext.getPayer());
        assertEquals(schedulerKey, innerContext.getPayerKey());
    }

    @Test
    void scheduleSignFailsIfScheduleMissing() {
        final var txn = scheduleSignTransaction();
        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(null);
        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(scheduler, context.getPayer());
        assertNull(context.getInnerContext());
        assertEquals(INVALID_SCHEDULE_ID, context.getStatus());

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void scheduleSignVanillaWithOptionalPayerSet() {
        final var txn = scheduleSignTransaction();
        givenSetupForScheduleSign(txn);
        given(schedule.hasExplicitPayer()).willReturn(true);
        // @migration this use of PbjConverter is temporary until services complete PBJ migration
        given(schedule.payer()).willReturn(EntityId.fromGrpcAccountId(PbjConverter.fromPbj(payer)));
        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(adminKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);

        assertEquals(scheduler, context.getPayer());
        assertEquals(schedulerKey, context.getPayerKey());

        final var innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, false, OK);
        assertEquals(payer, innerContext.getPayer());
        assertEquals(adminKey, innerContext.getPayerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void scheduleSignForNotSchedulableFails() {
        final var txn = scheduleSignTransaction();

        scheduledTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler))
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder().build())
                .build();

        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);
        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(scheduledTxn));
        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
        given(schedule.hasExplicitPayer()).willReturn(false);

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, scheduleStore, dispatcher);
        basicContextAssertions(context, 0, false, OK);
        assertEquals(scheduler, context.getPayer());
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());
        assertEquals(scheduler, context.getPayer());
        assertEquals(OK, context.getStatus());
    }

    @Test
    void scheduleSignNotInWhiteList() {
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        final var txn = scheduleTxnNotRecognized();
        final var context = new PreHandleContext(keyLookup, txn, payer);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(txn, context.getTxn());
        assertEquals(payer, context.getPayer());
        assertEquals(INVALID_SCHEDULE_ID, context.getStatus());
    }

    private TransactionBody givenSetupForScheduleSign(TransactionBody txn) {
        scheduledTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler).build())
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().build())
                .build();
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);
        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(scheduledTxn));
        given(schedule.adminKey()).willReturn(Optional.of(adminJKey));
        return scheduledTxn;
    }

    private TransactionBody scheduleSignTransaction() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler))
                .scheduleSign(ScheduleSignTransactionBody.newBuilder().scheduleID(scheduleID))
                .build();
    }
}
