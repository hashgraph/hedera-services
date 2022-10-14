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
package com.hedera.services.base.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InMemoryStateImplTest {
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private final Instant lastModifiedTime = Instant.ofEpochSecond(1_234_567L);
    private final EntityNum num = EntityNum.fromLong(2L);

    @Mock private MerkleMap<EntityNum, MerkleAccount> accountsMap;
    @Mock private MerkleAccount mockAccount;

    private InMemoryStateImpl subject;

    @BeforeEach
    void setUp() {
        subject = new InMemoryStateImpl(ACCOUNTS_KEY, accountsMap, lastModifiedTime);
    }

    @Test
    void gettersWork() {
        assertEquals(lastModifiedTime, subject.getLastModifiedTime());
        assertEquals(ACCOUNTS_KEY, subject.getStateKey());
    }

    @Test
    void readsValueFromMerkleMap() {
        given(accountsMap.get(num)).willReturn(mockAccount);

        assertEquals(Optional.of(mockAccount), subject.get(num));
    }

    @Test
    void initializesToEmptyMerkleMapIfNotProvided() {
        subject = new InMemoryStateImpl(ACCOUNTS_KEY, lastModifiedTime);
        assertEquals(Optional.empty(), subject.get(num));
    }

    @Test
    void cachesReadKeysFromState() {
        final var unknownKey = EntityNum.fromLong(20L);
        given(accountsMap.get(num)).willReturn(mockAccount);
        assertEquals(Optional.of(mockAccount), subject.get(num));

        verify(accountsMap).get(num);
        assertTrue(subject.getReadKeys().containsKey(num));

        assertEquals(Optional.of(mockAccount), subject.get(num));
        verify(accountsMap, times(1)).get(num);

        assertEquals(Optional.empty(), subject.get(unknownKey));
        assertFalse(subject.getReadKeys().containsKey(unknownKey));
    }
}
