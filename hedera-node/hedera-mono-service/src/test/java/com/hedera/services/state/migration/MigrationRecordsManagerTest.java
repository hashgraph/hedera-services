/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static com.hedera.services.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.state.initialization.TreasuryClonerTest.accountWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.PropertyNames;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.initialization.BackedSystemAccountsCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class MigrationRecordsManagerTest {
    private static final long fundingExpiry = 33197904000L;
    private static final long stakingRewardAccount = 800;
    private static final long nodeRewardAccount = 801;
    private static final ExpirableTxnRecord.Builder pretend200 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend201 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend800 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend801 = ExpirableTxnRecord.newBuilder();

    private static final ExpirableTxnRecord.Builder pretend100 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend101 = ExpirableTxnRecord.newBuilder();
    private static final Instant now = Instant.ofEpochSecond(1_234_567L);
    private static final Key EXPECTED_KEY =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
    private static final String MEMO = "Release 0.24.1 migration record";
    private static final String TREASURY_CLONE_MEMO = "Synthetic zero-balance treasury clone";
    private static final String SYSTEM_ACCOUNT_CREATION_MEMO = "Synthetic system creation";
    private static final long contract1Expiry = 2000000L;
    private static final long contract2Expiry = 4000000L;
    private static final EntityId contract1Id = EntityId.fromIdentityCode(1);
    private static final EntityId contract2Id = EntityId.fromIdentityCode(2);
    private static final EntityId deletedContractId = EntityId.fromIdentityCode(3);
    private static final long pretendExpiry = 2 * now.getEpochSecond();
    private static final JKey pretendTreasuryKey =
            new JEd25519Key("a123456789a123456789a123456789a1".getBytes());
    private static final long systemAccount = 3 * now.getEpochSecond();
    private static final JKey systemAccountKey =
            new JEd25519Key("a123456789a123456789a1234567891011a1".getBytes());
    private final List<HederaAccount> treasuryClones = new ArrayList<>();
    private final List<HederaAccount> systemAccounts = new ArrayList<>();
    private final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
    private final AtomicInteger nextTracker = new AtomicInteger();
    @Mock private BootstrapProperties bootstrapProperties;
    private final SyntheticTxnFactory factory =
            new SyntheticTxnFactory(new MockGlobalDynamicProps());
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ConsensusTimeTracker consensusTimeTracker;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private SideEffectsTracker tracker800;
    @Mock private SideEffectsTracker tracker801;
    @Mock private SideEffectsTracker tracker200;
    @Mock private SideEffectsTracker tracker201;
    @Mock private SideEffectsTracker tracker100;
    @Mock private SideEffectsTracker tracker101;
    @Mock private EntityCreator creator;
    @Mock private AccountNumbers accountNumbers;
    @Mock private BackedSystemAccountsCreator systemAccountsCreator;
    @Mock private MerkleAccount merkleAccount;
    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private MigrationRecordsManager subject;

    @BeforeEach
    void setUp() {
        accounts.put(EntityNum.fromLong(1L), merkleAccount);
        accounts.put(EntityNum.fromLong(2L), merkleAccount);

        subject =
                new MigrationRecordsManager(
                        creator,
                        systemAccountsCreator,
                        sigImpactHistorian,
                        recordsHistorian,
                        () -> networkCtx,
                        consensusTimeTracker,
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        factory,
                        accountNumbers,
                        bootstrapProperties);

        subject.setSideEffectsFactory(
                () ->
                        switch (nextTracker.getAndIncrement()) {
                            case 0 -> tracker800;
                            case 1 -> tracker801;
                            case 2 -> tracker200;
                            case 3 -> tracker201;
                            case 4 -> tracker100;
                            default -> tracker101;
                        });
    }

    @Test
    void streamsSystemAccountCreationRecords() {
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final var rewardSynthBody = expectedSyntheticRewardAccount();
        final var cloneSynthBody = expectedSyntheticTreasuryClone();
        final var systemAccountSynthBody = expectedSystemAccountCreationSynthBody();

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker800, MEMO))
                .willReturn(pretend800);
        given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker801, MEMO))
                .willReturn(pretend801);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES, tracker200, TREASURY_CLONE_MEMO))
                .willReturn(pretend200);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES, tracker201, TREASURY_CLONE_MEMO))
                .willReturn(pretend201);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES, tracker100, SYSTEM_ACCOUNT_CREATION_MEMO))
                .willReturn(pretend100);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES, tracker101, SYSTEM_ACCOUNT_CREATION_MEMO))
                .willReturn(pretend101);

        given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccount);
        given(accountNumbers.nodeRewardAccount()).willReturn(nodeRewardAccount);
        givenSomeTreasuryClones();
        givenSystemAccountsCreated();

        subject.publishMigrationRecords(now);

        verify(sigImpactHistorian).markEntityChanged(800L);
        verify(sigImpactHistorian).markEntityChanged(801L);
        verify(tracker800)
                .trackAutoCreation(
                        AccountID.newBuilder().setAccountNum(800L).build(), ByteString.EMPTY);
        verify(tracker801)
                .trackAutoCreation(
                        AccountID.newBuilder().setAccountNum(801L).build(), ByteString.EMPTY);
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend800));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend801));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend200));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend201));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend100));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend101));
        verify(networkCtx).markMigrationRecordsStreamed();
        verify(systemAccountsCreator).forgetCreations();
        final var bodies = bodyCaptor.getAllValues();
        assertEquals(rewardSynthBody, bodies.get(0).build());
        assertEquals(rewardSynthBody, bodies.get(1).build());
        assertEquals(cloneSynthBody, bodies.get(2).build());
        assertEquals(cloneSynthBody, bodies.get(3).build());
        assertEquals(systemAccountSynthBody, bodies.get(4).build());
        assertEquals(systemAccountSynthBody, bodies.get(5).build());
    }

    @Test
    void ifContextIndicatesRecordsNeedToBeStreamedThenDoesSo() {
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final var rewardSynthBody = expectedSyntheticRewardAccount();
        final var cloneSynthBody = expectedSyntheticTreasuryClone();

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker800, MEMO))
                .willReturn(pretend800);
        given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker801, MEMO))
                .willReturn(pretend801);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES, tracker200, TREASURY_CLONE_MEMO))
                .willReturn(pretend200);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES, tracker201, TREASURY_CLONE_MEMO))
                .willReturn(pretend201);
        given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccount);
        given(accountNumbers.nodeRewardAccount()).willReturn(nodeRewardAccount);
        givenSomeTreasuryClones();

        subject.publishMigrationRecords(now);

        verify(sigImpactHistorian).markEntityChanged(800L);
        verify(sigImpactHistorian).markEntityChanged(801L);
        verify(tracker800)
                .trackAutoCreation(
                        AccountID.newBuilder().setAccountNum(800L).build(), ByteString.EMPTY);
        verify(tracker801)
                .trackAutoCreation(
                        AccountID.newBuilder().setAccountNum(801L).build(), ByteString.EMPTY);
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend800));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend801));
        verify(networkCtx).markMigrationRecordsStreamed();
        verify(systemAccountsCreator).forgetCreations();
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend200));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend201));
        final var bodies = bodyCaptor.getAllValues();
        assertEquals(rewardSynthBody, bodies.get(0).build());
        assertEquals(rewardSynthBody, bodies.get(1).build());
        assertEquals(cloneSynthBody, bodies.get(2).build());
        assertEquals(cloneSynthBody, bodies.get(3).build());
    }

    @Test
    void ifContextIndicatesContractRenewRecordsNeedToBeStreamedThenDoesSo() {
        registerConstructables();

        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final ArgumentCaptor<ExpirableTxnRecord.Builder> recordCaptor =
                forClass(ExpirableTxnRecord.Builder.class);

        accounts.clear();
        accounts.put(
                contract1Id.asNum(),
                MerkleAccountFactory.newContract().expirationTime(contract1Expiry).get());
        accounts.put(
                contract2Id.asNum(),
                MerkleAccountFactory.newContract().expirationTime(contract2Expiry).get());
        accounts.put(
                deletedContractId.asNum(), MerkleAccountFactory.newContract().deleted(true).get());
        final var contractUpdateSynthBody1 =
                factory.synthContractAutoRenew(contract1Id.asNum(), contract1Expiry).build();
        final var contractUpdateSynthBody2 =
                factory.synthContractAutoRenew(contract2Id.asNum(), contract2Expiry).build();

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        givenFreeRenewalsOnly();

        subject.publishMigrationRecords(now);

        verify(recordsHistorian, times(2))
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), recordCaptor.capture());
        verify(networkCtx).markMigrationRecordsStreamed();

        final var bodies = bodyCaptor.getAllValues();
        assertEquals(contractUpdateSynthBody1, bodies.get(0).build());
        assertEquals(contractUpdateSynthBody2, bodies.get(1).build());

        final var records = recordCaptor.getAllValues();
        // since txnId will be set at late point, will set txnId for comparing
        assertEquals(
                expectedContractUpdateRecord(contract1Id, contract1Expiry).build(),
                records.get(0).setTxnId(new TxnId()).build());
        assertEquals(
                expectedContractUpdateRecord(contract2Id, contract2Expiry).build(),
                records.get(1).setTxnId(new TxnId()).build());
    }

    @Test
    void ifExpiryNotJustEnabledThenContractRenewRecordsAreNotStreamed() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);

        subject.publishMigrationRecords(now);

        verifyNoInteractions(recordsHistorian);
    }

    private ExpirableTxnRecord.Builder expectedContractUpdateRecord(
            final EntityId num, final long newExpiry) {
        final var receipt = TxnReceipt.newBuilder().setStatus(SUCCESS_LITERAL).build();

        final var memo =
                String.format(MigrationRecordsManager.AUTO_RENEW_MEMO_TPL, num.num(), newExpiry);
        return ExpirableTxnRecord.newBuilder()
                .setTxnId(new TxnId())
                .setMemo(memo)
                .setReceipt(receipt);
    }

    @Test
    void doesNothingIfRecordsAlreadyStreamed() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(false);

        subject.publishMigrationRecords(now);

        given(networkCtx.areMigrationRecordsStreamed()).willReturn(true);
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);

        subject.publishMigrationRecords(now);

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(false);

        subject.publishMigrationRecords(now);

        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
        verify(networkCtx, never()).consensusTimeOfLastHandledTxn();
        verify(networkCtx, never()).markMigrationRecordsStreamed();
    }

    @Test
    void doesntStreamRewardAccountCreationIfNotGenesis() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(Instant.MAX);

        subject.publishMigrationRecords(now);

        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
    }

    private TransactionBody expectedSyntheticRewardAccount() {
        final var txnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(EXPECTED_KEY)
                        .setMemo(EMPTY_MEMO)
                        .setInitialBalance(0)
                        .setAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(fundingExpiry - now.getEpochSecond()))
                        .build();
        return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody).build();
    }

    private TransactionBody expectedSystemAccountCreationSynthBody() {
        final var txnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(MiscUtils.asKeyUnchecked(systemAccountKey))
                        .setMemo("123")
                        .setInitialBalance(0)
                        .setReceiverSigRequired(true)
                        .setAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(systemAccount - now.getEpochSecond()))
                        .build();
        return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody).build();
    }

    private TransactionBody expectedSyntheticTreasuryClone() {
        final var txnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(MiscUtils.asKeyUnchecked(pretendTreasuryKey))
                        .setMemo("123")
                        .setInitialBalance(0)
                        .setReceiverSigRequired(true)
                        .setAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(pretendExpiry - now.getEpochSecond()))
                        .build();
        return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody).build();
    }

    private void givenSomeTreasuryClones() {
        treasuryClones.addAll(
                List.of(
                        accountWith(pretendExpiry, pretendTreasuryKey),
                        accountWith(pretendExpiry, pretendTreasuryKey)));
        treasuryClones.get(0).setEntityNum(EntityNum.fromLong(200L));
        treasuryClones.get(1).setEntityNum(EntityNum.fromLong(201L));
        given(systemAccountsCreator.getTreasuryClonesCreated()).willReturn(treasuryClones);
    }

    private void givenSystemAccountsCreated() {
        systemAccounts.addAll(
                List.of(
                        accountWith(systemAccount, systemAccountKey),
                        accountWith(systemAccount, systemAccountKey)));
        systemAccounts.get(0).setEntityNum(EntityNum.fromLong(100L));
        systemAccounts.get(1).setEntityNum(EntityNum.fromLong(101L));
        given(systemAccountsCreator.getSystemAccountsCreated()).willReturn(systemAccounts);
    }

    private void givenFreeRenewalsOnly() {
        given(bootstrapProperties.getBooleanProperty(PropertyNames.AUTO_RENEW_GRANT_FREE_RENEWALS))
                .willReturn(true);
    }

    private void registerConstructables() {
        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleMap.class, MerkleMap::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleBinaryTree.class, MerkleBinaryTree::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
        } catch (ConstructableRegistryException e) {
            throw new IllegalStateException(e);
        }
    }
}
