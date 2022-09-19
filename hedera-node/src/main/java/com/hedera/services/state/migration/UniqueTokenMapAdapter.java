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
import javax.annotation.Nullable;

/**
 * Adaptor for NFT store that allows dropping in a MerkleMap/VirtualMap instances in places where
 * the NFTs are read and written from their respective data store.
 */
public class UniqueTokenMapAdapter {

    /** Pointer to the underlying MerkleMap to interface with. null if unavailable. */
    @Nullable private final MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap;

    /** Pointer to the underlying VirtualMap to interface with. null if unavailable. */
    @Nullable private final VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap;

    /** True if {@link #virtualMap} is set. False if {@link #merkleMap} is set. */
    private final boolean isVirtual;

    /**
     * Construct a UniqueTokenMapAdapter given a VirtualMap instance.
     *
     * @param virtualMap the VirtualMap instance to interface
     * @return newly constructed adapter making use of the provided virtual map.
     */
    public static UniqueTokenMapAdapter wrap(
            final VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap) {
        return new UniqueTokenMapAdapter(virtualMap);
    }

    /**
     * Construct a UniqueTokenMapAdapter given a MerkleMap instance.
     *
     * @param merkleMap the MerkleMap instance to interface
     * @return newly constructed adapter making use of the provided merkle map.
     */
    public static UniqueTokenMapAdapter wrap(
            final MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap) {
        return new UniqueTokenMapAdapter(merkleMap);
    }

    UniqueTokenMapAdapter(final VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap) {
        isVirtual = true;
        this.virtualMap = virtualMap;
        this.merkleMap = null;
    }

    UniqueTokenMapAdapter(final MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap) {
        isVirtual = false;
        this.merkleMap = merkleMap;
        this.virtualMap = null;
    }

    /**
     * @return true if the adapter makes use of a virtual map instance.
     */
    public boolean isVirtual() {
        return isVirtual;
    }

    /**
     * @return the virtual map instance that the adapter is connecting to.
     */
    public VirtualMap<UniqueTokenKey, UniqueTokenValue> virtualMap() {
        return virtualMap;
    }

    /**
     * @return the merkle map instance that the adapter is connecting to.
     */
    public MerkleMap<EntityNumPair, MerkleUniqueToken> merkleMap() {
        return merkleMap;
    }

    /**
     * @return the number of entries the map is holding.
     */
    public long size() {
        return isVirtual ? virtualMap.size() : merkleMap.size();
    }

    /**
     * Check if the map contains an entry for the given key.
     *
     * @param nftId the key to lookup
     * @return true if the underlying map has a value associated with the provided key.
     */
    public boolean containsKey(final NftId nftId) {
        return isVirtual
                ? virtualMap.containsKey(UniqueTokenKey.from(nftId))
                : merkleMap.containsKey(EntityNumPair.fromNftId(nftId));
    }

    /**
     * Put or update the value into the map under the given key.
     *
     * @param key the key in the map in which to insert/replace the value of.
     * @param value the new value to insert/replace in the map.
     * @throws UnsupportedOperationException if one attempts to insert a virtual value for a
     *     non-virtual key or vice versa
     */
    public void put(final NftId key, final UniqueTokenAdapter value) {
        if (isVirtual != value.isVirtual()) {
            throw new UnsupportedOperationException(
                    isVirtual
                            ? "Trying to insert non-virtual nft in VirtualMap"
                            : "Trying to insert a virtual nft in MerkleMap");
        }

        if (isVirtual) {
            virtualMap.put(UniqueTokenKey.from(key), value.uniqueTokenValue());
        } else {
            merkleMap.put(EntityNumPair.fromNftId(key), value.merkleUniqueToken());
        }
    }

    /**
     * Retrieve a new copy of the value of the map with the given key.
     *
     * @param key of the value to retrieve.
     * @return the value associated with the key.
     */
    public UniqueTokenAdapter get(final NftId key) {
        return isVirtual
                ? UniqueTokenAdapter.wrap(virtualMap.get(UniqueTokenKey.from(key)))
                : UniqueTokenAdapter.wrap(merkleMap.get(EntityNumPair.fromNftId(key)));
    }

    /**
     * Retrieve a reference to the value of the map with the given key.
     *
     * @param key of the value to retrieve.
     * @return the value associated with the key. This value can be mutated and its updated contents
     *     will be updated in the map.
     */
    public UniqueTokenAdapter getForModify(final NftId key) {
        return isVirtual
                ? UniqueTokenAdapter.wrap(virtualMap.getForModify(UniqueTokenKey.from(key)))
                : UniqueTokenAdapter.wrap(merkleMap.getForModify(EntityNumPair.fromNftId(key)));
    }

    /**
     * Remove the entry in the map associated with the provided key.
     *
     * @param key of the value to remove from the map.
     */
    public void remove(final NftId key) {
        if (isVirtual) {
            virtualMap.remove(UniqueTokenKey.from(key));
        } else {
            merkleMap.remove(EntityNumPair.fromNftId(key));
        }
    }

    /** Archive a copy of the map. This is a no-op for VirtualMap. */
    public void archive() {
        // VirtualMap does not need to be archived. We can safely ignore the call.
        if (!isVirtual) {
            merkleMap.archive();
        }
    }

    /**
     * @return the computed hash of the map's entries.
     */
    public Hash getHash() {
        return isVirtual ? virtualMap.getHash() : merkleMap.getHash();
    }
}
