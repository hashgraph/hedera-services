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

import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.migration.UniqueTokenMapAdapter;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNumPair;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BackingNfts implements BackingStore<NftId, UniqueTokenAdapter> {
    private static final Logger LOG = LogManager.getLogger(BackingNfts.class);
    private final Supplier<UniqueTokenMapAdapter> delegate;

    public BackingNfts(final Supplier<UniqueTokenMapAdapter> delegate) {
        this.delegate = delegate;
    }

    @Override
    public UniqueTokenAdapter getRef(final NftId id) {
        return delegate.get().getForModify(id);
    }

    @Override
    public UniqueTokenAdapter getImmutableRef(final NftId id) {
        return delegate.get().get(id);
    }

    @Override
    public void put(final NftId id, final UniqueTokenAdapter nft) {
        if (!delegate.get().containsKey(id)) {
            delegate.get().put(id, nft);
        }
    }

    @Override
    public void remove(final NftId id) {
        delegate.get().remove(id);
    }

    @Override
    public boolean contains(final NftId id) {
        return delegate.get().containsKey(id);
    }

    @Override
    public Set<NftId> idSet() {
        if (delegate.get().isVirtual()) {
            throw new UnsupportedOperationException();
        }
        LOG.warn("idSet() called for BackingNfts. This is a slow operation.");
        return delegate.get().merkleMap().keySet().stream()
                .map(EntityNumPair::asTokenNumAndSerialPair)
                .map(pair -> NftId.withDefaultShardRealm(pair.getLeft(), pair.getRight()))
                .collect(Collectors.toSet());
    }

    @Override
    public long size() {
        return delegate.get().size();
    }

    /* -- only for unit tests */
    public Supplier<UniqueTokenMapAdapter> getDelegate() {
        return delegate;
    }
}
