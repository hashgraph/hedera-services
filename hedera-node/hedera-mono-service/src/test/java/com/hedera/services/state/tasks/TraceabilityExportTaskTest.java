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
package com.hedera.services.state.tasks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SidecarUtils;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TraceabilityExportTaskTest {
    private static final long ENTITY_NUM = 1234L;
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567, 890);
    private static final MerkleAccount AN_ACCOUNT = MerkleAccountFactory.newAccount().get();

    @Mock private TraceabilityRecordsHelper recordsHelper;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private EntityAccess entityAccess;
    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private VirtualMap<ContractKey, IterableContractValue> contractStorage;

    private TraceabilityExportTask subject;

    @BeforeEach
    void setUp() {
        subject =
                new TraceabilityExportTask(
                        entityAccess,
                        expiryThrottle,
                        dynamicProperties,
                        recordsHelper,
                        () -> accounts,
                        () -> contractStorage);
    }

    @Test
    void notActiveIfDisabled() {
        assertFalse(subject.isActive(ENTITY_NUM, networkCtx));
    }

    @Test
    void notActiveIfAllPreExistingEntitiesScanned() {
        given(dynamicProperties.shouldDoTraceabilityExport()).willReturn(true);
        given(networkCtx.areAllPreUpgradeEntitiesScanned()).willReturn(true);
        assertFalse(subject.isActive(ENTITY_NUM, networkCtx));
    }

    @Test
    void notActiveIfEntityWasNotPreExisting() {
        given(dynamicProperties.shouldDoTraceabilityExport()).willReturn(true);
        given(networkCtx.seqNoPostUpgrade()).willReturn(ENTITY_NUM);
        assertFalse(subject.isActive(ENTITY_NUM, networkCtx));
    }

    @Test
    void needsDifferentContextIfCannotExportRecords() {
        assertEquals(SystemTaskResult.NEEDS_DIFFERENT_CONTEXT, subject.process(ENTITY_NUM, NOW));

        verifyNoInteractions(expiryThrottle);
    }

    @Test
    void nothingToDoIfNotAnAccount() {
        given(recordsHelper.canExportNow()).willReturn(true);

        assertEquals(SystemTaskResult.NOTHING_TO_DO, subject.process(ENTITY_NUM, NOW));

        verify(expiryThrottle).allowOne(MapAccessType.ACCOUNTS_GET);
    }

    @Test
    void nothingToDoIfNotAContract() {
        given(recordsHelper.canExportNow()).willReturn(true);

        given(accounts.get(EntityNum.fromLong(ENTITY_NUM))).willReturn(AN_ACCOUNT);
        assertEquals(SystemTaskResult.NOTHING_TO_DO, subject.process(ENTITY_NUM, NOW));

        verify(expiryThrottle).allowOne(MapAccessType.ACCOUNTS_GET);
    }

    @Test
    void createsWellBehavedContractSideCars() {
        given(recordsHelper.canExportNow()).willReturn(true);
        final ArgumentCaptor<List<TransactionSidecarRecord.Builder>> captor = forClass(List.class);

        // Setup mock contract with 4 slots
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
        given(accounts.get(entityNum1)).willReturn(contract1);

        // when:
        final var result = subject.process(entityNum1.longValue(), NOW);
        assertEquals(SystemTaskResult.DONE, result);

        // then:
        verify(recordsHelper)
                .exportSidecarsViaSynthUpdate(eq(entityNum1.longValue()), captor.capture());
        final var sidecarRecords = captor.getValue();
        assertEquals(
                SidecarUtils.createContractBytecodeSidecarFrom(
                                entityNum1.toGrpcContractID(), runtimeBytes)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(0).build());
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
                sidecarRecords.get(1).build());
    }

    @Test
    void createsPoisonPillContractSideCars() {
        given(recordsHelper.canExportNow()).willReturn(true);
        final ArgumentCaptor<List<TransactionSidecarRecord.Builder>> captor = forClass(List.class);

        // mock contract with 1 slot with loop
        final var contract2 = mock(MerkleAccount.class);
        given(contract2.isSmartContract()).willReturn(true);
        given(contract2.getNumContractKvPairs()).willReturn(1);
        final var contract1Num = 3L;
        final var contract2Num = 4L;
        final var contract2Slot257 = UInt256.valueOf(257L);
        final var contract2Key1 = ContractKey.from(contract2Num, contract2Slot257);
        final var contract2Key2 = ContractKey.from(contract1Num, UInt256.valueOf(2L));
        final var contract2Value1 = mock(IterableContractValue.class);
        given(contract2.getFirstContractStorageKey()).willReturn(contract2Key1);
        given(contract2Value1.getNextKeyScopedTo(contract2Num)).willReturn(contract2Key2);
        final var value4 = UInt256.valueOf(1L);
        given(contract2Value1.asUInt256()).willReturn(value4);
        given(contractStorage.get(contract2Key1)).willReturn(contract2Value1);
        final var entityNum2 = EntityNum.fromLong(contract2Num);
        final var runtimeBytes2 = "runtime2".getBytes();
        given(entityAccess.fetchCodeIfPresent(entityNum2.toGrpcAccountId()))
                .willReturn(Bytes.of(runtimeBytes2));
        given(accounts.get(entityNum2)).willReturn(contract2);

        // when:
        final var result = subject.process(entityNum2.longValue(), NOW);
        assertEquals(SystemTaskResult.DONE, result);

        // then:
        verify(recordsHelper)
                .exportSidecarsViaSynthUpdate(eq(entityNum2.longValue()), captor.capture());
        final var sidecarRecords = captor.getValue();
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
    }

    @Test
    void createsMisSizedContractSideCars() {
        given(recordsHelper.canExportNow()).willReturn(true);
        final ArgumentCaptor<List<TransactionSidecarRecord.Builder>> captor = forClass(List.class);

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
        given(accounts.get(entityNum1)).willReturn(contract1);

        // when:
        final var result = subject.process(entityNum1.longValue(), NOW);
        assertEquals(SystemTaskResult.DONE, result);

        // then:
        verify(recordsHelper)
                .exportSidecarsViaSynthUpdate(eq(entityNum1.longValue()), captor.capture());
        final var sidecarRecords = captor.getValue();
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
    void createsStorageLessContractSideCar() {
        given(recordsHelper.canExportNow()).willReturn(true);
        final ArgumentCaptor<List<TransactionSidecarRecord.Builder>> captor = forClass(List.class);

        // Mock contract no storage
        final var contract = mock(MerkleAccount.class);
        given(contract.isSmartContract()).willReturn(true);
        given(contract.getFirstContractStorageKey()).willReturn(null);
        final var entityNum = EntityNum.fromLong(1L);
        final var runtimeBytes = "runtime".getBytes();
        given(entityAccess.fetchCodeIfPresent(entityNum.toGrpcAccountId()))
                .willReturn(Bytes.of(runtimeBytes));
        given(accounts.get(entityNum)).willReturn(contract);

        // when:
        final var result = subject.process(entityNum.longValue(), NOW);
        assertEquals(SystemTaskResult.DONE, result);

        // then:
        verify(recordsHelper)
                .exportSidecarsViaSynthUpdate(eq(entityNum.longValue()), captor.capture());
        final var sidecarRecords = captor.getValue();
        assertEquals(
                SidecarUtils.createContractBytecodeSidecarFrom(
                                entityNum.toGrpcContractID(), runtimeBytes)
                        .setMigration(true)
                        .build(),
                sidecarRecords.get(0).build());
    }

    @Test
    void skipsBytecodeLessContractSideCars() {
        given(recordsHelper.canExportNow()).willReturn(true);
        // Mock contract no storage
        final var contract = mock(MerkleAccount.class);
        given(contract.isSmartContract()).willReturn(true);
        final var contractNum = 1L;
        final var contractEntityNum = EntityNum.fromLong(contractNum);
        given(accounts.get(contractEntityNum)).willReturn(contract);

        // when:
        final var result = subject.process(contractEntityNum.longValue(), NOW);
        assertEquals(SystemTaskResult.DONE, result);

        // then:
        verify(recordsHelper, never()).exportSidecarsViaSynthUpdate(anyLong(), anyList());
    }
}
