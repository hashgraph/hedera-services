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
import com.hedera.node.app.spi.meta.InvalidTransactionMetadata;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ScheduleDeleteHandlerTest extends ScheduleHandlerTestBase {
    private final ScheduleID scheduleID = ScheduleID.newBuilder().setScheduleNum(100L).build();
    @Mock private ScheduleVirtualValue schedule;
    @Mock private ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;
    private ReadableScheduleStore scheduleStore;

    private final AccountID scheduleDeleter = AccountID.newBuilder().setAccountNum(3001L).build();

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
        given(keyLookup.getKey(scheduleDeleter))
                .willReturn(KeyOrLookupFailureReason.withKey(adminKey));
        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(schedule);

        final var scheduleDeleteMeta =
                subject.preHandle(txn, scheduleDeleter, keyLookup, scheduleStore);
        assertEquals(scheduleDeleter, scheduleDeleteMeta.payer());
        assertEquals(List.of(adminKey), scheduleDeleteMeta.requiredNonPayerKeys());
        assertEquals(OK, scheduleDeleteMeta.status());
    }

    @Test
    // when schedule id to delete is not found, fail with INVALID_SCHEDULE_ID
    void scheduleDeleteFailsIfScheduleMissing() {
        final var txn = scheduleDeleteTransaction();
        givenSetupForScheduleDelete(txn);

        given(keyLookup.getKey(scheduleDeleter))
                .willReturn(KeyOrLookupFailureReason.withKey(adminKey));
        final var scheduleDeleteMeta =
                subject.preHandle(txn, scheduleDeleter, keyLookup, scheduleStore);
        assertEquals(scheduleDeleter, scheduleDeleteMeta.payer());
        assertEquals(INVALID_SCHEDULE_ID, scheduleDeleteMeta.status());
        assertTrue(scheduleDeleteMeta instanceof InvalidTransactionMetadata);
    }

    @Test
    // when admin key not set in scheduled tx, fail with SCHEDULE_IS_IMMUTABLE
    void scheduleDeleteScheduleIsImmutable() {
        final var txn = scheduleDeleteTransaction();
        givenSetupForScheduleDelete(txn);
        given(keyLookup.getKey(scheduleDeleter))
                .willReturn(KeyOrLookupFailureReason.withKey(adminKey));
        given(schedulesById.get(scheduleID.getScheduleNum())).willReturn(schedule);

        final var scheduleDeleteMeta =
                subject.preHandle(txn, scheduleDeleter, keyLookup, scheduleStore);
        assertEquals(scheduleDeleter, scheduleDeleteMeta.payer());
        assertEquals(SCHEDULE_IS_IMMUTABLE, scheduleDeleteMeta.status());
        assertTrue(scheduleDeleteMeta instanceof InvalidTransactionMetadata);
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    }

    private void givenSetupForScheduleDelete(TransactionBody txn) {
        scheduledTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder().setAccountID(scheduler).build())
                        .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
                        .build();
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        schedulerKey,
                        List.of());
    }

    private TransactionBody scheduleDeleteTransaction() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(scheduleDeleter))
                .setScheduleDelete(
                        ScheduleDeleteTransactionBody.newBuilder().setScheduleID(scheduleID))
                .build();
    }
}
