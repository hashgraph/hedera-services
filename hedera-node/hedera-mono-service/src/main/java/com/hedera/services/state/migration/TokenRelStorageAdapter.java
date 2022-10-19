package com.hedera.services.state.migration;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.state.virtual.entities.OnDiskTokenRel;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

import javax.annotation.Nullable;
import java.util.Set;

public class TokenRelStorageAdapter {
    private final boolean relsOnDisk;

    private final @Nullable MerkleMap<EntityNumPair, MerkleTokenRelStatus> inMemoryRels;
    private final @Nullable VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> onDiskRels;

    public static TokenRelStorageAdapter fromInMemory(
            final MerkleMap<EntityNumPair, MerkleTokenRelStatus> rels) {
        return new TokenRelStorageAdapter(rels, null);
    }

    public static TokenRelStorageAdapter fromOnDisk(
            final VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> rels) {
        return new TokenRelStorageAdapter(null, rels);
    }

    private TokenRelStorageAdapter(
            @Nullable final MerkleMap<EntityNumPair, MerkleTokenRelStatus> inMemoryRels,
            @Nullable final VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> onDiskRels) {
        if (inMemoryRels != null) {
            this.relsOnDisk = false;
            this.inMemoryRels = inMemoryRels;
            this.onDiskRels = null;
        } else {
            this.relsOnDisk = true;
            this.inMemoryRels = null;
            this.onDiskRels = onDiskRels;
        }
    }

    public HederaTokenRel get(final EntityNumPair num) {
        return relsOnDisk
                ? onDiskRels.get(EntityNumVirtualKey.fromPair(num))
                : inMemoryRels.get(num);
    }

    public HederaTokenRel getForModify(final EntityNumPair num) {
        return relsOnDisk
                ? onDiskRels.getForModify(EntityNumVirtualKey.fromPair(num))
                : inMemoryRels.getForModify(num);
    }

    public void put(final EntityNumPair num, final HederaTokenRel wrapper) {
        if (relsOnDisk) {
            wrapper.setKey(num);
            onDiskRels.put(EntityNumVirtualKey.fromPair(num), (OnDiskTokenRel) wrapper);
        } else {
            inMemoryRels.put(num, (MerkleTokenRelStatus) wrapper);
        }
    }

    public void remove(final EntityNumPair num) {
        if (relsOnDisk) {
            onDiskRels.remove(EntityNumVirtualKey.fromPair(num));
        } else {
            inMemoryRels.remove(num);
        }
    }

    public long size() {
        return relsOnDisk ? onDiskRels.size() : inMemoryRels.size();
    }

    public boolean containsKey(final EntityNumPair num) {
        return relsOnDisk
                ? onDiskRels.containsKey(EntityNumVirtualKey.fromPair(num))
                : inMemoryRels.containsKey(num);
    }

    public void archive() {
        if (!relsOnDisk) {
            inMemoryRels.archive();
        }
    }

    public Hash getHash() {
        return relsOnDisk ? onDiskRels.getHash() : inMemoryRels.getHash();
    }

    public boolean areOnDisk() {
        return relsOnDisk;
    }

    @Nullable
    public MerkleMap<EntityNumPair, MerkleTokenRelStatus> getInMemoryRels() {
        return inMemoryRels;
    }

    @Nullable
    public VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> getOnDiskRels() {
        return onDiskRels;
    }
}
