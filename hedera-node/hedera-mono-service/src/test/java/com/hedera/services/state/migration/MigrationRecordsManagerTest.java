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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.initialization.TreasuryCloner;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.SidecarUtils;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hamcrest.Matchers;
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
    private static final Instant now = Instant.ofEpochSecond(1_234_567L);
    private static final Key EXPECTED_KEY =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
    private static final String MEMO = "Release 0.24.1 migration record";
    private static final long contract1Expiry = 2000000L;
    private static final long contract2Expiry = 4000000L;
    private static final EntityId contract1Id = EntityId.fromIdentityCode(1);
    private static final EntityId contract2Id = EntityId.fromIdentityCode(2);
    private static final long pretendExpiry = 2 * now.getEpochSecond();
    private static final String pretendMemo = "WHATEVER";
    private static final JKey pretendTreasuryKey =
            new JEd25519Key("a123456789a123456789a123456789a1".getBytes());
    private final List<MerkleAccount> treasuryClones = new ArrayList<>();
    private final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
    private final AtomicInteger nextTracker = new AtomicInteger();
    @Mock private GlobalDynamicProperties dynamicProperties;
    private final SyntheticTxnFactory factory = new SyntheticTxnFactory(dynamicProperties);
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ConsensusTimeTracker consensusTimeTracker;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private SideEffectsTracker tracker800;
    @Mock private SideEffectsTracker tracker801;
    @Mock private SideEffectsTracker tracker200;
    @Mock private SideEffectsTracker tracker201;
    @Mock private EntityCreator creator;
    @Mock private AccountNumbers accountNumbers;
    @Mock private TreasuryCloner treasuryCloner;
    @Mock private MerkleAccount merkleAccount;
    @Mock private TransactionContext transactionContext;
    @Mock private VirtualMap<ContractKey, IterableContractValue> contractStorage;
    @Mock private EntityAccess entityAccess;
    @Mock private TxnAccessor txnAccessor;
    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private MigrationRecordsManager subject;

    @BeforeEach
    void setUp() {
        accounts.put(EntityNum.fromLong(1L), merkleAccount);
        accounts.put(EntityNum.fromLong(2L), merkleAccount);

        subject =
                new MigrationRecordsManager(
                        creator,
                        treasuryCloner,
                        sigImpactHistorian,
                        recordsHistorian,
                        () -> networkCtx,
                        consensusTimeTracker,
                        () -> accounts,
                        factory,
                        accountNumbers,
                        transactionContext,
                        dynamicProperties,
                        () -> contractStorage,
                        entityAccess);

        subject.setSideEffectsFactory(
                () ->
                        switch (nextTracker.getAndIncrement()) {
                            case 0 -> tracker800;
                            case 1 -> tracker801;
                            case 2 -> tracker200;
                            default -> tracker201;
                        });
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
                                NO_CUSTOM_FEES,
                                tracker200,
                                "Synthetic zero-balance treasury clone"))
                .willReturn(pretend200);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES,
                                tracker201,
                                "Synthetic zero-balance treasury clone"))
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
        verify(treasuryCloner).forgetScannedSystemAccounts();
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
        assertTrue(subject.areTraceabilityRecordsStreamed());
    }

    @Test
    void ifTransactionIsContractCallThenDontPerformTraceabilityMigration() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.ContractCall);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);

        subject.publishMigrationRecords(now);

        assertFalse(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext, never()).addSidecarRecord(any());
    }

    @Test
    void ifTransactionIsEthereumTxnThenDontPerformTraceabilityMigration() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.EthereumTransaction);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);

        subject.publishMigrationRecords(now);

        assertFalse(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext, never()).addSidecarRecord(any());
    }

    @Test
    void ifTransactionIsContractCreateThenDontPerformTraceabilityMigration() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.ContractCreate);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);

        subject.publishMigrationRecords(now);

        assertFalse(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext, never()).addSidecarRecord(any());
    }

    @Test
    void ifContextIndicatesTraceabilityMigrationNeedsToBeStreamedThenDoesSo() {
        final ArgumentCaptor<TransactionSidecarRecord.Builder> sidecarCaptor =
                forClass(TransactionSidecarRecord.Builder.class);
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.ConsensusCreateTopic);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);
        accounts.clear();
        // mock contract with 4 slots
        final var contract1 = mock(MerkleAccount.class);
        given(contract1.isSmartContract()).willReturn(true);
        given(contract1.getNumContractKvPairs()).willReturn(4);
        final var contract1Num = 3L;
        final var slot1 = UInt256.valueOf(1L);
        final var contract1Key1 = ContractKey.from(contract1Num, slot1);
        final var slot0 = UInt256.valueOf(0L);
        final var contract1Key2 = ContractKey.from(contract1Num, slot0);
        final var slot1555542 = UInt256.valueOf(155542L);
        final var contract1Key3 = ContractKey.from(contract1Num, slot1555542);
        final var slot999 = UInt256.valueOf(999L);
        final var contract1Key4 = ContractKey.from(contract1Num, slot999);
        final var contract1Value1 = mock(IterableContractValue.class);
        final var contract1Value2 = mock(IterableContractValue.class);
        final var contract1Value3 = mock(IterableContractValue.class);
        final var contract1Value4 = mock(IterableContractValue.class);
        given(contract1.getFirstContractStorageKey()).willReturn(contract1Key1);
        given(contract1Value1.getNextKeyScopedTo(contract1Num)).willReturn(contract1Key2);
        final var value1 = "value1".getBytes();
        given(contract1Value1.asUInt256()).willReturn(UInt256.fromBytes(Bytes.of(value1)));
        given(contract1Value2.getNextKeyScopedTo(contract1Num)).willReturn(contract1Key3);
        final var value2 = UInt256.valueOf(0);
        given(contract1Value2.asUInt256()).willReturn(value2);
        final var value3 = "value3".getBytes();
        given(contract1Value3.asUInt256()).willReturn(UInt256.fromBytes(Bytes.of(value3)));
        given(contract1Value3.getNextKeyScopedTo(contract1Num)).willReturn(contract1Key4);
        given(contract1Value4.asUInt256()).willReturn(UInt256.ZERO);
        given(contract1Value4.getNextKeyScopedTo(contract1Num)).willReturn(null);
        given(contractStorage.get(contract1Key1)).willReturn(contract1Value1);
        given(contractStorage.get(contract1Key2)).willReturn(contract1Value2);
        given(contractStorage.get(contract1Key3)).willReturn(contract1Value3);
        given(contractStorage.get(contract1Key4)).willReturn(contract1Value4);
        final var entityNum1 = EntityNum.fromLong(contract1Num);
        final var runtimeBytes = "runtime".getBytes();
        given(entityAccess.fetchCodeIfPresent(entityNum1.toGrpcAccountId()))
                .willReturn(Bytes.of(runtimeBytes));
        // mock contract with 1 slot with loop
        final var contract2 = mock(MerkleAccount.class);
        given(contract2.isSmartContract()).willReturn(true);
        given(contract2.getNumContractKvPairs()).willReturn(1);
        final var contrcat2Num = 4L;
        final var contract2Slot257 = UInt256.valueOf(257L);
        final var contract2Key1 = ContractKey.from(contrcat2Num, contract2Slot257);
        final var contract2Key2 = ContractKey.from(contract1Num, UInt256.valueOf(2L));
        final var contract2Value1 = mock(IterableContractValue.class);
        given(contract2.getFirstContractStorageKey()).willReturn(contract2Key1);
        given(contract2Value1.getNextKeyScopedTo(contrcat2Num)).willReturn(contract2Key2);
        final var value4 = UInt256.valueOf(1L);
        given(contract2Value1.asUInt256()).willReturn(value4);
        given(contractStorage.get(contract2Key1)).willReturn(contract2Value1);
        final var entityNum2 = EntityNum.fromLong(contrcat2Num);
        final var runtimeBytes2 = "runtime2".getBytes();
        given(entityAccess.fetchCodeIfPresent(entityNum2.toGrpcAccountId()))
                .willReturn(Bytes.of(runtimeBytes2));

        accounts.put(entityNum1, contract1);
        accounts.put(entityNum2, contract2);

        // when
        subject.publishMigrationRecords(now);

        // then
        assertTrue(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext, times(4)).addSidecarRecord(sidecarCaptor.capture());
        final var sidecarRecords = sidecarCaptor.getAllValues();
        assertEquals(
                SidecarUtils.createContractBytecodeSidecarFrom(
                                entityNum2.toGrpcContractID(), runtimeBytes2)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(0).build());
        final var contract2StateChange =
                ContractStateChange.newBuilder()
                        .setContractId(entityNum2.toGrpcContractID())
                        .addStorageChanges(
                                StorageChange.newBuilder()
                                        .setSlot(
                                                ByteStringUtils.wrapUnsafely(
                                                        contract2Slot257
                                                                .trimLeadingZeros()
                                                                .toArrayUnsafe()))
                                        .setValueRead(
                                                ByteStringUtils.wrapUnsafely(
                                                        value4.trimLeadingZeros().toArrayUnsafe()))
                                        .build())
                        .build();
        final var expectedStateChangesContract2 =
                ContractStateChanges.newBuilder()
                        .addContractStateChanges(contract2StateChange)
                        .build();
        assertEquals(
                TransactionSidecarRecord.newBuilder()
                        .setStateChanges(expectedStateChangesContract2)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(1).build());
        assertEquals(
                SidecarUtils.createContractBytecodeSidecarFrom(
                                entityNum1.toGrpcContractID(), runtimeBytes)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(2).build());
        final var contract1StateChanges =
                ContractStateChange.newBuilder()
                        .setContractId(entityNum1.toGrpcContractID())
                        .addStorageChanges(
                                StorageChange.newBuilder()
                                        .setSlot(
                                                ByteStringUtils.wrapUnsafely(
                                                        slot1.trimLeadingZeros().toArrayUnsafe()))
                                        .setValueRead(ByteStringUtils.wrapUnsafely(value1))
                                        .build())
                        .addStorageChanges(
                                // as per HIP-260 - a contract that only reads a zero value from
                                // slot zero will have an empty message.
                                StorageChange.newBuilder().build())
                        .addStorageChanges(
                                StorageChange.newBuilder()
                                        .setSlot(
                                                ByteStringUtils.wrapUnsafely(
                                                        slot1555542
                                                                .trimLeadingZeros()
                                                                .toArrayUnsafe()))
                                        .setValueRead(ByteStringUtils.wrapUnsafely(value3))
                                        .build())
                        .addStorageChanges(
                                // as per HIP-260 - zero value read will not set the valueRead field
                                // of a
                                // storage change
                                StorageChange.newBuilder()
                                        .setSlot(
                                                ByteStringUtils.wrapUnsafely(
                                                        slot999.trimLeadingZeros().toArrayUnsafe()))
                                        .build())
                        .build();
        final var expectedStateChangesContract1 =
                ContractStateChanges.newBuilder()
                        .addContractStateChanges(contract1StateChanges)
                        .build();
        assertEquals(
                TransactionSidecarRecord.newBuilder()
                        .setStateChanges(expectedStateChangesContract1)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(3).build());
    }

    @Test
    void traceabilityMigrationHandlesNumKvPairsFieldIndicatingMoreThanActualPairsSuccessfully() {
        final ArgumentCaptor<TransactionSidecarRecord.Builder> sidecarCaptor =
                forClass(TransactionSidecarRecord.Builder.class);
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.ConsensusCreateTopic);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);
        accounts.clear();
        // mock contract with 1 slot but numKvPairs = 2
        final var contract1 = mock(MerkleAccount.class);
        given(contract1.isSmartContract()).willReturn(true);
        given(contract1.getNumContractKvPairs()).willReturn(2);
        final var contract1Num = 3L;
        final var contract1Key1 = ContractKey.from(contract1Num, UInt256.valueOf(1L));
        final var contract1Value1 = mock(IterableContractValue.class);
        given(contract1.getFirstContractStorageKey()).willReturn(contract1Key1);
        given(contract1Value1.getNextKeyScopedTo(contract1Num)).willReturn(null);
        final var value = "value".getBytes();
        given(contract1Value1.asUInt256()).willReturn(UInt256.fromBytes(Bytes.of(value)));
        given(contractStorage.get(contract1Key1)).willReturn(contract1Value1);
        final var entityNum1 = EntityNum.fromLong(contract1Num);
        final var runtimeBytes = "runtime".getBytes();
        given(entityAccess.fetchCodeIfPresent(entityNum1.toGrpcAccountId()))
                .willReturn(Bytes.of(runtimeBytes));

        accounts.put(entityNum1, contract1);

        // when
        subject.publishMigrationRecords(now);

        // then
        assertTrue(subject.areTraceabilityRecordsStreamed());
        // then:
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.equalTo(
                                "After walking through all iterable storage of contract 0.0.3,"
                                    + " numContractKvPairs field indicates that there should have"
                                    + " been 1 more k/v pair(s) left")));
        verify(transactionContext, times(2)).addSidecarRecord(sidecarCaptor.capture());
        final var sidecarRecords = sidecarCaptor.getAllValues();
        assertEquals(
                SidecarUtils.createContractBytecodeSidecarFrom(
                                entityNum1.toGrpcContractID(), runtimeBytes)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(0).build());
        final var contract2StateChange =
                ContractStateChange.newBuilder()
                        .setContractId(entityNum1.toGrpcContractID())
                        .addStorageChanges(
                                StorageChange.newBuilder()
                                        .setSlot(
                                                ByteStringUtils.wrapUnsafely(
                                                        UInt256.valueOf(1L)
                                                                .trimLeadingZeros()
                                                                .toArrayUnsafe()))
                                        .setValueRead(ByteStringUtils.wrapUnsafely(value))
                                        .build())
                        .build();
        final var expectedStateChangesContract2 =
                ContractStateChanges.newBuilder()
                        .addContractStateChanges(contract2StateChange)
                        .build();
        assertEquals(
                TransactionSidecarRecord.newBuilder()
                        .setStateChanges(expectedStateChangesContract2)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(1).build());
    }

    @Test
    void traceabilityMigrationDoesNotAddAnySidecarsWhenAccountIsNotSmartContract() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.ConsensusCreateTopic);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);

        subject.publishMigrationRecords(now);

        assertTrue(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext, never()).addSidecarRecord(any());
    }

    @Test
    void traceabilityMigrationIsNotExecutedWhenMarkedAsDone() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);

        subject.markTraceabilityMigrationAsDone();
        subject.publishMigrationRecords(now);

        verify(transactionContext, never()).accessor();
        assertTrue(subject.areTraceabilityRecordsStreamed());
    }

    @Test
    void traceabilityMigrationDoesNotAddStateChangesWhenSmartContractDoesNotHaveAnyStorage() {
        final ArgumentCaptor<TransactionSidecarRecord.Builder> sidecarCaptor =
                forClass(TransactionSidecarRecord.Builder.class);
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.ConsensusCreateTopic);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);
        accounts.clear();
        final var contract = mock(MerkleAccount.class);
        given(contract.isSmartContract()).willReturn(true);
        given(contract.getFirstContractStorageKey()).willReturn(null);
        final var entityNum = EntityNum.fromLong(1L);
        final var runtimeBytes = "runtime".getBytes();
        given(entityAccess.fetchCodeIfPresent(entityNum.toGrpcAccountId()))
                .willReturn(Bytes.of(runtimeBytes));
        accounts.put(entityNum, contract);

        subject.publishMigrationRecords(now);

        assertTrue(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext).addSidecarRecord(sidecarCaptor.capture());
        final var sidecarRecords = sidecarCaptor.getValue();
        assertEquals(
                SidecarUtils.createContractBytecodeSidecarFrom(
                                entityNum.toGrpcContractID(), runtimeBytes)
                        .setMigration(true)
                        .build(),
                sidecarRecords.build());
    }

    @Test
    void traceabilityMigrationDoesNotCreateMigrationRecordsForContractWithoutBytecodeInState() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(false);
        given(txnAccessor.getFunction()).willReturn(HederaFunctionality.ConsensusCreateTopic);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);
        accounts.clear();
        final var contract = mock(MerkleAccount.class);
        given(contract.isSmartContract()).willReturn(true);
        final var contractNum = 1L;
        final var contractEntityNum = EntityNum.fromLong(contractNum);
        accounts.put(contractEntityNum, contract);
        given(entityAccess.fetchCodeIfPresent(contractEntityNum.toGrpcAccountId()))
                .willReturn(null);

        subject.publishMigrationRecords(now);

        assertTrue(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext, never())
                .addSidecarRecord(any(TransactionSidecarRecord.Builder.class));
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.equalTo(
                                "Contract 0.0."
                                        + contractNum
                                        + " has no bytecode in state - no migration"
                                        + " sidecar records will be published.")));
    }

    @Test
    void doesNotPerformOtherMigrationOnSubsequentCallsIfOnlyTraceabilityNeedsFinishing() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.areMigrationRecordsStreamed()).willReturn(false).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        MigrationRecordsManager.setExpiryJustEnabled(true);
        given(txnAccessor.getFunction())
                .willReturn(HederaFunctionality.ContractCreate)
                .willReturn(HederaFunctionality.CryptoTransfer);
        given(transactionContext.accessor()).willReturn(txnAccessor);
        given(dynamicProperties.isTraceabilityMigrationEnabled()).willReturn(true);
        given(merkleAccount.isSmartContract()).willReturn(true).willReturn(false);

        subject.publishMigrationRecords(now);
        assertFalse(subject.areTraceabilityRecordsStreamed());
        verify(transactionContext, never()).addSidecarRecord(any());
        verify(networkCtx).markMigrationRecordsStreamed();

        subject.publishMigrationRecords(now);
        assertTrue(subject.areTraceabilityRecordsStreamed());
        verify(treasuryCloner, times(1)).getClonesCreated();
        verify(recordsHistorian, times(1))
                .trackPrecedingChildRecord(
                        anyInt(), any(Builder.class), any(ExpirableTxnRecord.Builder.class));
    }

    @Test
    void ifContextIndicatesContractRenewRecordsNeedToBeStreamedThenDoesSo() {
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final ArgumentCaptor<ExpirableTxnRecord.Builder> recordCaptor =
                forClass(ExpirableTxnRecord.Builder.class);

        final var contractUpdateSynthBody1 =
                factory.synthContractAutoRenew(
                                contract1Id.asNum(), contract1Expiry, contract1Id.toGrpcAccountId())
                        .build();
        final var contractUpdateSynthBody2 =
                factory.synthContractAutoRenew(
                                contract2Id.asNum(), contract2Expiry, contract2Id.toGrpcAccountId())
                        .build();

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        given(merkleAccount.isSmartContract()).willReturn(true);
        given(merkleAccount.getExpiry()).willReturn(contract1Expiry).willReturn(contract2Expiry);
        MigrationRecordsManager.setExpiryJustEnabled(true);

        subject.publishMigrationRecords(now);

        MigrationRecordsManager.setExpiryJustEnabled(false);

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

    @Test
    void traceabilityRecordsFlagWorksAsExpected() {
        assertFalse(subject.areTraceabilityRecordsStreamed());

        subject.markTraceabilityMigrationAsDone();

        assertTrue(subject.areTraceabilityRecordsStreamed());
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
        treasuryClones.get(0).setKey(EntityNum.fromLong(200L));
        treasuryClones.get(1).setKey(EntityNum.fromLong(201L));
        given(treasuryCloner.getClonesCreated()).willReturn(treasuryClones);
    }
}
