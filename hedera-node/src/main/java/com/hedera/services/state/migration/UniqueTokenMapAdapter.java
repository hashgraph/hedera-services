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

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

public class UniqueTokenMapAdapter {

    private final MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap;
    private final VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap;
    private final boolean isVirtual;

    public static UniqueTokenMapAdapter wrap(
            VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap) {
        return new UniqueTokenMapAdapter(virtualMap);
    }

    public static UniqueTokenMapAdapter wrap(
            MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap) {
        return new UniqueTokenMapAdapter(merkleMap);
    }

    UniqueTokenMapAdapter(VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap) {
        isVirtual = true;
        this.virtualMap = virtualMap;
        this.merkleMap = null;
    }

    UniqueTokenMapAdapter(MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap) {
        isVirtual = false;
        this.merkleMap = merkleMap;
        this.virtualMap = null;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap() {
        return virtualMap;
    }

    public MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap() {
        return merkleMap;
    }

    public long size() {
        return isVirtual ? virtualMap.size() : merkleMap.size();
    }

    public boolean containsKey(NftId nftId) {
        return isVirtual
                ? virtualMap.containsKey(UniqueTokenKey.from(nftId))
                : merkleMap.containsKey(EntityNumPair.fromNftId(nftId));
    }

    public void put(NftId key, UniqueTokenAdapter value) {
        if (isVirtual) {
            virtualMap.put(UniqueTokenKey.from(key), value.uniqueTokenValue());
        } else {
            if (value.isVirtual()) {
                throw new UnsupportedOperationException(
                        "Trying to insert virtual nft in MerkleMap");
            }
            merkleMap.put(EntityNumPair.fromNftId(key), value.merkleUniqueToken());
        }
    }

    public UniqueTokenAdapter get(NftId key) {
        return isVirtual
                ? UniqueTokenAdapter.wrap(virtualMap.get(UniqueTokenKey.from(key)))
                : UniqueTokenAdapter.wrap(merkleMap.get(EntityNumPair.fromNftId(key)));
    }

    public UniqueTokenAdapter getForModify(NftId key) {
        return isVirtual
                ? UniqueTokenAdapter.wrap(virtualMap.getForModify(UniqueTokenKey.from(key)))
                : UniqueTokenAdapter.wrap(merkleMap.getForModify(EntityNumPair.fromNftId(key)));
    }

    public void remove(NftId key) {
        if (isVirtual) {
            virtualMap.remove(UniqueTokenKey.from(key));
        } else {
            merkleMap.remove(EntityNumPair.fromNftId(key));
        }
    }

    public void archive() {
        // VirtualMap does not need to be archived. We can safely ignore the call.
        if (!isVirtual) {
            merkleMap.archive();
        }
    }

    public Hash getHash() {
        return isVirtual ? virtualMap.getHash() : merkleMap.getHash();
    }
}
