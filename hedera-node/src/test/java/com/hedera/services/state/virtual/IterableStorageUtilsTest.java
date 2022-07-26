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

import static com.hedera.services.state.virtual.IterableStorageUtils.inPlaceUpsertMapping;
import static com.hedera.services.state.virtual.IterableStorageUtils.overwritingUpsertMapping;
import static com.hedera.services.state.virtual.IterableStorageUtils.removeMapping;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.List;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IterableStorageUtilsTest {
    @Mock private VirtualMap<ContractKey, IterableContractValue> storage;

    private final IterableContractValue rootValue =
            new IterableContractValue(rootEvmValue.toArray());
    private final IterableContractValue nextValue =
            new IterableContractValue(nextEvmValue.toArray());
    private final IterableContractValue targetValue =
            new IterableContractValue(targetEvmValue.toArray());

    @Test
    void canListOwnedNfts() {
        final var aKey = EntityNumPair.fromLongs(666L, 1L);
        final var bKey = EntityNumPair.fromLongs(777L, 2L);
        final var cKey = EntityNumPair.fromLongs(888L, 3L);
        final var sixesNft = new MerkleUniqueToken();
        sixesNft.setKey(aKey);
        sixesNft.setNext(bKey.asNftNumPair());
        final var sevensNft = new MerkleUniqueToken();
        sevensNft.setKey(bKey);
        sevensNft.setNext(cKey.asNftNumPair());
        final var eightsNft = new MerkleUniqueToken();
        eightsNft.setKey(cKey);

        final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts = new MerkleMap<>();
        nfts.put(aKey, sixesNft);
        nfts.put(bKey, sevensNft);
        nfts.put(cKey, eightsNft);

        final var expected = "[0.0.666.1, 0.0.777.2, 0.0.888.3]";

        assertEquals("[]", IterableStorageUtils.joinedOwnedNfts(null, nfts));
        final var readable = IterableStorageUtils.joinedOwnedNfts(aKey, nfts);

        assertEquals(expected, readable);
    }

    @Test
    void canListStorageValues() {
        given(storage.get(rootKey)).willReturn(rootValue);
        rootValue.setNextKey(targetKey.getKey());
        given(storage.get(targetKey)).willReturn(targetValue);
        targetValue.setNextKey(nextKey.getKey());
        given(storage.get(nextKey)).willReturn(nextValue);

        final var expected =
                "["
                        + String.join(
                                ", ",
                                List.of(
                                        rootKey + " -> " + CommonUtils.hex(rootValue.getValue()),
                                        targetKey
                                                + " -> "
                                                + CommonUtils.hex(targetValue.getValue()),
                                        nextKey + " -> " + CommonUtils.hex(nextValue.getValue())))
                        + "]";

        assertEquals(expected, IterableStorageUtils.joinedStorageMappings(rootKey, storage));
    }

    @Test
    void canRemoveOnlyValue() {
        given(storage.get(rootKey)).willReturn(rootValue);

        final var newRoot = removeMapping(rootKey, rootKey, storage);

        assertNull(newRoot);
    }

    @Test
    void canListNoStorageValues() {
        assertEquals("[]", IterableStorageUtils.joinedStorageMappings(null, storage));
    }

    @Test
    void canUpdateExistingMappingInPlace() {
        given(storage.getForModify(targetKey)).willReturn(targetValue);

        final var newRoot = inPlaceUpsertMapping(targetKey, nextValue, rootKey, null, storage);

        assertSame(rootKey, newRoot);
        assertArrayEquals(targetValue.getValue(), nextValue.getValue());
    }

    @Test
    void canUpdateExistingMappingOverwriting() {
        final var valueCaptor = ArgumentCaptor.forClass(IterableContractValue.class);
        given(storage.get(targetKey)).willReturn(targetValue);

        final var newRoot = overwritingUpsertMapping(targetKey, nextValue, rootKey, null, storage);

        assertSame(rootKey, newRoot);
        verify(storage).put(eq(targetKey), valueCaptor.capture());
        final var putValue = valueCaptor.getValue();
        assertArrayEquals(nextValue.getValue(), putValue.getValue());
    }

    @Test
    void canInsertToEmptyListInPlace() {
        final var newRoot = inPlaceUpsertMapping(targetKey, targetValue, null, null, storage);

        verify(storage).put(targetKey, targetValue);

        assertSame(targetKey, newRoot);
    }

    @Test
    void canInsertToEmptyListOverwriting() {
        final var newRoot = overwritingUpsertMapping(targetKey, targetValue, null, null, storage);

        verify(storage).put(targetKey, targetValue);

        assertSame(targetKey, newRoot);
    }

    @Test
    void canInsertWithUnknownRootValueInPlace() {
        given(storage.getForModify(targetKey)).willReturn(null);
        given(storage.getForModify(rootKey)).willReturn(rootValue);

        final var newRoot = inPlaceUpsertMapping(targetKey, targetValue, rootKey, null, storage);

        verify(storage).put(targetKey, targetValue);

        assertSame(targetKey, newRoot);
        assertNull(targetValue.getPrevKeyScopedTo(contractNum));
        assertEquals(rootKey, targetValue.getNextKeyScopedTo(contractNum));
        assertEquals(newRoot, rootValue.getPrevKeyScopedTo(contractNum));
    }

    @Test
    void canInsertWithUnknownRootValueOverwriting() {
        final var valueCaptor = ArgumentCaptor.forClass(IterableContractValue.class);
        given(storage.get(targetKey)).willReturn(null);
        given(storage.get(rootKey)).willReturn(rootValue);

        final var newRoot =
                overwritingUpsertMapping(targetKey, targetValue, rootKey, null, storage);

        verify(storage).put(targetKey, targetValue);

        assertSame(targetKey, newRoot);
        assertNull(targetValue.getPrevKeyScopedTo(contractNum));
        assertEquals(rootKey, targetValue.getNextKeyScopedTo(contractNum));
        verify(storage).put(eq(rootKey), valueCaptor.capture());
        final var putValue = valueCaptor.getValue();
        assertEquals(newRoot, putValue.getPrevKeyScopedTo(contractNum));
    }

    @Test
    void canInsertWithPrefetchedValueInPlace() {
        final var newRoot =
                inPlaceUpsertMapping(targetKey, targetValue, rootKey, rootValue, storage);

        assertSame(targetKey, newRoot);
        assertNull(targetValue.getPrevKeyScopedTo(contractNum));
        assertEquals(rootKey, targetValue.getNextKeyScopedTo(contractNum));
        assertEquals(newRoot, rootValue.getPrevKeyScopedTo(contractNum));

        verify(storage).put(targetKey, targetValue);
        verify(storage, never()).getForModify(rootKey);
    }

    @Test
    void canInsertWithPrefetchedValueOverwriting() {
        final var newRoot =
                overwritingUpsertMapping(targetKey, targetValue, rootKey, rootValue, storage);

        assertSame(targetKey, newRoot);
        assertNull(targetValue.getPrevKeyScopedTo(contractNum));
        assertEquals(rootKey, targetValue.getNextKeyScopedTo(contractNum));
        assertEquals(newRoot, rootValue.getPrevKeyScopedTo(contractNum));

        verify(storage).put(targetKey, targetValue);
        verify(storage, never()).getForModify(rootKey);
    }

    private static final long contractNum = 1234;
    private static final AccountID contractId =
            AccountID.newBuilder().setAccountNum(contractNum).build();
    private static final UInt256 targetEvmKey = UInt256.fromHexString("0xaabbcc");
    private static final UInt256 rootEvmKey = UInt256.fromHexString("0xbbccdd");
    private static final UInt256 nextEvmKey = UInt256.fromHexString("0xffeedd");
    private static final ContractKey rootKey = ContractKey.from(contractId, rootEvmKey);
    private static final ContractKey targetKey = ContractKey.from(contractId, targetEvmKey);
    private static final ContractKey nextKey = ContractKey.from(contractId, nextEvmKey);
    private static final UInt256 rootEvmValue =
            UInt256.fromHexString(
                    "0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
    private static final UInt256 targetEvmValue =
            UInt256.fromHexString(
                    "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060");
    private static final UInt256 nextEvmValue =
            UInt256.fromHexString(
                    "0x210aeca1542b62a2a60345a122326fc24ba6bc15424002f6362f13160ef3e563");
}
