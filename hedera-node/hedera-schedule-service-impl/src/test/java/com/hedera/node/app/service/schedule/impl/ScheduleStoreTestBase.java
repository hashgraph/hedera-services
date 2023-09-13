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

package com.hedera.node.app.service.schedule.impl;

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
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.InvalidKeyException;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class ScheduleStoreTestBase {
    // A few random values for fake ed25519 test keys
    protected static final Bytes PAYER_KEY_HEX =
            Bytes.fromHex("badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada");
    protected static final Bytes SCHEDULER_KEY_HEX =
            Bytes.fromHex("feedbeadcafe8675309bafedfacecaeddeedcedebede4adaacecab2badcadfad");
    // This one is a perfect 10.
    protected static final Bytes ADMIN_KEY_HEX =
            Bytes.fromHex("0000000000191561942608236107294793378084303638130997321548169216");
    // A few random values for fake ed25519 test keys
    protected static final String SCHEDULE_IN_STATE_SHA256 =
            "6a78609f84e64fbd721b9100e6e3324fb74bb5b8a2ded24391571321c8c95760";
    protected static final String SCHEDULE_IN_STATE_0_EXPIRE_SHA256 =
            "8901af793bb2c7a41664ccd0642db62851347240683e5b3050f23e2c852c94e7";
    protected static final String SCHEDULE_IN_STATE_PAYER_IS_ADMIN_SHA256 =
            "1a22f9a6657aa6d74f30be868f2bbca622cd6770822a5ad4811df6412b562dcd";
    protected static final String SCHEDULE_IN_STATE_ALTERNATE_SCHEDULED_SHA256 =
            "d27438c485f25cdd1d3f83938ae10336c39608f7f97298f616ca7b7cfac555ae";
    protected static final String SCHEDULE_IN_STATE_ADMIN_IS_PAYER_SHA256 =
            "9a16ae6a82e2c92840bcfc6adf899c9ae40563952b46f392bd7cc5663f9247ca";
    protected static final String SCHEDULE_IN_STATE_PAYER_IS_SCHEDULER_SHA256 =
            "76e8c1f149fd36f6fed3344c47114f59bdbba1170adc4496ca10a77a1911d2f4";
    protected static final String SCHEDULE_IN_STATE_ODD_MEMO_SHA256 =
            "c6fb52659ffc491d4b0e31e94c8377382f970b9b5f892582575bdb6b9e9aa9c3";
    protected static final String SCHEDULE_IN_STATE_WAIT_EXPIRE_SHA256 =
            "fa853a75922546a0cb14ca1324ed1b3e6db6ba16c843593d782f2a0328b64a1f";
    protected static final String SCHEDULED_TRANSACTION_MEMO = "Les ħ2ᛏᚺᛂ🌕 goo";
    protected static final String ODD_MEMO = "she had marvelous judgement, Don... if not particularly good taste.";
    // spotless mangles this section randomly, due to unstable wrapping rules
    // spotless:off
    protected final ScheduleID testScheduleID = ScheduleID.newBuilder().scheduleNum(100L).build();
    protected final AccountID adminAccountId = AccountID.newBuilder().accountNum(626068L).build();
    protected final Key adminKey = Key.newBuilder().ed25519(ADMIN_KEY_HEX).build();
    protected final Key schedulerKey = Key.newBuilder().ed25519(SCHEDULER_KEY_HEX).build();
    protected final Key payerKey = Key.newBuilder().ed25519(PAYER_KEY_HEX).build();
    protected final AccountID scheduler = AccountID.newBuilder().accountNum(1001L).build();
    protected final AccountID payerAccountId = AccountID.newBuilder().accountNum(2001L).build();
    protected final List<Key> alternateSignatories = List.of(payerKey, adminKey, schedulerKey);
    protected final Timestamp testValidStart = Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected final String memo = "Test";
    // Note, many tests assume that these are the same as validStart, which is unfortunate.
    protected final Timestamp expirationTime = Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected final Timestamp calculatedExpirationTime = Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected final Timestamp modifiedResolutionTime = new Timestamp(18601220L, 18030109);
    protected final Timestamp modifiedStartTime = new Timestamp(18601220L, 18030109);
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

    @Mock
    protected HandleContext mockContext;

    // Spied data object, to allow for per-test data adjustments
    protected Schedule scheduleInState;

    // Non-Mock objects, but may contain or reference mock objects.
    protected ReadableScheduleStore scheduleStore;
    protected Configuration testConfig;
    protected SchedulableTransactionBody scheduled;
    protected TransactionBody originalCreateTransaction;
    protected TransactionBody alternateCreateTransaction;

    protected void setUpBase() throws PreCheckException, InvalidKeyException {
        testConfig = HederaTestConfigBuilder.create().getOrCreateConfig();
        scheduled = createSampleScheduled();
        originalCreateTransaction = originalCreateTransaction(scheduled, scheduler, adminKey);
        alternateCreateTransaction = alternateCreateTransaction(originalCreateTransaction);
        BDDMockito.given(states.<ScheduleID, Schedule>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        BDDMockito.given(schedulerAccount.key()).willReturn(schedulerKey);
        BDDMockito.given(payerAccount.key()).willReturn(payerKey);

        scheduleStore = new ReadableScheduleStoreImpl(states);

        BDDMockito.given(accountStore.getAccountById(scheduler)).willReturn(schedulerAccount);
        BDDMockito.given(accountStore.getAccountById(payerAccountId)).willReturn(payerAccount);

        final Schedule.Builder builder = Schedule.newBuilder();
        builder.payerAccountId(payerAccountId).schedulerAccountId(scheduler).adminKey(adminKey);
        builder.scheduledTransaction(scheduled);
        builder.originalCreateTransaction(originalCreateTransaction);
        builder.providedExpirationSecond(18651206L).deleted(false).executed(false);
        builder.memo("test schedule");
        scheduleInState = Mockito.spy(builder.build());
        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(scheduleInState);

        BDDMockito.given(mockStoreFactory.getStore(ReadableScheduleStore.class)).willReturn(scheduleStore);
        BDDMockito.given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
    }

    private static SchedulableTransactionBody createSampleScheduled() {
        final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder())
                .build();
        return scheduledTxn;
    }

    protected static SchedulableTransactionBody createAlternateScheduled() {
        final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
                .tokenBurn(TokenBurnTransactionBody.newBuilder())
                .build();
        return scheduledTxn;
    }

    protected TransactionBody alternateCreateTransaction(final TransactionBody originalTransaction) {
        return TransactionBody.newBuilder()
                .transactionID(originalTransaction.transactionID())
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder())
                .build();
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
                .payerAccountID(payerAccountId)
                .waitForExpiry(true)
                .expirationTime(expirationTime)
                .adminKey(adminKey)
                .memo(memo);
        if (explicitPayer != null) builder.payerAccountID(explicitPayer);
        if (adminKey != null) builder.adminKey(adminKey);
        return TransactionBody.newBuilder()
                .transactionID(createdTransactionId)
                .scheduleCreate(builder)
                .build();
    }
}
