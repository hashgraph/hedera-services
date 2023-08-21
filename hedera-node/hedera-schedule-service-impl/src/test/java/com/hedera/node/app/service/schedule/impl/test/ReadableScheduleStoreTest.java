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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableScheduleStoreTest {
    // spotless mangles this section randomly, due to incorrect wrapping rules
    // spotless:off
    // A few random values for fake ed25519 test keys
    private static final String PAYER_KEY_HEX =
            "badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada";
    private static final String SCHEDULER_KEY_HEX =
            "feedbeadcafe8675309bafedfacecaeddeedcedebede4adaacecab2badcadfad";
    // This one is a perfect 10.
    private static final String ADMIN_KEY_HEX =
            "0000000000191561942608236107294793378084303638130997321548169216";
    private final ScheduleID testScheduleID = ScheduleID.newBuilder().scheduleNum(100L).build();
    private AccountID adminAccount = AccountID.newBuilder().accountNum(626068L).build();
    private Key adminKey = Key.newBuilder().ed25519(Bytes.fromHex(ADMIN_KEY_HEX)).build();
    private AccountID scheduler = AccountID.newBuilder().accountNum(1001L).build();
    private Key schedulerKey = Key.newBuilder().ed25519(Bytes.fromHex(SCHEDULER_KEY_HEX)).build();
    private AccountID payer = AccountID.newBuilder().accountNum(2001L).build();
    private Key payerKey = Key.newBuilder().ed25519(Bytes.fromHex(PAYER_KEY_HEX)).build();
    // spotless:on

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Account schedulerAccount;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Account payerAccount;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableAccountStore accountStore;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableStates states;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableKVStateBase<ScheduleID, Schedule> schedulesById;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Schedule scheduleInState;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableStoreFactory mockStoreFactory;

    // Non-Mock objects, but may contain or reference mock objects.
    private ReadableScheduleStore scheduleStore;
    private SchedulableTransactionBody scheduled;

    @BeforeEach
    void setUp() throws PreCheckException {
        BDDMockito.given(states.<ScheduleID, Schedule>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        BDDMockito.given(schedulerAccount.key()).willReturn(schedulerKey);
        BDDMockito.given(payerAccount.key()).willReturn(payerKey);

        scheduleStore = new ReadableScheduleStoreImpl(states);

        BDDMockito.given(accountStore.getAccountById(scheduler)).willReturn(schedulerAccount);
        BDDMockito.given(accountStore.getAccountById(payer)).willReturn(payerAccount);

        BDDMockito.given(scheduleInState.hasPayerAccountId()).willReturn(Boolean.TRUE);
        BDDMockito.given(scheduleInState.payerAccountId()).willReturn(payer);
        BDDMockito.given(scheduleInState.schedulerAccountId()).willReturn(adminAccount);
        BDDMockito.given(scheduleInState.hasAdminKey()).willReturn(true);
        BDDMockito.given(scheduleInState.adminKey()).willReturn(adminKey);
        BDDMockito.given(scheduleInState.scheduledTransaction()).willReturn(scheduled);

        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(scheduleInState);
        BDDMockito.given(mockStoreFactory.getStore(ReadableScheduleStore.class)).willReturn(scheduleStore);
        BDDMockito.given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void constructorThrowsIfStatesIsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> new ReadableScheduleStoreImpl(null));
    }

    @Test
    void returnsEmptyIfMissingSchedule() {
        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(null);
        Assertions.assertNull(scheduleStore.get(testScheduleID));
    }

    @Test
    void getsScheduleMetaFromFetchedSchedule() {
        final Schedule meta = scheduleStore.get(testScheduleID);
        Assertions.assertNotNull(meta);
        Assertions.assertEquals(adminKey, meta.adminKey());
        Assertions.assertEquals(scheduled, meta.scheduledTransaction());
        Assertions.assertEquals(payer, meta.payerAccountId());
    }

    @Test
    void getsScheduleMetaFromFetchedScheduleNoExplicitPayer() {
        BDDMockito.given(scheduleInState.hasPayerAccountId()).willReturn(false);
        BDDMockito.given(scheduleInState.payerAccountId()).willReturn(null);

        final Schedule meta = scheduleStore.get(testScheduleID);
        Assertions.assertNotNull(meta);
        Assertions.assertEquals(adminKey, meta.adminKey());
        Assertions.assertEquals(scheduled, meta.scheduledTransaction());
        Assertions.assertNull(meta.payerAccountId());
    }
}
