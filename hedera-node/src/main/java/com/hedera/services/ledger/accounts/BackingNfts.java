package com.hedera.services.ledger.accounts;

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

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.swirlds.fcmap.FCMap;

import java.util.Set;
import java.util.function.Supplier;

public class BackingNfts implements BackingStore<NftId, MerkleUniqueToken> {
	private final Supplier<FCMap<NftId, MerkleUniqueToken>> delegate;

	public BackingNfts(Supplier<FCMap<NftId, MerkleUniqueToken>> delegate) {
		this.delegate = delegate;
		rebuildFromSources();
	}

	@Override
	public void rebuildFromSources() {
		/* No-op */
	}

	@Override
	public MerkleUniqueToken getRef(NftId id) {
		return delegate.get().getForModify(id);
	}

	@Override
	public MerkleUniqueToken getImmutableRef(NftId id) {
		return delegate.get().get(id);
	}

	@Override
	public void put(NftId id, MerkleUniqueToken nft) {
		final var currentNfts = delegate.get();
		if (!currentNfts.containsKey(id)) {
			currentNfts.put(id, nft);
		}
	}

	@Override
	public void remove(NftId id) {
		delegate.get().remove(id);
	}

	@Override
	public boolean contains(NftId id) {
		return delegate.get().containsKey(id);
	}

	@Override
	public Set<NftId> idSet() {
		throw new UnsupportedOperationException();
	}
}
