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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkRepairsTest {
    @Mock private MerkleAccount contract;
    @Mock private MerkleMap<EntityNum, MerkleAccount> contracts;
    @Mock private VirtualMap<ContractKey, IterableContractValue> storage;

    private LinkRepairs subject;

    @BeforeEach
    void setUp() {
        subject = new LinkRepairs(contracts, storage);
    }

    @Test
    void detectsUnsetNonRootPrevPointer() throws InterruptedException {
        given(contracts.get(contractNum)).willReturn(contract);
        given(contracts.getForModify(contractNum)).willReturn(contract);
        given(contract.getFirstUint256Key()).willReturn(rootKey.getKey());
        given(storage.get(targetKey)).willReturn(targetValue);
        given(storage.get(nextKey)).willReturn(nextValue);

        nextValue.setPrevKey(targetKey.getKey());

        subject.accept(Pair.of(targetKey, targetValue));
        subject.accept(Pair.of(nextKey, nextValue));
        subject.markScanComplete();
        assertTrue(subject.hasBrokenLinks());
        subject.accept(Pair.of(targetKey, targetValue));
        subject.accept(Pair.of(nextKey, nextValue));
        subject.fixAnyBrokenLinks();

        verify(contract).setFirstUint256StorageKey(targetKey.getKey());
        verify(contract).setNumContractKvPairs(2);
        final var expectedTargetValue = targetValue.copy();
        expectedTargetValue.setNextKey(nextKey.getKey());
        verify(storage).put(targetKey, expectedTargetValue);
        verify(storage).put(nextKey, nextValue);
    }

    @Test
    void detectsMissingNextPointer() throws InterruptedException {
        given(contracts.get(contractNum)).willReturn(contract);
        given(contracts.getForModify(contractNum)).willReturn(contract);
        given(contract.getFirstUint256Key()).willReturn(targetKey.getKey());
        given(storage.get(targetKey)).willReturn(targetValue);

        targetValue.setNextKey(nextKey.getKey());

        subject.accept(Pair.of(targetKey, targetValue));
        subject.markScanComplete();
        assertTrue(subject.hasBrokenLinks());
        subject.accept(Pair.of(targetKey, targetValue));
        subject.fixAnyBrokenLinks();

        verify(contract).setFirstUint256StorageKey(targetKey.getKey());
        final var expectedTargetValue = targetValue.copy();
        expectedTargetValue.markAsLastMapping();
        verify(storage).put(targetKey, expectedTargetValue);
    }

    @Test
    void detectsNothingSpuriousForValidLinks() throws InterruptedException {
        given(contracts.get(contractNum)).willReturn(contract);
        given(contract.getFirstUint256Key()).willReturn(rootKey.getKey());
        rootValue.setNextKey(targetKey.getKey());
        given(storage.containsKey(targetKey)).willReturn(true);
        targetValue.setPrevKey(rootKey.getKey());
        targetValue.setNextKey(nextKey.getKey());
        given(storage.containsKey(nextKey)).willReturn(true);
        nextValue.setPrevKey(targetKey.getKey());

        subject.accept(Pair.of(rootKey, rootValue));
        subject.accept(Pair.of(targetKey, targetValue));
        subject.accept(Pair.of(nextKey, nextValue));
        subject.markScanComplete();
        assertFalse(subject.hasBrokenLinks());
        subject.accept(Pair.of(rootKey, rootValue));
        subject.accept(Pair.of(targetKey, targetValue));
        subject.accept(Pair.of(nextKey, nextValue));
        subject.fixAnyBrokenLinks();

        verify(storage, never()).put(any(), any());
        verify(contracts, never()).getForModify(any());
    }

    @Test
    void canSkipMissingContracts() {
        assertDoesNotThrow(() -> subject.accept(Pair.of(rootKey, rootValue)));
    }

    private static final long contractId = 123L;
    private static final EntityNum contractNum = EntityNum.fromLong(contractId);
    private static final UInt256 rootEvmKey = UInt256.fromHexString("0xbbccdd");
    private static final UInt256 nextEvmKey = UInt256.fromHexString("0xffeedd");
    private static final UInt256 targetEvmKey = UInt256.fromHexString("0xaabbcc");
    private static final UInt256 rootEvmValue =
            UInt256.fromHexString(
                    "0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
    private static final UInt256 nextEvmValue =
            UInt256.fromHexString(
                    "0x210aeca1542b62a2a60345a122326fc24ba6bc15424002f6362f13160ef3e563");
    private static final UInt256 targetEvmValue =
            UInt256.fromHexString(
                    "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060");
    private static final ContractKey rootKey = ContractKey.from(contractId, rootEvmKey);
    private static final ContractKey targetKey = ContractKey.from(contractId, targetEvmKey);
    private static final ContractKey nextKey = ContractKey.from(contractId, nextEvmKey);
    private final IterableContractValue rootValue =
            new IterableContractValue(rootEvmValue.toArray());
    private final IterableContractValue nextValue =
            new IterableContractValue(nextEvmValue.toArray());
    private final IterableContractValue targetValue =
            new IterableContractValue(targetEvmValue.toArray());
}
