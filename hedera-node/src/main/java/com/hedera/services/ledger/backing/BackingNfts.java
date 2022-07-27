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

import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Set;
import java.util.function.Supplier;

public class BackingNfts implements BackingStore<NftId, UniqueTokenValue> {
    private final Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> delegate;

    public BackingNfts(Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public UniqueTokenValue getRef(NftId id) {
        return delegate.get().getForModify(UniqueTokenKey.from(id));
    }

    @Override
    public UniqueTokenValue getImmutableRef(NftId id) {
        return delegate.get().get(UniqueTokenKey.from(id));
    }

    @Override
    public void put(NftId id, UniqueTokenValue nft) {
        final var key = UniqueTokenKey.from(id);
        delegate.get().put(key, nft);
    }

    @Override
    public void remove(NftId id) {
        delegate.get().remove(UniqueTokenKey.from(id));
    }

    @Override
    public boolean contains(NftId id) {
        return delegate.get().containsKey(UniqueTokenKey.from(id));
    }

    @Override
    public Set<NftId> idSet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long size() {
        return delegate.get().size();
    }

    /* -- only for unit tests */
    public Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> getDelegate() {
        return delegate;
    }
}
