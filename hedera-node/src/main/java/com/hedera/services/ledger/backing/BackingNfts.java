/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.backing;

import static com.hedera.services.utils.EntityNumPair.fromNftId;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BackingNfts implements BackingStore<NftId, UniqueTokenAdapter> {
    private final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> delegate;

    public BackingNfts(Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public UniqueTokenAdapter getRef(NftId id) {
        return UniqueTokenAdapter.wrap(delegate.get().getForModify(fromNftId(id)));
    }

    @Override
    public UniqueTokenAdapter getImmutableRef(NftId id) {
        return UniqueTokenAdapter.wrap(delegate.get().get(fromNftId(id)));
    }

    @Override
    public void put(NftId id, UniqueTokenAdapter nft) {
        if (!delegate.get().containsKey(EntityNumPair.fromNftId(id))) {
            if (!nft.isVirtual()) {
                delegate.get().put(fromNftId(id), nft.merkleUniqueToken());
            } else {
                throw new UnsupportedOperationException("Not implemented yet.");
            }
        }
    }

    @Override
    public void remove(NftId id) {
        delegate.get().remove(fromNftId(id));
    }

    @Override
    public boolean contains(NftId id) {
        return delegate.get().containsKey(EntityNumPair.fromNftId(id));
    }

    @Override
    public Set<NftId> idSet() {
        return delegate.get().keySet().stream()
                .map(EntityNumPair::asTokenNumAndSerialPair)
                .map(pair -> NftId.withDefaultShardRealm(pair.getLeft(), pair.getRight()))
                .collect(Collectors.toSet());
    }

    @Override
    public long size() {
        return delegate.get().size();
    }

    /* -- only for unit tests */
    public Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> getDelegate() {
        return delegate;
    }
}
