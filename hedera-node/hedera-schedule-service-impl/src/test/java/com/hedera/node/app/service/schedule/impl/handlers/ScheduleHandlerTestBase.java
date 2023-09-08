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

package com.hedera.node.app.service.schedule.impl.handlers;

import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractDeleteTransactionBody;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.file.FileDeleteTransactionBody;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody.Builder;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.token.TokenDeleteTransactionBody;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.config.data.SchedulingConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.Pair;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class ScheduleHandlerTestBase {
    // spotless mangles this section randomly, due to incorrect wrapping rules
    // spotless:off
    private static final ScheduleID ALL_SCHEDULES_ID =
            ScheduleID.newBuilder().shardNum(12).realmNum(6).scheduleNum(1865).build();
    // A few random values for fake ed25519 test keys
    protected static final Bytes PAYER_KEY_HEX =
            Bytes.fromHex("badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada");
    protected static final Bytes SCHEDULER_KEY_HEX =
            Bytes.fromHex("feedbeadcafe8675309bafedfacecaeddeedcedebede4adaacecab2badcadfad");
    // This one is a perfect 10.
    protected static final Bytes ADMIN_KEY_HEX =
            Bytes.fromHex("0000000000191561942608236107294793378084303638130997321548169216");
    protected static final Bytes OPTION_KEY_HEX =
            Bytes.fromHex("9834701927540926570495640961948794713207439248567184729049081327");
    protected static final Bytes OTHER_KEY_HEX =
            Bytes.fromHex("983470192754092657adbdbeef61948794713207439248567184729049081327");
    protected final ScheduleID testScheduleID = ScheduleID.newBuilder().scheduleNum(100L).build();
    protected final AccountID adminAccount = AccountID.newBuilder().accountNum(626068L).build();
    protected final Key adminKey = Key.newBuilder().ed25519(ADMIN_KEY_HEX).build();
    protected final AccountID scheduler = AccountID.newBuilder().accountNum(1001L).build();
    protected final Key schedulerKey = Key.newBuilder().ed25519(SCHEDULER_KEY_HEX).build();
    protected final AccountID payer = AccountID.newBuilder().accountNum(2001L).build();
    protected final Key payerKey = Key.newBuilder().ed25519(PAYER_KEY_HEX).build();
    protected final Key optionKey = Key.newBuilder().ed25519(OPTION_KEY_HEX).build();
    protected final Key otherKey = Key.newBuilder().ed25519(OTHER_KEY_HEX).build();
    protected final Timestamp testValidStart =
            Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected final Instant testConsensusTime = Instant.ofEpochSecond(1656087862L, 1221973L);
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

    @Mock // This is only setup per-test, and should remain strict
    protected HandleContext mockContext;

    // Non-Mock objects, but may contain or reference mock objects.
    protected Schedule scheduleInState;
    protected ReadableScheduleStore scheduleStore;
    protected Configuration testConfig;
    protected SchedulingConfig scheduleConfig;
    protected SchedulableTransactionBody scheduled;
    protected TransactionBody originalCreateTransaction;
    protected List<Schedule> listOfScheduledOptions;

    protected void setUpBase() throws PreCheckException, InvalidKeyException {
        testConfig = HederaTestConfigBuilder.create().getOrCreateConfig();
        scheduleConfig = testConfig.getConfigData(SchedulingConfig.class);
        scheduled = createSampleScheduled();
        originalCreateTransaction = originalCreateTransaction(scheduled, scheduler, adminKey);
        listOfScheduledOptions =
                createAllScheduled(originalCreateTransaction, payer, testConsensusTime, scheduleConfig);
        given(states.<ScheduleID, Schedule>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        given(schedulerAccount.key()).willReturn(schedulerKey);
        given(payerAccount.key()).willReturn(payerKey);

        scheduleStore = new ReadableScheduleStoreImpl(states);

        given(accountStore.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(accountStore.getAccountById(payer)).willReturn(payerAccount);

        final Schedule.Builder builder = Schedule.newBuilder();
        builder.payerAccountId(payer).schedulerAccountId(scheduler).adminKey(adminKey);
        builder.scheduledTransaction(scheduled).originalCreateTransaction(originalCreateTransaction);
        builder.deleted(false).executed(false).memo("test schedule");
        scheduleInState = Mockito.spy(builder.build());
        given(schedulesById.get(testScheduleID)).willReturn(scheduleInState);

        given(mockStoreFactory.getStore(ReadableScheduleStore.class)).willReturn(scheduleStore);
        given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
    }

    private static SchedulableTransactionBody createSampleScheduled() {
        final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().build())
                .build();
        return scheduledTxn;
    }

    /**
     * Create a large array of potential scheduled transactions with every possible "child" transaction type.
     * <p>
     * This method has some initial complexity because each {@link Schedule} produced must contain an original
     * ScheduleCreate transaction that matches, in every respect, the schedule and the child transaction.
     * Partly this is to support testing schedule creation, but also it supports the need to match the existing
     * mono service which stores the original create transaction and fills in many schedule values from that
     * transaction on every deserialization from state.
     * <p>
     * The remainder of the method is creating empty child transactions, adding each to the base schedule builder,
     * building the resultant schedule (all identical except for child transaction and create transaction), and adding
     * that schedule to the output list.
     *
     * @param createTransaction the base original create transaction to use.  Several values are read from this
     *     entry, including transaction ID values, and the transaction body is added to the Schedule as well.
     * @param childPayer The payer to use for the child transaction
     * @param consensusTime The consensus time used to create the various schedules
     * @param scheduleConfig The current {@link SchedulingConfig}, typically this is default values returned from the
     *     test configuration builder.
     * @return a {@link List<Schedule>} filled with one Schedule for each possible child transaction type.
     */
    // palantir spotless config hates fluent API calls; forces them to wrap after 72 columns, which mangles this code.
    // spotless:off
    private List<Schedule> createAllScheduled(
            final TransactionBody createTransaction,
            final AccountID childPayer,
            final Instant consensusTime,
            final SchedulingConfig scheduleConfig) {
        final List<Schedule> listOfOptions = new LinkedList<>();
        final TransactionBody.Builder originBuilder = createTransaction.copyBuilder();
        ScheduleCreateTransactionBody.Builder modifiedCreate = createTransaction.scheduleCreateOrThrow().copyBuilder();
        final Pair<Schedule.Builder, Builder> builders =
                createScheduleBuilders(createTransaction, childPayer, consensusTime, scheduleConfig, ALL_SCHEDULES_ID);
        final Schedule.Builder builder = builders.left();
        final Builder childBuilder = builders.right();

        childBuilder.consensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.consensusUpdateTopic(ConsensusUpdateTopicTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.consensusDeleteTopic(ConsensusDeleteTopicTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.cryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.cryptoTransfer(CryptoTransferTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.cryptoDelete(CryptoDeleteTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.fileCreate(FileCreateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.fileAppend(FileAppendTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.fileUpdate(FileUpdateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.fileDelete(FileDeleteTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.contractCreateInstance(ContractCreateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.contractUpdateInstance(ContractUpdateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.contractCall(ContractCallTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.contractDeleteInstance(ContractDeleteTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.systemDelete(SystemDeleteTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.systemUndelete(SystemUndeleteTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.freeze(FreezeTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenCreation(TokenCreateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenFreeze(TokenFreezeAccountTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenUnfreeze(TokenUnfreezeAccountTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenGrantKyc(TokenGrantKycTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenDeletion(TokenDeleteTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenUpdate(TokenUpdateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenMint(TokenMintTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenBurn(TokenBurnTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenWipe(TokenWipeAccountTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenAssociate(TokenAssociateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenDissociate(TokenDissociateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.scheduleDelete(ScheduleDeleteTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenPause(TokenPauseTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenUnpause(TokenUnpauseTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.cryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.cryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);
        childBuilder.utilPrng(UtilPrngTransactionBody.newBuilder());
        addNextItem(listOfOptions, builder, originBuilder, modifiedCreate, childBuilder);

        return listOfOptions;
    }

    protected static Pair<Schedule.Builder, Builder> createScheduleBuilders(
            final TransactionBody createTransaction,
            final AccountID childPayer,
            final Instant consensusTime,
            final SchedulingConfig scheduleConfig,
            final ScheduleID scheduleIdToUse) {
        final long expirationSecond = consensusTime.getEpochSecond() + scheduleConfig.maxExpirationFutureSeconds();
        final Schedule.Builder builder = Schedule.newBuilder();
        builder.scheduleId(scheduleIdToUse);
        builder.originalCreateTransaction(createTransaction);
        builder.memo(createTransaction.memo());
        builder.calculatedExpirationSecond(expirationSecond);
        builder.scheduleValidStart(createTransaction.transactionID().transactionValidStart());
        final ScheduleCreateTransactionBody originalCreate = createTransaction.scheduleCreateOrThrow();
        builder.adminKey(originalCreate.adminKey());
        builder.payerAccountId(originalCreate.payerAccountIDOrElse(childPayer));
        builder.schedulerAccountId(originalCreate.payerAccountID());
        final Builder childBuilder = SchedulableTransactionBody.newBuilder();
        childBuilder.memo("Scheduled by %s.".formatted(createTransaction.memo()));
        childBuilder.transactionFee(createTransaction.transactionFee());
        return new Pair<>(builder, childBuilder);
    }

    private static void addNextItem(final List<Schedule> listOfOptions, final Schedule.Builder builder,
            final TransactionBody.Builder originBuilder, final ScheduleCreateTransactionBody.Builder modifiedCreate,
            final Builder childBuilder) {
        modifiedCreate.scheduledTransactionBody(childBuilder);
        builder.originalCreateTransaction(originBuilder.scheduleCreate(modifiedCreate));
        listOfOptions.add(builder.scheduledTransaction(childBuilder).build());
    }
    // spotless:on

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
                .transactionFee(12231913L)
                .scheduleCreate(scheduleCreate)
                .build();
    }
}
