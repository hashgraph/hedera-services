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

import static com.hedera.services.state.migration.ReleaseThirtyMigrationTest.registerForMerkleMap;
import static com.hedera.services.state.migration.StateChildIndices.ACCOUNTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.utils.EntityNum;
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

    @Mock private ServicesState mutableState;
    @Mock private VirtualMapFactory virtualMapFactory;
    @Mock private VirtualMap<EntityNumVirtualKey, OnDiskAccount> accountStore;
    @Mock private Function<MerkleAccountState, OnDiskAccount> accountMigrator;

    @Test
    @SuppressWarnings("unchecked")
    void migratesAccountsAsExpected() throws ConstructableRegistryException {
        registerForMerkleMap();

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
                1, mutableState, virtualMapFactory, accountMigrator);

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

    private MerkleAccount nextAccount(final boolean withRecords) {
        return withRecords
                ? new MerkleAccount(List.of(source.nextAccountState(), twoRecords()))
                : new MerkleAccount(List.of(source.nextAccountState(), new FCQueue<>()));
    }

    private FCQueue<ExpirableTxnRecord> twoRecords() {
        final FCQueue<ExpirableTxnRecord> queue = new FCQueue<>();
        queue.offer(source.nextRecord());
        queue.offer(source.nextRecord());
        return queue;
    }
}
