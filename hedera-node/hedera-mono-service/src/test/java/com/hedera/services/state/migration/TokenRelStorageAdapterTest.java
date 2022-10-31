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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.entities.OnDiskTokenRel;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenRelStorageAdapterTest {
    @Mock private MerkleMap<EntityNumPair, MerkleTokenRelStatus> inMemoryRels;
    @Mock private VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> onDiskRels;
    @Mock private MerkleTokenRelStatus inMemoryRel;
    @Mock private OnDiskTokenRel onDiskRel;

    private static final Hash SOME_HASH = new Hash();
    private static final EntityNumPair SOME_PAIR = new EntityNumPair(666_666L);
    private static final EntityNumVirtualKey SOME_KEY = EntityNumVirtualKey.fromPair(SOME_PAIR);

    private TokenRelStorageAdapter subject;

    @Test
    void summariesWorkForOnDisk() {
        withOnDiskSubject();
        given(onDiskRels.getHash()).willReturn(SOME_HASH);
        assertSame(SOME_HASH, subject.getHash());
        assertTrue(subject.areOnDisk());
        assertSame(onDiskRels, subject.getOnDiskRels());
    }

    @Test
    void summariesWorkForInMemory() {
        withInMemorySubject();
        given(inMemoryRels.getHash()).willReturn(SOME_HASH);
        assertSame(SOME_HASH, subject.getHash());
        assertFalse(subject.areOnDisk());
        assertSame(inMemoryRels, subject.getInMemoryRels());
    }

    @Test
    void archiveWorksForOnDisk() {
        withOnDiskSubject();
        subject.archive();
        verifyNoInteractions(onDiskRels);
    }

    @Test
    void archiveWorkForInMemory() {
        withInMemorySubject();
        subject.archive();
        verify(inMemoryRels).archive();
    }

    @Test
    void getWorksForOnDisk() {
        withOnDiskSubject();
        given(onDiskRels.get(SOME_KEY)).willReturn(onDiskRel);
        assertSame(onDiskRel, subject.get(SOME_PAIR));
    }

    @Test
    void getWorksForInMemory() {
        withInMemorySubject();
        given(inMemoryRels.get(SOME_PAIR)).willReturn(inMemoryRel);
        assertSame(inMemoryRel, subject.get(SOME_PAIR));
    }

    @Test
    void g4MWorksForOnDisk() {
        withOnDiskSubject();
        given(onDiskRels.getForModify(SOME_KEY)).willReturn(onDiskRel);
        assertSame(onDiskRel, subject.getForModify(SOME_PAIR));
    }

    @Test
    void sizeWorksForInMemory() {
        withInMemorySubject();
        given(inMemoryRels.size()).willReturn(123);
        assertEquals(123, subject.size());
    }

    @Test
    void sizeWorksForOnDisk() {
        withOnDiskSubject();
        given(onDiskRels.size()).willReturn(123L);
        assertEquals(123L, subject.size());
    }

    @Test
    void containsKeyWorksForOnDisk() {
        withOnDiskSubject();
        given(onDiskRels.containsKey(SOME_KEY)).willReturn(true);
        assertTrue(subject.containsKey(SOME_PAIR));
    }

    @Test
    void containsKeyWorksForInMemory() {
        withInMemorySubject();
        given(inMemoryRels.containsKey(SOME_PAIR)).willReturn(true);
        assertTrue(subject.containsKey(SOME_PAIR));
    }

    @Test
    void g4mWorksForInMemory() {
        withInMemorySubject();
        given(inMemoryRels.getForModify(SOME_PAIR)).willReturn(inMemoryRel);
        assertSame(inMemoryRel, subject.getForModify(SOME_PAIR));
    }

    @Test
    void putWorksForOnDisk() {
        withOnDiskSubject();
        subject.put(SOME_PAIR, onDiskRel);
        verify(onDiskRel).setKey(SOME_PAIR);
        verify(onDiskRels).put(SOME_KEY, onDiskRel);
    }

    @Test
    void putWorksForInMemory() {
        withInMemorySubject();
        subject.put(SOME_PAIR, inMemoryRel);
        verify(inMemoryRels).put(SOME_PAIR, inMemoryRel);
    }

    @Test
    void removeWorksForOnDisk() {
        withOnDiskSubject();
        subject.remove(SOME_PAIR);
        verify(onDiskRels).remove(SOME_KEY);
    }

    @Test
    void removeWorksForInMemory() {
        withInMemorySubject();
        subject.remove(SOME_PAIR);
        verify(inMemoryRels).remove(SOME_PAIR);
    }

    private void withInMemorySubject() {
        subject = TokenRelStorageAdapter.fromInMemory(inMemoryRels);
    }

    private void withOnDiskSubject() {
        subject = TokenRelStorageAdapter.fromOnDisk(onDiskRels);
    }
}
