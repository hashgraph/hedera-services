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
package com.hedera.node.app.state.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.LongMerkleLeaf;

class InMemoryStateTest extends MutableStateBaseTest<LongMerkleLeaf> {
    private MerkleMap<Long, LongMerkleLeaf> merkleMap;
    private InMemoryState<Long, LongMerkleLeaf> state;

    @BeforeEach
    void setUp() {
        merkleMap = new MerkleMap<>();
        state = new InMemoryState<>(STATE_KEY, merkleMap);
    }

    @Override
    protected MutableStateBase<Long, LongMerkleLeaf> state() {
        return state;
    }

    @Override
    protected LongMerkleLeaf newValue(long key) {
        final var value = new LongMerkleLeaf(key, 1000 + key);
        merkleMap.put(key, value);
        return value;
    }

    @Test
    @DisplayName("Constructor requires a non-null state key")
    void nullStateKeyThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new InMemoryState<>(null, merkleMap));
    }

    @Test
    @DisplayName("Constructor requires a non-null merkle map")
    void nullMapThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new InMemoryState<>("KEY", null));
    }
}
