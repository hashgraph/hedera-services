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
package com.hedera.services.ledger.backing.pure;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PureBackingAccountsTest {
    private final AccountID a = asAccount("0.0.3");
    private final AccountID b = asAccount("0.0.1");
    private final EntityNum aKey = EntityNum.fromAccountId(a);
    private final EntityNum bKey = EntityNum.fromAccountId(b);
    private final MerkleAccount aValue = MerkleAccountFactory.newAccount().balance(123L).get();

    private MerkleMap<EntityNum, MerkleAccount> map;
    private PureBackingAccounts subject;

    @BeforeEach
    void setup() {
        map = mock(MerkleMap.class);

        subject = new PureBackingAccounts(() -> AccountStorageAdapter.fromInMemory(map));
    }

    @Test
    void mutationsNotSupported() {
        // expect:
        assertThrows(UnsupportedOperationException.class, () -> subject.remove(null));
        assertThrows(UnsupportedOperationException.class, () -> subject.put(null, null));
    }

    @Test
    void delegatesGet() {
        given(map.get(aKey)).willReturn(aValue);

        // then:
        assertSame(aValue, subject.getRef(a));
    }

    @Test
    void delegatesContains() {
        given(map.containsKey(aKey)).willReturn(false);
        given(map.containsKey(bKey)).willReturn(true);

        // then:
        assertFalse(subject.contains(a));
        assertTrue(subject.contains(b));
        // and:
        verify(map, times(2)).containsKey(any());
    }

    @Test
    void delegatesIdSet() {
        var ids = Set.of(aKey, bKey);
        var expectedIds = Set.of(a, b);

        given(map.keySet()).willReturn(ids);

        // expect:
        assertEquals(expectedIds, subject.idSet());
    }

    @Test
    void delegatesUnsafeGet() {
        given(map.get(aKey)).willReturn(aValue);

        // expect:
        assertEquals(aValue, subject.getImmutableRef(a));
    }

    @Test
    void delegatesSize() {
        var size = 123;
        given(map.size()).willReturn(size);

        // expect:
        assertEquals(size, subject.size());
    }
}
