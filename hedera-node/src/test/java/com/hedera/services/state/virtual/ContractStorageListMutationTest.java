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
package com.hedera.services.state.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractStorageListMutationTest {
    private static final long contractId = 123L;
    @Mock private VirtualMap<ContractKey, IterableContractValue> storage;

    private ContractStorageListMutation subject;

    @BeforeEach
    void setUp() {
        subject = new ContractStorageListMutation(contractId, storage);
    }

    @Test
    void delegatesGet() {
        given(storage.get(rootKey)).willReturn(rootValue);

        assertSame(rootValue, subject.get(rootKey));
    }

    @Test
    void delegatesGet4M() {
        given(storage.getForModify(rootKey)).willReturn(rootValue);

        assertSame(rootValue, subject.getForModify(rootKey));
    }

    @Test
    void delegatesRemoval() {
        subject.remove(rootKey);
        verify(storage).remove(rootKey);
    }

    @Test
    void delegatesPut() {
        subject.put(rootKey, rootValue);
        verify(storage).put(rootKey, rootValue);
    }

    @Test
    void knowsHowToMakeListNodeHead() {
        targetValue.setPrevKey(rootKey.getKey());

        subject.markAsHead(targetValue);

        assertNull(targetValue.getPrevKeyScopedTo(contractId));
    }

    @Test
    void knowsHowToMakeListNodeTail() {
        targetValue.setNextKey(nextKey.getKey());

        subject.markAsTail(targetValue);

        assertNull(targetValue.getNextKeyScopedTo(contractId));
    }

    @Test
    void updatesPrevAsExpected() {
        nextValue.setPrevKey(targetKey.getKey());

        subject.updatePrev(nextValue, rootKey);

        assertEquals(rootKey, nextValue.getPrevKeyScopedTo(contractId));
    }

    @Test
    void updatesNextAsExpected() {
        rootValue.setNextKey(targetKey.getKey());

        subject.updateNext(rootValue, nextKey);

        assertEquals(nextKey, rootValue.getNextKeyScopedTo(contractId));
    }

    @Test
    void understandsListScopeForNext() {
        rootValue.setNextKey(targetKey.getKey());

        final var ans = subject.next(rootValue);

        assertEquals(targetKey, ans);
    }

    @Test
    void understandsListScopeForPrev() {
        targetValue.setPrevKey(rootKey.getKey());

        final var ans = subject.prev(targetValue);

        assertEquals(rootKey, ans);
    }

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
