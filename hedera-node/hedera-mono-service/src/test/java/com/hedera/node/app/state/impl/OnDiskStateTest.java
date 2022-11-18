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
class OnDiskStateTest {
    //    private final Instant lastModifiedTime = Instant.ofEpochSecond(1_234_567L);
    //    private final EntityNumVirtualKey num = new EntityNumVirtualKey(2L);
    //    private static final String TOKENS = "TOKENS";
    //    @Mock private UniqueTokenValue mockNft;
    //    @Mock private VirtualMap<EntityNumVirtualKey, UniqueTokenValue> nftsMap;
    //
    //    private OnDiskState subject;
    //
    //    @BeforeEach
    //    void setUp() {
    //        subject = new OnDiskState<>("TOKENS", nftsMap, lastModifiedTime);
    //    }
    //
    //    @Test
    //    void gettersWork() {
    //        assertEquals(lastModifiedTime, subject.getLastModifiedTime());
    //        assertEquals(TOKENS, subject.getStateKey());
    //    }
    //
    //    @Test
    //    void readsValueFromMerkleMap() {
    //        given(nftsMap.get(num)).willReturn(mockNft);
    //
    //        assertEquals(Optional.of(mockNft), subject.get(num));
    //    }
    //
    //    @Test
    //    void cachesReadKeysFromState() {
    //        final var unknownKey = new EntityNumVirtualKey(20L);
    //        given(nftsMap.get(num)).willReturn(mockNft);
    //        assertEquals(Optional.of(mockNft), subject.get(num));
    //
    //        verify(nftsMap).get(num);
    //        assertTrue(subject.getReadCache().containsKey(num));
    //
    //        assertEquals(Optional.of(mockNft), subject.get(num));
    //        verify(nftsMap, times(1)).get(num);
    //
    //        assertEquals(Optional.empty(), subject.get(unknownKey));
    //        assertFalse(subject.getReadCache().containsKey(unknownKey));
    //    }
}
