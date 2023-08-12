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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.InvalidKeyException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@SuppressWarnings("NewClassNamingConvention")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class ScheduleHandlerTestBase {
    // spotless mangles this section randomly, due to incorrect wrapping rules
    // spotless:off
    // A few random values for fake ed25519 test keys
    protected static final String PAYER_KEY_HEX =
            "badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada";
    protected static final String SCHEDULER_KEY_HEX =
            "feedbeadcafe8675309bafedfacecaeddeedcedebede4adaacecab2badcadfad";
    // This one is a perfect 10.
    protected static final String ADMIN_KEY_HEX =
            "0000000000191561942608236107294793378084303638130997321548169216";
    protected final ScheduleID testScheduleID = ScheduleID.newBuilder().scheduleNum(100L).build();
    protected AccountID adminAccount = AccountID.newBuilder().accountNum(626068L).build();
    protected Key adminKey = Key.newBuilder().ed25519(Bytes.fromHex(ADMIN_KEY_HEX)).build();
    protected AccountID scheduler = AccountID.newBuilder().accountNum(1001L).build();
    protected Key schedulerKey = Key.newBuilder().ed25519(Bytes.fromHex(SCHEDULER_KEY_HEX)).build();
    protected AccountID payer = AccountID.newBuilder().accountNum(2001L).build();
    protected Key payerKey = Key.newBuilder().ed25519(Bytes.fromHex(PAYER_KEY_HEX)).build();
    protected Timestamp testValidStart = Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected Schedule scheduleInState;
    // spotless:on

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected Account schedulerAccount;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected Account payerAccount;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableAccountStore accountStore;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableStates states;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableKVStateBase<ScheduleID, Schedule> schedulesById;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableStoreFactory mockStoreFactory;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected TransactionDispatcher mockDispatcher;

    // Non-Mock objects, but may contain or reference mock objects.
    protected ReadableScheduleStore scheduleStore;
    protected Configuration testConfig;
    protected SchedulableTransactionBody scheduled;
    protected TransactionBody originalCreateTransaction;

    protected void setUpBase() throws PreCheckException, InvalidKeyException {
        testConfig = HederaTestConfigBuilder.create().getOrCreateConfig();
        scheduled = createSampleScheduled();
        originalCreateTransaction = originalCreateTransaction(scheduled, scheduler, adminKey);
        BDDMockito.given(states.<ScheduleID, Schedule>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        BDDMockito.given(schedulerAccount.key()).willReturn(schedulerKey);
        BDDMockito.given(payerAccount.key()).willReturn(payerKey);

        scheduleStore = new ReadableScheduleStoreImpl(states);

        BDDMockito.given(accountStore.getAccountById(scheduler)).willReturn(schedulerAccount);
        BDDMockito.given(accountStore.getAccountById(payer)).willReturn(payerAccount);

        final Schedule.Builder builder = Schedule.newBuilder();
        builder.payerAccountId(payer).schedulerAccountId(scheduler).adminKey(adminKey);
        builder.scheduledTransaction(scheduled).originalCreateTransaction(originalCreateTransaction);
        builder.deleted(false).executed(false).memo("test schedule");
        scheduleInState = Mockito.spy(builder.build());
        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(scheduleInState);

        BDDMockito.given(mockStoreFactory.getStore(ReadableScheduleStore.class)).willReturn(scheduleStore);
        BDDMockito.given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
    }

    private static SchedulableTransactionBody createSampleScheduled() {
        final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().build())
                .build();
        return scheduledTxn;
    }

    protected TransactionBody originalCreateTransaction(
            @NonNull final SchedulableTransactionBody childTransaction,
            @Nullable final AccountID explicitPayer,
            @Nullable final Key adminKey) {
        final TransactionID createdTransactionId = TransactionID.newBuilder()
                .accountID(scheduler)
                .transactionValidStart(testValidStart)
                .nonce(4444)
                .scheduled(false)
                .build();
        final ScheduleCreateTransactionBody.Builder builder = ScheduleCreateTransactionBody.newBuilder()
                .scheduledTransactionBody(childTransaction)
                .payerAccountID(scheduler);
        if (explicitPayer != null) builder.payerAccountID(explicitPayer);
        if (adminKey != null) builder.adminKey(adminKey);
        final ScheduleCreateTransactionBody scheduleCreate = builder.build();
        return TransactionBody.newBuilder()
                .transactionID(createdTransactionId)
                .scheduleCreate(scheduleCreate)
                .build();
    }
}
