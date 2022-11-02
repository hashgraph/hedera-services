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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountStorageAdapter {
    private static final Logger log = LogManager.getLogger(AccountStorageAdapter.class);
    private static final int THREAD_COUNT = 32;
    private final boolean accountsOnDisk;
    private final @Nullable VirtualMapDataAccess virtualMapDataAccess;
    private final @Nullable MerkleMap<EntityNum, MerkleAccount> inMemoryAccounts;
    private final @Nullable MerkleMap<EntityNum, MerklePayerRecords> payerRecords;
    private final @Nullable VirtualMap<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts;

    public static AccountStorageAdapter fromInMemory(
            final MerkleMap<EntityNum, MerkleAccount> accounts) {
        return new AccountStorageAdapter(accounts, null, null, null);
    }

    public static AccountStorageAdapter fromOnDisk(
            final VirtualMapDataAccess virtualMapDataAccess,
            final MerkleMap<EntityNum, MerklePayerRecords> payerRecords,
            final VirtualMap<EntityNumVirtualKey, OnDiskAccount> accounts) {
        return new AccountStorageAdapter(null, virtualMapDataAccess, payerRecords, accounts);
    }

    private AccountStorageAdapter(
            @Nullable final MerkleMap<EntityNum, MerkleAccount> inMemoryAccounts,
            final @Nullable VirtualMapDataAccess virtualMapDataAccess,
            @Nullable final MerkleMap<EntityNum, MerklePayerRecords> payerRecords,
            @Nullable final VirtualMap<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts) {
        if (inMemoryAccounts != null) {
            this.accountsOnDisk = false;
            this.inMemoryAccounts = inMemoryAccounts;
            this.onDiskAccounts = null;
            this.payerRecords = null;
            this.virtualMapDataAccess = null;
        } else {
            this.accountsOnDisk = true;
            this.inMemoryAccounts = null;
            this.onDiskAccounts = onDiskAccounts;
            this.payerRecords = payerRecords;
            this.virtualMapDataAccess = virtualMapDataAccess;
        }
    }

    public HederaAccount get(final EntityNum num) {
        return accountsOnDisk
                ? onDiskAccounts.get(EntityNumVirtualKey.from(num))
                : inMemoryAccounts.get(num);
    }

    public HederaAccount getForModify(final EntityNum num) {
        return accountsOnDisk
                ? onDiskAccounts.getForModify(EntityNumVirtualKey.from(num))
                : inMemoryAccounts.getForModify(num);
    }

    public void put(final EntityNum num, final HederaAccount wrapper) {
        if (accountsOnDisk) {
            wrapper.setEntityNum(num);
            onDiskAccounts.put(EntityNumVirtualKey.from(num), (OnDiskAccount) wrapper);
        } else {
            inMemoryAccounts.put(num, (MerkleAccount) wrapper);
        }
    }

    public void remove(final EntityNum num) {
        if (accountsOnDisk) {
            onDiskAccounts.remove(EntityNumVirtualKey.from(num));
        } else {
            inMemoryAccounts.remove(num);
        }
    }

    public long size() {
        return accountsOnDisk ? onDiskAccounts.size() : inMemoryAccounts.size();
    }

    public boolean containsKey(final EntityNum num) {
        return accountsOnDisk
                ? onDiskAccounts.containsKey(EntityNumVirtualKey.from(num))
                : inMemoryAccounts.containsKey(num);
    }

    public void archive() {
        if (!accountsOnDisk) {
            inMemoryAccounts.archive();
        }
    }

    public Hash getHash() {
        return accountsOnDisk ? onDiskAccounts.getHash() : inMemoryAccounts.getHash();
    }

    public Set<EntityNum> keySet() {
        if (accountsOnDisk) {
            return payerRecords.keySet();
        } else {
            return inMemoryAccounts.keySet();
        }
    }

    public void forEach(final BiConsumer<EntityNum, HederaAccount> visitor) {
        if (accountsOnDisk) {
            try {
                virtualMapDataAccess.extractVirtualMapData(
                        getStaticThreadManager(),
                        onDiskAccounts,
                        entry -> visitor.accept(entry.getKey().asEntityNum(), entry.getValue()),
                        THREAD_COUNT);
            } catch (final InterruptedException e) {
                log.error("Interrupted while extracting VM data", e);
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } else {
            inMemoryAccounts.forEach(visitor);
        }
    }

    public boolean areOnDisk() {
        return accountsOnDisk;
    }

    @Nullable
    public MerkleMap<EntityNum, MerkleAccount> getInMemoryAccounts() {
        return inMemoryAccounts;
    }

    @Nullable
    public VirtualMap<EntityNumVirtualKey, OnDiskAccount> getOnDiskAccounts() {
        return onDiskAccounts;
    }
}
