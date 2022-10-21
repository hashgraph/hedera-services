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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountStorageAdapterTest {
    private static final EntityNum SOME_NUM = EntityNum.fromInt(1234);
    private static final Hash SOME_HASH = new Hash();
    private static final Set<EntityNum> SOME_KEY_SET = Set.of(SOME_NUM);
    private static final EntityNumVirtualKey SOME_KEY = EntityNumVirtualKey.from(SOME_NUM);
    private static final MerkleAccount IN_MEMORY_STAND_IN = new MerkleAccount();
    private final OnDiskAccount onDiskStandIn = new OnDiskAccount();

    @Mock private MerkleMap<EntityNum, MerkleAccount> inMemoryAccounts;
    @Mock private MerkleMap<EntityNum, MerklePayerRecords> payerRecords;
    @Mock private VirtualMap<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts;
    @Mock private BiConsumer<EntityNum, HederaAccount> visitor;
    @Mock private VirtualMapDataAccess virtualMapDataAccess;

    private AccountStorageAdapter subject;

    @Test
    void onDiskProfileAsExpected() {
        withOnDiskSubject();
        assertTrue(subject.areOnDisk());
        assertNull(subject.getInMemoryAccounts());
        assertNotNull(subject.getOnDiskAccounts());
    }

    @Test
    void inMemoryProfileAsExpected() {
        withInMemorySubject();
        assertFalse(subject.areOnDisk());
        assertNotNull(subject.getInMemoryAccounts());
        assertNull(subject.getOnDiskAccounts());
    }

    @Test
    void getForModifyIdentifiesOnDisk() {
        withOnDiskSubject();
        given(onDiskAccounts.getForModify(SOME_KEY)).willReturn(onDiskStandIn);
        assertSame(onDiskStandIn, subject.getForModify(SOME_NUM));
    }

    @Test
    void getForModifyIdentifiesInMemory() {
        withInMemorySubject();
        given(inMemoryAccounts.getForModify(SOME_NUM)).willReturn(IN_MEMORY_STAND_IN);
        assertSame(IN_MEMORY_STAND_IN, subject.getForModify(SOME_NUM));
    }

    @Test
    void getIdentifiesOnDisk() {
        withOnDiskSubject();
        given(onDiskAccounts.get(SOME_KEY)).willReturn(onDiskStandIn);
        assertSame(onDiskStandIn, subject.get(SOME_NUM));
    }

    @Test
    void getIdentifiesInMemory() {
        withInMemorySubject();
        given(inMemoryAccounts.get(SOME_NUM)).willReturn(IN_MEMORY_STAND_IN);
        assertSame(IN_MEMORY_STAND_IN, subject.get(SOME_NUM));
    }

    @Test
    void putIdentifiesOnDiskAndSetsKey() {
        withOnDiskSubject();
        subject.put(SOME_NUM, onDiskStandIn);
        verify(onDiskAccounts).put(SOME_KEY, onDiskStandIn);
        assertEquals(SOME_KEY.getKeyAsLong(), onDiskStandIn.number());
    }

    @Test
    void putIdentifiesInMemory() {
        withInMemorySubject();
        subject.put(SOME_NUM, IN_MEMORY_STAND_IN);
        verify(inMemoryAccounts).put(SOME_NUM, IN_MEMORY_STAND_IN);
    }

    @Test
    void removeIdentifiesOnDisk() {
        withOnDiskSubject();
        subject.remove(SOME_NUM);
        verify(onDiskAccounts).remove(SOME_KEY);
    }

    @Test
    void removeIdentifiesInMemory() {
        withInMemorySubject();
        subject.remove(SOME_NUM);
        verify(inMemoryAccounts).remove(SOME_NUM);
    }

    @Test
    void containsKeyIdentifiesOnDisk() {
        withOnDiskSubject();
        given(onDiskAccounts.containsKey(SOME_KEY)).willReturn(true);
        assertTrue(subject.containsKey(SOME_NUM));
    }

    @Test
    void containsKeyIdentifiesInMemory() {
        withInMemorySubject();
        given(inMemoryAccounts.containsKey(SOME_NUM)).willReturn(true);
        assertTrue(subject.containsKey(SOME_NUM));
    }

    @Test
    void getHashIdentifiesOnDisk() {
        withOnDiskSubject();
        given(onDiskAccounts.getHash()).willReturn(SOME_HASH);
        assertSame(SOME_HASH, subject.getHash());
    }

    @Test
    void getHashIdentifiesInMemory() {
        withInMemorySubject();
        given(inMemoryAccounts.getHash()).willReturn(SOME_HASH);
        assertSame(SOME_HASH, subject.getHash());
    }

    @Test
    void keySetIdentifiesOnDisk() {
        withOnDiskSubject();
        given(payerRecords.keySet()).willReturn(SOME_KEY_SET);
        assertSame(SOME_KEY_SET, subject.keySet());
    }

    @Test
    void keySetIdentifiesInMemory() {
        withInMemorySubject();
        given(inMemoryAccounts.keySet()).willReturn(SOME_KEY_SET);
        assertSame(SOME_KEY_SET, subject.keySet());
    }

    @Test
    void onDiskArchivalIsNoop() {
        withOnDiskSubject();
        subject.archive();
        verifyNoInteractions(onDiskAccounts);
    }

    @Test
    void inMemArchivalPropagates() {
        withInMemorySubject();
        subject.archive();
        verify(inMemoryAccounts).archive();
    }

    @Test
    void inMemForEachDelegates() {
        withInMemorySubject();
        subject.forEach(visitor);
        verify(inMemoryAccounts).forEach(visitor);
    }

    @Test
    @SuppressWarnings("unchecked")
    void onDiskForEachDelegates() throws InterruptedException {
        final ArgumentCaptor<InterruptableConsumer<Pair<EntityNumVirtualKey, OnDiskAccount>>>
                captor = ArgumentCaptor.forClass(InterruptableConsumer.class);
        withOnDiskSubject();
        subject.forEach(visitor);
        verify(virtualMapDataAccess)
                .extractVirtualMapData(
                        eq(getStaticThreadManager()), eq(onDiskAccounts), captor.capture(), eq(32));
        captor.getValue().accept(Pair.of(SOME_KEY, onDiskStandIn));
        verify(visitor).accept(SOME_NUM, onDiskStandIn);
    }

    @Test
    @SuppressWarnings("unchecked")
    void onDiskPropagatesInterruption() throws InterruptedException {
        withOnDiskSubject();
        willThrow(InterruptedException.class)
                .given(virtualMapDataAccess)
                .extractVirtualMapData(
                        eq(getStaticThreadManager()),
                        eq(onDiskAccounts),
                        any(InterruptableConsumer.class),
                        eq(32));
        assertThrows(IllegalStateException.class, () -> subject.forEach(visitor));
    }

    private void withInMemorySubject() {
        subject = AccountStorageAdapter.fromInMemory(inMemoryAccounts);
    }

    private void withOnDiskSubject() {
        subject =
                AccountStorageAdapter.fromOnDisk(
                        virtualMapDataAccess, payerRecords, onDiskAccounts);
    }
}
