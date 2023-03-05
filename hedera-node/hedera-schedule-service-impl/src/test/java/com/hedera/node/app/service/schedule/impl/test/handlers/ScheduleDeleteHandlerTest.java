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
import static com.hedera.node.app.service.schedule.impl.Utils.asOrdinary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScheduleDeleteHandlerTest extends ScheduleHandlerTestBase {
    private final ScheduleID scheduleID =
            ScheduleID.newBuilder().scheduleNum(100L).build();

    @Mock
    private ScheduleVirtualValue schedule;

    @Mock
    private ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;

    private ReadableScheduleStore scheduleStore;

    private final AccountID scheduleDeleter =
            AccountID.newBuilder().accountNum(3001L).build();

    @BeforeEach
    void setUp() {
        given(states.<Long, ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStore(states);
    }

    private final ScheduleDeleteHandler subject = new ScheduleDeleteHandler();

    @Test
    void scheduleDeleteHappyPath() throws DecoderException {
        final var txn = scheduleDeleteTransaction();
        givenSetupForScheduleDelete(txn);
        given(schedule.adminKey()).willReturn(Optional.of(JKey.mapKey(key)));
        given(keyLookup.getKey(scheduleDeleter)).willReturn(KeyOrLookupFailureReason.withKey(adminKey));
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);

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
        givenSetupForScheduleDelete(txn);

        given(keyLookup.getKey(scheduleDeleter)).willReturn(KeyOrLookupFailureReason.withKey(adminKey));
        final var context = new PreHandleContext(keyLookup, txn, scheduleDeleter);
        subject.preHandle(context, scheduleStore);
        assertEquals(scheduleDeleter, context.getPayer());
        assertEquals(INVALID_SCHEDULE_ID, context.getStatus());
    }

    @Test
    // when admin key not set in scheduled tx, fail with SCHEDULE_IS_IMMUTABLE
    void scheduleDeleteScheduleIsImmutable() {
        final var txn = scheduleDeleteTransaction();
        givenSetupForScheduleDelete(txn);
        given(keyLookup.getKey(scheduleDeleter)).willReturn(KeyOrLookupFailureReason.withKey(adminKey));
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);

        final var context = new PreHandleContext(keyLookup, txn, scheduleDeleter);
        subject.preHandle(context, scheduleStore);
        assertEquals(scheduleDeleter, context.getPayer());
        assertEquals(SCHEDULE_IS_IMMUTABLE, context.getStatus());
    }

    private void givenSetupForScheduleDelete(TransactionBody txn) {
        scheduledTxn = TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(scheduler).build())
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().build())
                .build();
        scheduledMeta = new TransactionMetadata(
                asOrdinary(txn.scheduleCreate().orElseThrow().scheduledTransactionBody(), txn.transactionID()),
                scheduler,
                OK,
                schedulerKey,
                List.of(),
                null,
                List.of());
    }

    private TransactionBody scheduleDeleteTransaction() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduleDeleter))
                .scheduleDelete(ScheduleDeleteTransactionBody.newBuilder().scheduleID(scheduleID))
                .build();
    }
}
