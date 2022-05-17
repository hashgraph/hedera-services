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
		// Note: for any mutations that occur on the returned value, the caller is responsible for calling
		// backingNfts.put(id, token) to store the mutations. Otherwise, the mutations will be discarded.
		// VirtualMap's getForModify() cannot be used here as it can lead to race conditions.
		return new UniqueTokenValue(delegate.get().get(UniqueTokenKey.from(id)));
	}

	@Override
	public UniqueTokenValue getImmutableRef(NftId id) {
		return delegate.get().get(UniqueTokenKey.from(id));
	}

	@Override
	public void put(NftId id, UniqueTokenValue nft) {
		final var key = UniqueTokenKey.from(id);
		if (!delegate.get().containsKey(key)) {
			delegate.get().put(key, nft);
		}
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

	/* -- only for unit tests --*/
	public Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> getDelegate() {
		return delegate;
	}
}
