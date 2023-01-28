/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.Nullable;

public class TokenRelStorageAdapter {
    private final boolean relsOnDisk;

    private final @Nullable MerkleMap<EntityNumPair, MerkleTokenRelStatus> inMemoryRels;
    private final @Nullable VirtualMapLike<EntityNumVirtualKey, OnDiskTokenRel> onDiskRels;

    public static TokenRelStorageAdapter fromInMemory(
            final MerkleMap<EntityNumPair, MerkleTokenRelStatus> rels) {
        return new TokenRelStorageAdapter(rels, null);
    }

    public static TokenRelStorageAdapter fromOnDisk(
            final VirtualMapLike<EntityNumVirtualKey, OnDiskTokenRel> rels) {
        return new TokenRelStorageAdapter(null, rels);
    }

    private TokenRelStorageAdapter(
            @Nullable final MerkleMap<EntityNumPair, MerkleTokenRelStatus> inMemoryRels,
            @Nullable final VirtualMapLike<EntityNumVirtualKey, OnDiskTokenRel> onDiskRels) {
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
    public VirtualMapLike<EntityNumVirtualKey, OnDiskTokenRel> getOnDiskRels() {
        return onDiskRels;
    }
}
