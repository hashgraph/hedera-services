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

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InMemoryStateTest {
    //    private final Instant lastModifiedTime = Instant.ofEpochSecond(1_234_567L);
    //    private final EntityNum num = EntityNum.fromLong(2L);
    //    private static final String ACCOUNTS = "ACCOUNTS";
    //    @Mock private MerkleMap<EntityNum, MerkleAccount> accountsMap;
    //    @Mock private MerkleAccount mockAccount;
    //
    //    private InMemoryState subject;
    //
    //    @BeforeEach
    //    void setUp() {
    //        subject = new InMemoryState(ACCOUNTS, accountsMap, lastModifiedTime);
    //    }
    //
    //    @Test
    //    void gettersWork() {
    //        assertEquals(lastModifiedTime, subject.getLastModifiedTime());
    //        assertEquals(ACCOUNTS, subject.getStateKey());
    //    }
    //
    //    @Test
    //    void readsValueFromMerkleMap() {
    //        given(accountsMap.get(num)).willReturn(mockAccount);
    //
    //        assertEquals(Optional.of(mockAccount), subject.get(num));
    //    }
    //
    //    @Test
    //    void initializesToEmptyMerkleMapIfNotProvided() {
    //        subject = new InMemoryState(ACCOUNTS, lastModifiedTime);
    //        assertEquals(Optional.empty(), subject.get(num));
    //    }
    //
    //    @Test
    //    void cachesReadKeysFromState() {
    //        final var unknownKey = EntityNum.fromLong(20L);
    //        given(accountsMap.get(num)).willReturn(mockAccount);
    //        assertEquals(Optional.of(mockAccount), subject.get(num));
    //
    //        verify(accountsMap).get(num);
    //        assertTrue(subject.getReadCache().containsKey(num));
    //
    //        assertEquals(Optional.of(mockAccount), subject.get(num));
    //        verify(accountsMap, times(1)).get(num);
    //
    //        assertEquals(Optional.empty(), subject.get(unknownKey));
    //        assertFalse(subject.getReadCache().containsKey(unknownKey));
    //    }
}
