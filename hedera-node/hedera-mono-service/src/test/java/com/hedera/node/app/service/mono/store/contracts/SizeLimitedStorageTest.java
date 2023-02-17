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

package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.FIRST_CONTRACT_STORAGE_KEY;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.NUM_CONTRACT_KV_PAIRS;
import static com.hedera.node.app.service.mono.store.contracts.SizeLimitedStorage.ZERO_VALUE;
import static com.hedera.node.app.service.mono.store.contracts.SizeLimitedStorage.incorporateKvImpact;
import static com.hedera.node.app.service.mono.store.contracts.SizeLimitedStorage.treeSetFactory;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.fees.charging.StorageFeeCharging;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.validation.ContractStorageLimits;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SizeLimitedStorageTest {
    @Mock
    private ContractStorageLimits usageLimits;

    @Mock
    private StorageFeeCharging storageFeeCharging;

    @Mock
    private SizeLimitedStorage.IterableStorageUpserter storageUpserter;

    @Mock
    private SizeLimitedStorage.IterableStorageRemover storageRemover;

    @Mock
    private MerkleMap<EntityNum, MerkleAccount> accounts;

    @Mock
    private VirtualMap<ContractKey, IterableContractValue> storage;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;

    private VirtualMapLike<ContractKey, IterableContractValue> storageLike;

    private final Map<Long, TreeSet<ContractKey>> updatedKeys = new TreeMap<>();
    private final Map<Long, TreeSet<ContractKey>> removedKeys = new TreeMap<>();
    private final Map<ContractKey, IterableContractValue> newMappings = new HashMap<>();

    private SizeLimitedStorage subject;

    @BeforeEach
    void setUp() {
        storageLike = VirtualMapLike.from(storage);
        subject = new SizeLimitedStorage(
                storageFeeCharging,
                usageLimits,
                storageUpserter,
                storageRemover,
                () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts)),
                () -> storageLike);
    }

    @Test
    void removesMappingsInOrder() {
        givenAccount(firstAccount, firstKvPairs, firstRootKey);
        givenAccount(nextAccount, nextKvPairs, nextRootKey);
        given(storageRemover.removeMapping(firstAKey, firstRootKey, VirtualMapLike.from(storage)))
                .willReturn(firstRootKey);
        given(storageRemover.removeMapping(firstBKey, firstRootKey, storageLike))
                .willReturn(firstRootKey);
        given(storageRemover.removeMapping(nextAKey, nextRootKey, storageLike)).willReturn(null);

        InOrder inOrder = Mockito.inOrder(storage, accounts, accountsLedger, storageRemover);

        given(storage.containsKey(firstAKey)).willReturn(true);
        given(storage.containsKey(firstBKey)).willReturn(true);
        given(storage.containsKey(nextAKey)).willReturn(true);

        subject.putStorage(firstAccount, aLiteralKey, UInt256.ZERO);
        subject.putStorage(firstAccount, bLiteralKey, UInt256.ZERO);
        subject.putStorage(nextAccount, aLiteralKey, UInt256.ZERO);

        subject.validateAndCommit(accountsLedger);
        subject.recordNewKvUsageTo(accountsLedger);

        inOrder.verify(storageRemover).removeMapping(firstAKey, firstRootKey, storageLike);
        inOrder.verify(storageRemover).removeMapping(firstBKey, firstRootKey, storageLike);
        inOrder.verify(storageRemover).removeMapping(nextAKey, nextRootKey, storageLike);
        // and:
        inOrder.verify(accountsLedger).set(firstAccount, NUM_CONTRACT_KV_PAIRS, firstKvPairs - 2);
        inOrder.verify(accountsLedger).set(firstAccount, FIRST_CONTRACT_STORAGE_KEY, firstRootKey.getKey());
        inOrder.verify(accountsLedger).set(nextAccount, NUM_CONTRACT_KV_PAIRS, nextKvPairs - 1);
        inOrder.verify(accountsLedger).set(nextAccount, FIRST_CONTRACT_STORAGE_KEY, null);
        // and:
        verify(usageLimits).refreshStorageSlots();
    }

    @Test
    void removesAllMappingsEvenIfExceptionThrown() {
        givenAccount(firstAccount, firstKvPairs, firstRootKey);
        givenAccount(nextAccount, nextKvPairs, nextRootKey);
        given(storageRemover.removeMapping(firstAKey, firstRootKey, storageLike))
                .willThrow(NullPointerException.class);
        given(storageRemover.removeMapping(eq(firstBKey), any(), eq(storageLike)))
                .willReturn(firstRootKey);
        given(storageRemover.removeMapping(eq(nextAKey), any(), eq(storageLike)))
                .willReturn(null);

        InOrder inOrder = Mockito.inOrder(storage, accounts, accountsLedger, storageRemover);

        given(storage.containsKey(firstAKey)).willReturn(true);
        given(storage.containsKey(firstBKey)).willReturn(true);
        given(storage.containsKey(nextAKey)).willReturn(true);

        subject.putStorage(firstAccount, aLiteralKey, UInt256.ZERO);
        subject.putStorage(firstAccount, bLiteralKey, UInt256.ZERO);
        subject.putStorage(nextAccount, aLiteralKey, UInt256.ZERO);

        subject.validateAndCommit(accountsLedger);
        subject.recordNewKvUsageTo(accountsLedger);

        inOrder.verify(storageRemover, times(3)).removeMapping(any(), any(), eq(storageLike));
        // and:
        inOrder.verify(accountsLedger).set(firstAccount, NUM_CONTRACT_KV_PAIRS, firstKvPairs - 2);
        inOrder.verify(accountsLedger).set(firstAccount, FIRST_CONTRACT_STORAGE_KEY, firstRootKey.getKey());
        inOrder.verify(accountsLedger).set(nextAccount, NUM_CONTRACT_KV_PAIRS, nextKvPairs - 1);
        inOrder.verify(accountsLedger).set(nextAccount, FIRST_CONTRACT_STORAGE_KEY, null);
    }

    @Test
    void commitsMappingsInOrderWithNewRootValue() {
        InOrder inOrder = Mockito.inOrder(storage, accountsLedger, storageUpserter);

        given(storage.size()).willReturn(0L).willReturn(1L);
        givenAccount(firstAccount, firstKvPairs, firstRootKey);
        givenAccount(nextAccount, nextKvPairs, nextRootKey);
        given(storageUpserter.upsertMapping(firstAKey, aValue, firstRootKey, null, storageLike))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(firstBKey, bValue, firstAKey, aValue, storageLike))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(firstDKey, dValue, firstAKey, null, storageLike))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(nextAKey, aValue, nextRootKey, null, storageLike))
                .willReturn(nextAKey);

        subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);
        subject.putStorage(firstAccount, bLiteralKey, bLiteralValue);
        subject.putStorage(nextAccount, aLiteralKey, aLiteralValue);
        subject.putStorage(firstAccount, dLiteralKey, dLiteralValue);

        subject.validateAndCommit(accountsLedger);

        inOrder.verify(storageUpserter).upsertMapping(firstAKey, aValue, firstRootKey, null, storageLike);
        inOrder.verify(storageUpserter).upsertMapping(firstBKey, bValue, firstAKey, aValue, storageLike);
        inOrder.verify(storageUpserter).upsertMapping(firstDKey, dValue, firstAKey, null, storageLike);
        inOrder.verify(storageUpserter).upsertMapping(nextAKey, aValue, nextRootKey, null, storageLike);
    }

    @Test
    void commitsAllMappingsEvenIfExceptionThrown() {
        givenAccount(firstAccount, firstKvPairs, firstRootKey);
        givenAccount(nextAccount, nextKvPairs, nextRootKey);
        given(storageUpserter.upsertMapping(firstAKey, aValue, firstRootKey, null, storageLike))
                .willThrow(NullPointerException.class);
        given(storageUpserter.upsertMapping(eq(firstBKey), eq(bValue), any(), any(), eq(storageLike)))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(eq(firstDKey), eq(dValue), any(), any(), eq(storageLike)))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(eq(nextAKey), eq(aValue), any(), any(), eq(storageLike)))
                .willReturn(nextAKey);

        subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);
        subject.putStorage(firstAccount, bLiteralKey, bLiteralValue);
        subject.putStorage(nextAccount, aLiteralKey, aLiteralValue);
        subject.putStorage(firstAccount, dLiteralKey, dLiteralValue);

        subject.validateAndCommit(accountsLedger);

        verify(storageUpserter, times(4)).upsertMapping(any(), any(), any(), any(), any());
    }

    @Test
    void okToCommitNoChanges() {
        assertDoesNotThrow(() -> subject.validateAndCommit(accountsLedger));
    }

    @Test
    void commitsMappingsInOrderWithUpdatedRootValue() {
        InOrder inOrder = Mockito.inOrder(storage, accountsLedger, storageUpserter);

        givenAccount(firstAccount, firstKvPairs, firstRootKey);
        givenAccount(nextAccount, nextKvPairs, nextRootKey);
        given(storageUpserter.upsertMapping(
                        eq(firstAKey), eq(aValue), eq(firstRootKey), eq(null), any(VirtualMapLike.class)))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(
                        eq(firstBKey), eq(bValue), eq(firstAKey), eq(null), any(VirtualMapLike.class)))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(
                        eq(firstDKey), eq(dValue), eq(firstAKey), eq(null), any(VirtualMapLike.class)))
                .willReturn(firstAKey);
        given(storageUpserter.upsertMapping(
                        eq(nextAKey), eq(aValue), eq(nextRootKey), eq(null), any(VirtualMapLike.class)))
                .willReturn(nextAKey);

        subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);
        subject.putStorage(firstAccount, bLiteralKey, bLiteralValue);
        subject.putStorage(nextAccount, aLiteralKey, aLiteralValue);
        subject.putStorage(firstAccount, dLiteralKey, dLiteralValue);

        subject.validateAndCommit(accountsLedger);

        inOrder.verify(storageUpserter)
                .upsertMapping(eq(firstAKey), eq(aValue), eq(firstRootKey), eq(null), any(VirtualMapLike.class));
        inOrder.verify(storageUpserter)
                .upsertMapping(eq(firstBKey), eq(bValue), eq(firstAKey), eq(null), any(VirtualMapLike.class));
        inOrder.verify(storageUpserter)
                .upsertMapping(eq(firstDKey), eq(dValue), eq(firstAKey), eq(null), any(VirtualMapLike.class));
        inOrder.verify(storageUpserter)
                .upsertMapping(eq(nextAKey), eq(aValue), eq(nextRootKey), eq(null), any(VirtualMapLike.class));
    }

    @Test
    void commitsMappingsForMissingAccount() {
        InOrder inOrder = Mockito.inOrder(storage, accountsLedger, storageUpserter);

        given(storageUpserter.upsertMapping(eq(firstAKey), eq(aValue), eq(null), eq(null), any(VirtualMapLike.class)))
                .willReturn(firstAKey);

        subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);

        subject.validateAndCommit(accountsLedger);
        subject.recordNewKvUsageTo(accountsLedger);

        inOrder.verify(storageUpserter)
                .upsertMapping(eq(firstAKey), eq(aValue), eq(null), eq(null), any(VirtualMapLike.class));
    }

    @Test
    void validatesSingleContractStorage() {
        givenAccount(firstAccount, firstKvPairs);
        willThrow(new InvalidTransactionException(MAX_CONTRACT_STORAGE_EXCEEDED))
                .given(usageLimits)
                .assertUsableContractSlots(firstKvPairs + 2);

        subject.putStorage(firstAccount, aLiteralKey, bLiteralValue);
        subject.putStorage(firstAccount, bLiteralKey, aLiteralValue);

        assertFailsWith(() -> subject.validateAndCommit(accountsLedger), MAX_CONTRACT_STORAGE_EXCEEDED);
    }

    @Test
    void validatesMaxContractStorage() {
        final var maxKvPairs = (long) firstKvPairs + nextKvPairs;
        givenAccount(firstAccount, firstKvPairs);
        givenAccount(nextAccount, nextKvPairs);
        given(storage.size()).willReturn(maxKvPairs);
        given(storage.containsKey(firstAKey)).willReturn(false);
        given(storage.containsKey(firstBKey)).willReturn(false);
        given(storage.containsKey(nextAKey)).willReturn(true);
        willThrow(new InvalidTransactionException(MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED))
                .given(usageLimits)
                .assertUsableTotalSlots(maxKvPairs + 1);

        subject.beginSession();
        subject.putStorage(firstAccount, aLiteralKey, bLiteralValue);
        subject.putStorage(firstAccount, bLiteralKey, aLiteralValue);
        subject.putStorage(nextAccount, aLiteralKey, UInt256.ZERO);

        assertFailsWith(() -> subject.validateAndCommit(accountsLedger), MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED);
    }

    @Test
    void updatesAreBufferedAndReturned() {
        givenAccount(firstAccount, firstKvPairs);

        subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);

        assertEquals(firstKvPairs + 1, subject.usageSoFar(firstAccount));
        assertEquals(aLiteralValue, subject.getStorage(firstAccount, aLiteralKey));
    }

    @Test
    void unbufferedValuesAreReturnedDirectly() {
        given(storage.get(firstAKey)).willReturn(aValue);

        assertEquals(aLiteralValue, subject.getStorage(firstAccount, aLiteralKey));
        assertEquals(UInt256.ZERO, subject.getStorage(firstAccount, bLiteralKey));
    }

    @Test
    void resetsPendingChangesAsExpected() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        given(storage.containsKey(nextAKey)).willReturn(true);

        subject.getNewFirstKeys().put(firstAKey.getContractId(), firstAKey);
        subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);
        subject.putStorage(nextAccount, aLiteralKey, UInt256.ZERO);

        subject.beginSession();

        assertTrue(subject.getUsageChanges().isEmpty());
        assertTrue(subject.getNewMappings().isEmpty());
        assertTrue(subject.getUpdatedKeys().isEmpty());
        assertTrue(subject.getRemovedKeys().isEmpty());
        assertTrue(subject.getNewFirstKeys().isEmpty());
    }

    @Test
    void initialKvForNotYetCreatedAccountIsZero() {
        subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);

        assertEquals(1, subject.usageSoFar(firstAccount));
    }

    @Test
    void removedKeysAreRespected() {
        givenAccount(firstAccount, firstKvPairs);
        givenContainedStorage(firstAKey, aValue);

        assertEquals(aLiteralValue, subject.getStorage(firstAccount, aLiteralKey));

        subject.putStorage(firstAccount, aLiteralKey, bLiteralValue);
        assertEquals(bLiteralValue, subject.getStorage(firstAccount, aLiteralKey));
        assertEquals(firstKvPairs, subject.usageSoFar(firstAccount));

        subject.putStorage(firstAccount, aLiteralKey, UInt256.ZERO);
        assertEquals(UInt256.ZERO, subject.getStorage(firstAccount, aLiteralKey));
        assertEquals(firstKvPairs - 1, subject.usageSoFar(firstAccount));
    }

    @Test
    void removingOnlyCurrentMappingInListCausesSubsequentInsertionToUseNullRoot() {
        givenAccount(firstAccount, 1, firstAKey);
        given(storage.containsKey(firstAKey)).willReturn(true);

        subject.putStorage(firstAccount, aLiteralKey, UInt256.ZERO);
        subject.putStorage(firstAccount, bLiteralKey, bLiteralValue);

        given(storageUpserter.upsertMapping(eq(firstBKey), eq(bValue), eq(null), eq(null), any(VirtualMapLike.class)))
                .willReturn(firstBKey);

        subject.validateAndCommit(accountsLedger);

        verify(storageUpserter).upsertMapping(eq(firstBKey), eq(bValue), eq(null), eq(null), any(VirtualMapLike.class));
    }

    @Test
    void incorporatesNewAddition() {
        final var kvImpact = incorporateKvImpact(
                firstAKey, aValue, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(1, kvImpact);
        assertEquals(aValue, newMappings.get(firstAKey));
        assertTrue(updatedKeys.containsKey(firstAKey.getContractId()));
        assertEquals(firstAKey, updatedKeys.get(firstAKey.getContractId()).first());
    }

    @Test
    void incorporatesNewUpdate() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        final var kvImpact = incorporateKvImpact(
                firstAKey, aValue, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(0, kvImpact);
        assertEquals(aValue, newMappings.get(firstAKey));
        assertTrue(updatedKeys.containsKey(firstAKey.getContractId()));
        assertEquals(firstAKey, updatedKeys.get(firstAKey.getContractId()).first());
    }

    @Test
    void incorporatesRecreatingUpdate() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        removedKeys.computeIfAbsent(firstAKey.getContractId(), treeSetFactory).add(firstAKey);
        final var kvImpact = incorporateKvImpact(
                firstAKey, aValue, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(1, kvImpact);
        assertEquals(aValue, newMappings.get(firstAKey));
        assertTrue(updatedKeys.containsKey(firstAKey.getContractId()));
        assertEquals(firstAKey, updatedKeys.get(firstAKey.getContractId()).first());
        assertFalse(removedKeys.get(firstAKey.getContractId()).contains(firstAKey));
    }

    @Test
    void incorporatesNewUpdateWithOtherContractKeyBeingRemoved() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        removedKeys.computeIfAbsent(firstAKey.getContractId(), treeSetFactory).add(firstBKey);
        final var kvImpact = incorporateKvImpact(
                firstAKey, aValue, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(0, kvImpact);
        assertEquals(aValue, newMappings.get(firstAKey));
        assertTrue(updatedKeys.containsKey(firstAKey.getContractId()));
        assertEquals(firstAKey, updatedKeys.get(firstAKey.getContractId()).first());
        assertFalse(removedKeys.get(firstAKey.getContractId()).contains(firstAKey));
    }

    @Test
    void incorporatesOverwriteOfPendingUpdate() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        newMappings.put(firstAKey, aValue);
        final var kvImpact = incorporateKvImpact(
                firstAKey, bValue, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(0, kvImpact);
        assertEquals(bValue, newMappings.get(firstAKey));
    }

    @Test
    void ignoresNoopZero() {
        final var kvImpact = incorporateKvImpact(
                firstAKey, ZERO_VALUE, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(0, kvImpact);
    }

    @Test
    void incorporatesErasingExtant() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        final var kvImpact = incorporateKvImpact(
                firstAKey, ZERO_VALUE, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(-1, kvImpact);
        assertTrue(removedKeys.containsKey(firstAKey.getContractId()));
        assertEquals(firstAKey, removedKeys.get(firstAKey.getContractId()).first());
    }

    @Test
    void incorporatesErasingPendingAndAlreadyPresent() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        updatedKeys.computeIfAbsent(firstAKey.getContractId(), treeSetFactory).add(firstAKey);
        newMappings.put(firstAKey, aValue);
        final var kvImpact = incorporateKvImpact(
                firstAKey, ZERO_VALUE, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(-1, kvImpact);
        assertTrue(removedKeys.containsKey(firstAKey.getContractId()));
        assertEquals(firstAKey, removedKeys.get(firstAKey.getContractId()).first());
        assertTrue(updatedKeys.get(firstAKey.getContractId()).isEmpty());
        assertFalse(newMappings.containsKey(firstAKey));
    }

    @Test
    void incorporatesErasingPendingAndNotAlreadyPresent() {
        updatedKeys.computeIfAbsent(firstAKey.getContractId(), treeSetFactory).add(firstAKey);
        newMappings.put(firstAKey, aValue);
        final var kvImpact = incorporateKvImpact(
                firstAKey, ZERO_VALUE, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(-1, kvImpact);
        assertFalse(removedKeys.containsKey(firstAKey.getContractId()));
        assertTrue(updatedKeys.get(firstAKey.getContractId()).isEmpty());
        assertFalse(newMappings.containsKey(firstAKey));
    }

    @Test
    void aPendingChangeMustBeReflectedInAnAdditionSet() {
        newMappings.put(firstAKey, aValue);
        assertThrows(
                IllegalStateException.class,
                () -> incorporateKvImpact(
                        firstAKey, ZERO_VALUE, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage)));
    }

    @Test
    void incorporatesErasingNotAlreadyPending() {
        given(storage.containsKey(firstAKey)).willReturn(true);
        final var kvImpact = incorporateKvImpact(
                firstAKey, ZERO_VALUE, updatedKeys, removedKeys, newMappings, VirtualMapLike.from(storage));

        assertEquals(-1, kvImpact);
        assertTrue(removedKeys.containsKey(firstAKey.getContractId()));
        assertEquals(firstAKey, removedKeys.get(firstAKey.getContractId()).first());
    }

    /* --- Internal helpers --- */
    private void givenAccount(final AccountID id, final int initialKvPairs, final ContractKey firstKey) {
        givenAccountInternal(id, initialKvPairs, firstKey, true);
    }

    private void givenAccount(final AccountID id, final int initialKvPairs) {
        givenAccountInternal(id, initialKvPairs, null, false);
    }

    private void givenAccountInternal(
            final AccountID id, final int initialKvPairs, final ContractKey firstKey, final boolean mockFirstKey) {
        final var key = EntityNum.fromAccountId(id);
        final var account = mock(MerkleAccount.class);
        given(account.getNumContractKvPairs()).willReturn(initialKvPairs);
        if (mockFirstKey) {
            given(account.getFirstContractStorageKey()).willReturn(firstKey);
        }
        given(accounts.get(key)).willReturn(account);
    }

    private void givenContainedStorage(final ContractKey key, final IterableContractValue value) {
        given(storage.get(key)).willReturn(value);
        given(storage.containsKey(key)).willReturn(true);
    }

    private static final AccountID firstAccount = IdUtils.asAccount("0.0.1234");
    private static final AccountID nextAccount = IdUtils.asAccount("0.0.2345");
    private static final UInt256 aLiteralKey = UInt256.fromHexString("0xaabbcc");
    private static final UInt256 bLiteralKey = UInt256.fromHexString("0xbbccdd");
    private static final UInt256 cLiteralKey = UInt256.fromHexString("0xffddee");
    private static final UInt256 dLiteralKey = UInt256.fromHexString("0xdddddd");
    private static final UInt256 aLiteralValue = UInt256.fromHexString("0x1234aa");
    private static final UInt256 bLiteralValue = UInt256.fromHexString("0x1234bb");
    private static final UInt256 dLiteralValue = UInt256.fromHexString("0xadadad");
    private static final ContractKey firstAKey = ContractKey.from(firstAccount, aLiteralKey);
    private static final ContractKey firstBKey = ContractKey.from(firstAccount, bLiteralKey);
    private static final ContractKey firstDKey = ContractKey.from(firstAccount, dLiteralKey);
    private static final ContractKey nextAKey = ContractKey.from(nextAccount, aLiteralKey);
    private static final ContractKey firstRootKey = ContractKey.from(firstAccount, cLiteralKey);
    private static final ContractKey nextRootKey = ContractKey.from(nextAccount, cLiteralKey);
    private static final IterableContractValue aValue = IterableContractValue.from(aLiteralValue);
    private static final IterableContractValue bValue = IterableContractValue.from(bLiteralValue);
    private static final IterableContractValue dValue = IterableContractValue.from(dLiteralValue);
    private static final int firstKvPairs = 5;
    private static final int nextKvPairs = 6;
}
