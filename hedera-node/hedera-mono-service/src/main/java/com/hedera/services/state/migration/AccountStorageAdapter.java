package com.hedera.services.state.migration;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.swirlds.virtualmap.VirtualMapMigration.extractVirtualMapData;

public class AccountStorageAdapter {
    private static final int THREAD_COUNT = 32;
    private final boolean accountsOnDisk;
    private final @Nullable MerkleMap<EntityNum, MerkleAccount> inMemoryAccounts;
    private final @Nullable VirtualMap<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts;

    public static AccountStorageAdapter fromInMemory(
            final MerkleMap<EntityNum, MerkleAccount> accounts) {
        return new AccountStorageAdapter(accounts, null);
    }

    public static AccountStorageAdapter fromOnDisk(
            final VirtualMap<EntityNumVirtualKey, OnDiskAccount> accounts) {
        return new AccountStorageAdapter(null, accounts);
    }

    private AccountStorageAdapter(
            @Nullable final MerkleMap<EntityNum, MerkleAccount> inMemoryAccounts,
            @Nullable final VirtualMap<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts) {
        if (inMemoryAccounts != null) {
            this.accountsOnDisk = false;
            this.inMemoryAccounts = inMemoryAccounts;
            this.onDiskAccounts = null;
        } else {
            this.accountsOnDisk = true;
            this.inMemoryAccounts = null;
            this.onDiskAccounts = onDiskAccounts;
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
            throw new UnsupportedOperationException();
        } else {
            return inMemoryAccounts.keySet();
        }
    }

    public void forEach(final BiConsumer<EntityNum, HederaAccount> visitor) {
        if (accountsOnDisk) {
            try {
                extractVirtualMapData(onDiskAccounts,
                        entry -> visitor.accept(entry.getKey().asEntityNum(), entry.getValue()), THREAD_COUNT);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        } else {
            inMemoryAccounts.forEach(visitor);
        }
    }
}
