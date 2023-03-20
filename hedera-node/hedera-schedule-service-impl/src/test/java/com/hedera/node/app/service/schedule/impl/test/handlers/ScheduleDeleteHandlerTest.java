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
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mock;

class ScheduleDeleteHandlerTest extends ScheduleHandlerTestBase {
    private final ScheduleID scheduleID =
            ScheduleID.newBuilder().scheduleNum(100L).build();
    private final AccountID scheduleDeleter =
            AccountID.newBuilder().accountNum(3001L).build();
    private final ScheduleDeleteHandler subject = new ScheduleDeleteHandler();
    protected TransactionBody scheduledTxn;
    @Mock
    private ScheduleVirtualValue schedule;
    @Mock
    private ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;
    private ReadableScheduleStore scheduleStore;

    @BeforeEach
    void setUp() {
        BDDMockito.given(states.<Long, ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStore(states);
    }

    @Test
    void scheduleDeleteHappyPath() throws DecoderException {
        final var txn = scheduleDeleteTransaction();
        scheduledTxn = givenSetupForScheduleDelete(txn);
        BDDMockito.given(schedule.adminKey()).willReturn(Optional.of(JKey.mapKey(TEST_KEY)));
        BDDMockito.given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);

        final var context = new PreHandleContext(keyLookup, txn, scheduleDeleter);
        subject.preHandle(context, scheduleStore);
        assertEquals(scheduleDeleter, context.getPayer());
        assertEquals(List.of(adminKey), context.getRequiredNonPayerKeys());
        assertEquals(OK, context.getStatus());
    }

    @Test
        // when schedule id to delete is not found, fail with INVALID_SCHEDULE_ID
    void scheduleDeleteFailsIfScheduleMissing() {
        final var txn = scheduleDeleteTransaction();
        scheduledTxn = givenSetupForScheduleDelete(txn);

        final var context = new PreHandleContext(keyLookup, txn, scheduleDeleter);
        subject.preHandle(context, scheduleStore);
        assertEquals(scheduleDeleter, context.getPayer());
        assertEquals(INVALID_SCHEDULE_ID, context.getStatus());
    }

    @Test
        // when admin key not set in scheduled tx, fail with SCHEDULE_IS_IMMUTABLE
    void scheduleDeleteScheduleIsImmutable() {
        final var txn = scheduleDeleteTransaction();
        scheduledTxn = givenSetupForScheduleDelete(txn);
        BDDMockito.given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);

        final var context = new PreHandleContext(keyLookup, txn, scheduleDeleter);
        subject.preHandle(context, scheduleStore);
        assertEquals(scheduleDeleter, context.getPayer());
        assertEquals(SCHEDULE_IS_IMMUTABLE, context.getStatus());
    }

    private TransactionBody givenSetupForScheduleDelete(TransactionBody txn) {
        final TransactionBody scheduledTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler).build())
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().build())
                .build();
        // must be lenient here, because Mockito is a bit too sensitive, and not setting this causes NPE's
        BDDMockito.lenient().when(schedule.ordinaryViewOfScheduledTxn()).thenReturn(PbjConverter.fromPbj(scheduledTxn));
        BDDMockito.given(keyLookup.getKey(scheduleDeleter)).willReturn(KeyOrLookupFailureReason.withKey(adminKey));
        return scheduledTxn;
    }

    private TransactionBody scheduleDeleteTransaction() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduleDeleter))
                .scheduleDelete(ScheduleDeleteTransactionBody.newBuilder().scheduleID(scheduleID))
                .build();
    }
}
