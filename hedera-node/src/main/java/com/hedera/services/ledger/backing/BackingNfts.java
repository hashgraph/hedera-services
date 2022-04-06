package com.hedera.services.ledger.backing;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.swirlds.virtualmap.VirtualMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.function.Supplier;

@Singleton
@SuppressWarnings("java:S6206")   // Unable to convert to record due to https://github.com/google/dagger/issues/2106
public class BackingNfts implements BackingStore<NftId, UniqueTokenValue> {
	private final Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> delegate;

	@Inject
	public BackingNfts(Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void rebuildFromSources() {
		/* No-op */
	}

	@Override
	public UniqueTokenValue getRef(NftId id) {
		return delegate.get().getForModify(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public UniqueTokenValue getImmutableRef(NftId id) {
		return delegate.get().get(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public void put(NftId id, UniqueTokenValue nft) {
		UniqueTokenKey key = UniqueTokenKey.fromNftId(id);
		// TODO: remove comments below if works
        //		if (!delegate.get().containsKey(key) || delegate.get().get(key)) {
		delegate.get().put(key, nft);
        //		}
	}

	@Override
	public void remove(NftId id) {
		delegate.get().remove(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public boolean contains(NftId id) {
		return delegate.get().containsKey(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public Set<NftId> idSet() {
		throw new UnsupportedOperationException("Virtual merkle operation unsupported");
	}

	@Override
	public long size() {
		return delegate.get().size();
	}

	/* -- only for unit tests --*/
	public Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> getDelegate() {
		return delegate;
	}
}
