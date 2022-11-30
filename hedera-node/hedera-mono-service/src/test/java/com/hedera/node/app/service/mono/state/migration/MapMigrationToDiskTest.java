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
package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.ACCOUNTS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOKEN_ASSOCIATIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccountState;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapMigrationToDiskTest {
    private final SeededPropertySource source = SeededPropertySource.forSerdeTest(14, 1);

    private final EntityNum aNum = EntityNum.fromInt(666);
    private final EntityNum bNum = EntityNum.fromInt(777);
    private final EntityNumPair aNumPair = new EntityNumPair(666L);
    private final EntityNumPair bNumPair = new EntityNumPair(777L);

    @Mock private ServicesState mutableState;
    @Mock private VirtualMapFactory virtualMapFactory;
    @Mock private VirtualMap<EntityNumVirtualKey, OnDiskAccount> accountStore;
    @Mock private VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> tokenRelStore;
    @Mock private Function<MerkleAccountState, OnDiskAccount> accountMigrator;
    @Mock private Function<MerkleTokenRelStatus, OnDiskTokenRel> tokenRelMigrator;

    @Test
    @SuppressWarnings("unchecked")
    void migratesAccountsAsExpected() throws ConstructableRegistryException {
        ReleaseThirtyMigrationTest.registerForAccountsMerkleMap();

        final var accountsOnly = new ToDiskMigrations(true, false);
        final var aAccount = nextAccount(false);
        final var bAccount = nextAccount(true);
        final MerkleMap<EntityNum, MerkleAccount> liveAccounts = new MerkleMap<>();
        liveAccounts.put(aNum, aAccount);
        liveAccounts.put(bNum, bAccount);

        final ArgumentCaptor<MerkleMap<EntityNum, MerklePayerRecords>> captor =
                forClass(MerkleMap.class);
        final var aPretendOnDiskAccount = new OnDiskAccount();
        final var bPretendOnDiskAccount = new OnDiskAccount();

        given(virtualMapFactory.newOnDiskAccountStorage()).willReturn(accountStore);
        given(accountStore.copy()).willReturn(accountStore);
        given(mutableState.getChild(ACCOUNTS)).willReturn(liveAccounts);
        given(accountMigrator.apply(aAccount.state())).willReturn(aPretendOnDiskAccount);
        given(accountMigrator.apply(bAccount.state())).willReturn(bPretendOnDiskAccount);

        MapMigrationToDisk.migrateToDiskAsApropos(
                1,
                mutableState,
                accountsOnly,
                virtualMapFactory,
                accountMigrator,
                tokenRelMigrator);

        verify(mutableState).setChild(ACCOUNTS, accountStore);
        verify(mutableState).setChild(eq(StateChildIndices.PAYER_RECORDS), captor.capture());
        final var payerRecords = captor.getValue();
        final var aRecords = payerRecords.get(aNum);
        final var bRecords = payerRecords.get(bNum);
        assertNotNull(aRecords);
        assertNotNull(bRecords);
        assertEquals(aAccount.records(), aRecords.readOnlyQueue());
        assertEquals(bAccount.records(), bRecords.readOnlyQueue());
        // and:
        verify(accountStore).put(new EntityNumVirtualKey(aNum.longValue()), aPretendOnDiskAccount);
        verify(accountStore).put(new EntityNumVirtualKey(bNum.longValue()), bPretendOnDiskAccount);
        // and:
        verify(accountStore, times(2)).copy();
    }

    @Test
    @SuppressWarnings("unchecked")
    void migratesTokenRelsAsExpected() throws ConstructableRegistryException {
        ReleaseThirtyMigrationTest.registerForTokenRelsMerkleMap();

        final var relsOnly = new ToDiskMigrations(false, true);
        final var aRel = nextRelStats(false);
        final var bRel = nextRelStats(true);
        final MerkleMap<EntityNumPair, MerkleTokenRelStatus> liveRels = new MerkleMap<>();
        liveRels.put(aNumPair, aRel);
        liveRels.put(bNumPair, bRel);

        final ArgumentCaptor<MerkleMap<EntityNum, MerklePayerRecords>> captor =
                forClass(MerkleMap.class);
        final var aPretendOnDiskRel = new OnDiskTokenRel();
        final var bPretendOnDiskRel = new OnDiskTokenRel();

        given(virtualMapFactory.newOnDiskTokenRels()).willReturn(tokenRelStore);
        given(tokenRelStore.copy()).willReturn(tokenRelStore);
        given(mutableState.getChild(TOKEN_ASSOCIATIONS)).willReturn(liveRels);
        given(tokenRelMigrator.apply(aRel)).willReturn(aPretendOnDiskRel);
        given(tokenRelMigrator.apply(bRel)).willReturn(bPretendOnDiskRel);

        MapMigrationToDisk.migrateToDiskAsApropos(
                1, mutableState, relsOnly, virtualMapFactory, accountMigrator, tokenRelMigrator);

        verify(mutableState).setChild(TOKEN_ASSOCIATIONS, tokenRelStore);
        // and:
        verify(tokenRelStore).put(EntityNumVirtualKey.fromPair(aNumPair), aPretendOnDiskRel);
        verify(tokenRelStore).put(EntityNumVirtualKey.fromPair(bNumPair), bPretendOnDiskRel);
        // and:
        verify(tokenRelStore, times(2)).copy();
    }

    private MerkleAccount nextAccount(final boolean withRecords) {
        return withRecords
                ? new MerkleAccount(List.of(source.nextAccountState(), twoRecords()))
                : new MerkleAccount(List.of(source.nextAccountState(), new FCQueue<>()));
    }

    private MerkleTokenRelStatus nextRelStats(final boolean withBalance) {
        final var someRel = new MerkleTokenRelStatus();
        if (withBalance) {
            someRel.setBalance(123_456L);
        }
        return someRel;
    }

    private FCQueue<ExpirableTxnRecord> twoRecords() {
        final FCQueue<ExpirableTxnRecord> queue = new FCQueue<>();
        queue.offer(source.nextRecord());
        queue.offer(source.nextRecord());
        return queue;
    }
}
