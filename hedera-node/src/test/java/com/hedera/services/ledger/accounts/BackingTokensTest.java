/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts;

import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.ledger.backing.BackingTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackingTokensTest {
    private final TokenID a = asToken("0.0.3");
    private final TokenID b = asToken("0.0.1");
    private final EntityNum aKey = EntityNum.fromTokenId(a);
    private final MerkleToken aValue = new MerkleToken();

    private MerkleMap<EntityNum, MerkleToken> map;
    MerkleMap<EntityNum, MerkleToken> mockedMap;
    private BackingTokens subject;

    @BeforeEach
    void setup() {
        map = new MerkleMap<>();

        map.put(aKey, aValue);

        subject = new BackingTokens(() -> map);
    }

    @Test
    void delegatesContains() {
        // then:
        assertTrue(subject.contains(a));
        assertFalse(subject.contains(b));
        // and:
    }

    @Test
    void delegatesIdSet() {
        var expectedIds = Set.of(a);
        // expect:
        assertEquals(expectedIds, subject.idSet());
    }

    @Test
    void delegatesUnsafeGet() {
        // expect:
        assertEquals(aValue, subject.getImmutableRef(a));
    }

    @Test
    void getRefWorks() {
        // given:
        setupMocked();
        // when:
        subject.getRef(a);
        // then:
        verify(mockedMap).getForModify(EntityNum.fromTokenId(a));
    }

    @Test
    void getImmutableRefWorks() {
        // given:
        setupMocked();
        // when:
        subject.getImmutableRef(a);
        // then:
        verify(mockedMap).get(EntityNum.fromTokenId(a));
    }

    @Test
    void delegatesSize() {
        // expect:
        assertEquals(1, subject.size());
    }

    @Test
    void putWorks() {
        // when:
        subject.put(a, aValue);

        // then:
        assertEquals(aValue, subject.getImmutableRef(a));
    }

    @Test
    void putWorksWithDelegate() {
        // given:
        setupMocked();
        // when:
        subject.put(a, aValue);

        // then:
        verify(mockedMap).containsKey(EntityNum.fromTokenId(a));
        verify(mockedMap).put(EntityNum.fromTokenId(a), aValue);
    }

    @Test
    void removeWorks() {
        // when:
        subject.remove(a);

        // then:
        assertFalse(subject.contains(a));
    }

    void setupMocked() {
        mockedMap = mock(MerkleMap.class);
        subject = new BackingTokens(() -> mockedMap);
    }
}
