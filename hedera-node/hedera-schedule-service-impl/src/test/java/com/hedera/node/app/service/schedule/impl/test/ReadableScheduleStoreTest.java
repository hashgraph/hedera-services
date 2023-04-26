/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.test;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class ReadableScheduleStoreTest {
    @Mock
    ReadableStates states;

    @Mock
    ReadableKVState state;

    @Mock
    ScheduleVirtualValue schedule;

    private ReadableScheduleStore subject;
    private Key adminKey = A_COMPLEX_KEY;
    private HederaKey adminHederaKey = asHederaKey(adminKey).get();

    @BeforeEach
    void setUp() {
        given(states.get("SCHEDULES_BY_ID")).willReturn(state);
        subject = new ReadableScheduleStore(states);
    }

    @Test
    void constructorThrowsIfStatesIsNull() {
        assertThrows(NullPointerException.class, () -> new ReadableScheduleStore(null));
    }

    @Test
    void returnsEmptyIfMissingSchedule() {
        given(state.get(1L)).willReturn(null);

        assertEquals(
                Optional.empty(),
                subject.get(ScheduleID.newBuilder().scheduleNum(1L).build()));
    }

    @Test
    void getsScheduleMetaFromFetchedSchedule() {
        given(state.get(1L)).willReturn(schedule);
        given(schedule.ordinaryViewOfScheduledTxn())
                .willReturn(PbjConverter.fromPbj(TransactionBody.newBuilder().build()));
        given(schedule.hasAdminKey()).willReturn(true);
        given(schedule.adminKey()).willReturn(Optional.of((JKey) adminHederaKey));
        given(schedule.hasExplicitPayer()).willReturn(true);
        given(schedule.payer()).willReturn(EntityId.fromNum(2L));

        final var meta = subject.get(ScheduleID.newBuilder().scheduleNum(1L).build());

        assertEquals(adminKey, meta.get().adminKey());
        assertEquals(TransactionBody.newBuilder().build(), meta.get().scheduledTxn());
        assertEquals(
                Optional.of(EntityId.fromNum(2L).toPbjAccountId()), meta.get().designatedPayer());
    }

    @Test
    void getsScheduleMetaFromFetchedScheduleNoExplicitPayer() {
        given(state.get(1L)).willReturn(schedule);
        given(schedule.ordinaryViewOfScheduledTxn())
                .willReturn(PbjConverter.fromPbj(TransactionBody.newBuilder().build()));
        given(schedule.adminKey()).willReturn(Optional.of((JKey) adminHederaKey));
        given(schedule.hasAdminKey()).willReturn(true);
        given(schedule.hasExplicitPayer()).willReturn(false);

        final var meta = subject.get(ScheduleID.newBuilder().scheduleNum(1L).build());

        assertEquals(adminKey, meta.get().adminKey());
        assertEquals(TransactionBody.newBuilder().build(), meta.get().scheduledTxn());
        assertEquals(Optional.empty(), meta.get().designatedPayer());
    }
}
