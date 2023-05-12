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

package com.hedera.node.app.service.mono.state.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class UniqueTokenMapAdapterTest {

    private UniqueTokenMapAdapter merkleMapAdapter;
    private UniqueTokenMapAdapter virtualMapAdapter;

    @Mock
    public MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap;

    @Mock
    public VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap;

    @BeforeEach
    void setUp() {
        merkleMapAdapter = UniqueTokenMapAdapter.wrap(merkleMap);
        virtualMapAdapter = UniqueTokenMapAdapter.wrap(VirtualMapLike.from(virtualMap));
    }

    @Test
    void testIsVirtual() {
        assertThat(merkleMapAdapter.isVirtual()).isFalse();
        assertThat(virtualMapAdapter.isVirtual()).isTrue();
    }

    @Test
    void testVirtualMapGetter() {
        assertThat(merkleMapAdapter.virtualMap()).isNull();
        assertInstanceOf(VirtualMapLike.class, virtualMapAdapter.virtualMap());
    }

    @Test
    void testMerkleMapGetter() {
        assertThat(merkleMapAdapter.merkleMap()).isSameAs(merkleMap);
        assertThat(virtualMapAdapter.merkleMap()).isNull();
    }

    @Test
    void testSizeReturnsSizeOfMap() {
        when(merkleMap.size()).thenReturn(3);
        when(virtualMap.size()).thenReturn(2L);
        assertThat(merkleMapAdapter.size()).isEqualTo(3L);
        assertThat(virtualMapAdapter.size()).isEqualTo(2L);
    }

    @Test
    void testContainsKey() {
        when(merkleMap.containsKey(EntityNumPair.fromNftId(NftId.withDefaultShardRealm(1, 2))))
                .thenReturn(true);
        when(virtualMap.containsKey(UniqueTokenKey.from(NftId.withDefaultShardRealm(0, 3))))
                .thenReturn(true);

        assertThat(merkleMapAdapter.containsKey(NftId.withDefaultShardRealm(1, 2)))
                .isTrue();
        assertThat(merkleMapAdapter.containsKey(NftId.withDefaultShardRealm(0, 3)))
                .isFalse();

        assertThat(virtualMapAdapter.containsKey(NftId.withDefaultShardRealm(0, 3)))
                .isTrue();
        assertThat(virtualMapAdapter.containsKey(NftId.withDefaultShardRealm(1, 2)))
                .isFalse();
    }

    @Test
    void testPutWithValidValue() {
        final var merkleKey = NftId.withDefaultShardRealm(1, 2);
        final var virtualKey = NftId.withDefaultShardRealm(3, 4);
        final var merkleValue = UniqueTokenAdapter.wrap(mock(MerkleUniqueToken.class));
        final var virtualValue = UniqueTokenAdapter.wrap(mock(UniqueTokenValue.class));

        merkleMapAdapter.put(merkleKey, merkleValue);
        virtualMapAdapter.put(virtualKey, virtualValue);

        verify(merkleMap, times(1)).put(EntityNumPair.fromNftId(merkleKey), merkleValue.merkleUniqueToken());
        verify(virtualMap, times(1)).put(UniqueTokenKey.from(virtualKey), virtualValue.uniqueTokenValue());
    }

    @Test
    void testPutWrongType() {
        final var merkleKey = NftId.withDefaultShardRealm(1, 2);
        final var virtualKey = NftId.withDefaultShardRealm(3, 4);
        final var merkleValue = UniqueTokenAdapter.wrap(mock(MerkleUniqueToken.class));
        final var virtualValue = UniqueTokenAdapter.wrap(mock(UniqueTokenValue.class));

        assertThrows(UnsupportedOperationException.class, () -> merkleMapAdapter.put(merkleKey, virtualValue));
        assertThrows(UnsupportedOperationException.class, () -> virtualMapAdapter.put(virtualKey, merkleValue));
    }

    @Test
    void testGet() {
        final var merkleKey = NftId.withDefaultShardRealm(1, 2);
        final var virtualKey = NftId.withDefaultShardRealm(3, 4);
        final var merkleValue = UniqueTokenAdapter.wrap(mock(MerkleUniqueToken.class));
        final var virtualValue = UniqueTokenAdapter.wrap(mock(UniqueTokenValue.class));

        when(merkleMap.get(EntityNumPair.fromNftId(merkleKey))).thenReturn(merkleValue.merkleUniqueToken());
        when(virtualMap.get(UniqueTokenKey.from(virtualKey))).thenReturn(virtualValue.uniqueTokenValue());
        final var merkleResult = merkleMapAdapter.get(merkleKey);
        assertThat(merkleResult).isEqualTo(merkleValue);
        assertThat(merkleResult.merkleUniqueToken()).isSameAs(merkleValue.merkleUniqueToken());

        final var virtualResult = virtualMapAdapter.get(virtualKey);
        assertThat(virtualResult).isEqualTo(virtualValue);
        assertThat(virtualResult.uniqueTokenValue()).isSameAs(virtualValue.uniqueTokenValue());
    }

    @Test
    void testGetForModify() {
        final var merkleKey = NftId.withDefaultShardRealm(1, 2);
        final var virtualKey = NftId.withDefaultShardRealm(3, 4);
        final var merkleValue = UniqueTokenAdapter.wrap(mock(MerkleUniqueToken.class));
        final var virtualValue = UniqueTokenAdapter.wrap(mock(UniqueTokenValue.class));

        when(merkleMap.getForModify(EntityNumPair.fromNftId(merkleKey))).thenReturn(merkleValue.merkleUniqueToken());
        when(virtualMap.getForModify(UniqueTokenKey.from(virtualKey))).thenReturn(virtualValue.uniqueTokenValue());
        final var merkleResult = merkleMapAdapter.getForModify(merkleKey);
        assertThat(merkleResult).isEqualTo(merkleValue);
        assertThat(merkleResult.merkleUniqueToken()).isSameAs(merkleValue.merkleUniqueToken());

        final var virtualResult = virtualMapAdapter.getForModify(virtualKey);
        assertThat(virtualResult).isEqualTo(virtualValue);
        assertThat(virtualResult.uniqueTokenValue()).isSameAs(virtualValue.uniqueTokenValue());
    }

    @Test
    void testRemove() {
        final var merkleKey = NftId.withDefaultShardRealm(1, 2);
        merkleMapAdapter.remove(merkleKey);
        verify(merkleMap, times(1)).remove(EntityNumPair.fromNftId(merkleKey));

        final var virtualKey = NftId.withDefaultShardRealm(3, 4);
        virtualMapAdapter.remove(virtualKey);
        verify(virtualMap, times(1)).remove(UniqueTokenKey.from(virtualKey));
    }

    @Test
    void testHash() {
        final Hash merkleHash = new Hash();
        final Hash virtualHash = new Hash();

        when(merkleMap.getHash()).thenReturn(merkleHash);
        when(virtualMap.getHash()).thenReturn(virtualHash);

        assertThat(merkleMapAdapter.getHash()).isSameAs(merkleHash);
        assertThat(virtualMapAdapter.getHash()).isSameAs(virtualHash);
    }
}
