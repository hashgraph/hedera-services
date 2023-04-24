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
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScheduleSignHandlerTest extends ScheduleHandlerTestBase {
    private final ScheduleID scheduleID =
            ScheduleID.newBuilder().scheduleNum(100L).build();
    private final ScheduleSignHandler subject = new ScheduleSignHandler();

    @Mock
    protected JKey adminJKey;

    @Mock
    protected ScheduleVirtualValue schedule;

    @Mock
    protected ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;

    protected ReadableScheduleStore scheduleStore;
    private TransactionBody scheduledTxn;

    @BeforeEach
    void setUp() {
        given(states.<Long, ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStore(states);
    }

    @Test
    void scheduleSignVanillaNoExplicitPayer() throws PreCheckException {
        final var txn = scheduleSignTransaction();
        scheduledTxn = givenSetupForScheduleSign();

        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context, scheduleStore, dispatcher);
        assertEquals(scheduler, context.payer());
        assertEquals(schedulerKey, context.payerKey());
        assertEquals(Collections.EMPTY_SET, context.requiredNonPayerKeys());

        PreHandleContext innerContext = context.innerContext();
        basicContextAssertions(innerContext, 0);
        assertEquals(scheduler, innerContext.payer());
        assertEquals(schedulerKey, innerContext.payerKey());
    }

    @Test
    void scheduleSignFailsIfScheduleMissing() throws PreCheckException {
        final var txn = scheduleSignTransaction();
        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.key()).willReturn(schedulerKey);
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(null);
        final var context = new PreHandleContext(keyLookup, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context, scheduleStore, dispatcher), INVALID_SCHEDULE_ID);

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void scheduleSignVanillaWithOptionalPayerSet() throws PreCheckException {
        final var txn = scheduleSignTransaction();
        scheduledTxn = givenSetupForScheduleSign();

        given(schedule.hasExplicitPayer()).willReturn(true);
        // @migration this use of PbjConverter is temporary until services complete PBJ migration
        given(schedule.payer()).willReturn(EntityId.fromGrpcAccountId(PbjConverter.fromPbj(payer)));
        given(keyLookup.getAccountById(payer)).willReturn(payerAccount);
        given(payerAccount.key()).willReturn(adminKey);

        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context, scheduleStore, dispatcher);

        assertEquals(scheduler, context.payer());
        assertEquals(schedulerKey, context.payerKey());

        final var innerContext = context.innerContext();
        basicContextAssertions(innerContext, 0);
        assertEquals(payer, innerContext.payer());
        assertEquals(adminKey, innerContext.payerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void scheduleSignForNotSchedulableFails() throws PreCheckException {
        final var txn = scheduleSignTransaction();

        scheduledTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler))
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder().build())
                .build();

        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);
        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.key()).willReturn(schedulerKey);
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(scheduledTxn));
        given(schedule.hasExplicitPayer()).willReturn(false);

        final var context = new PreHandleContext(keyLookup, txn);
        assertThrowsPreCheck(
                () -> subject.preHandle(context, scheduleStore, dispatcher), SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
    }

    // @todo Need to create a valid test for "schedule sign with key not in whitelist"
    //       (the prior test just checked for a missing key, which throws NPE now)

    private TransactionBody givenSetupForScheduleSign() {
        final TransactionBody scheduledTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler).build())
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().build())
                .build();
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);
        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.key()).willReturn(schedulerKey);
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(scheduledTxn));
        return scheduledTxn;
    }

    private TransactionBody scheduleSignTransaction() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler))
                .scheduleSign(ScheduleSignTransactionBody.newBuilder().scheduleID(scheduleID))
                .build();
    }
}
