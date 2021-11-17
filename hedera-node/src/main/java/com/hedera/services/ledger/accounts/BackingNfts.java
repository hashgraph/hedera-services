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
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNumPair.fromNftId;

@Singleton
public class BackingNfts implements BackingStore<NftId, MerkleUniqueToken> {
	Set<NftId> existingNfts = new HashSet<>();

	private final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> delegate;

	@Inject
	public BackingNfts(Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void rebuildFromSources() {
		existingNfts.clear();
		for (EntityNumPair entity : delegate.get().keySet()) {
			var pair = entity.asTokenNumAndSerialPair();
			existingNfts.add(new NftId(pair.getLeft(), pair.getRight()));
		}
	}

	@Override
	public MerkleUniqueToken getRef(NftId id) {
		return delegate.get().getForModify(fromNftId(id));
	}

	@Override
	public MerkleUniqueToken getImmutableRef(NftId id) {
		return delegate.get().get(fromNftId(id));
	}

	@Override
	public void put(NftId id, MerkleUniqueToken nft) {
		if (!existingNfts.contains(id)) {
			delegate.get().put(fromNftId(id), nft);
			existingNfts.add(id);
		}
	}

	@Override
	public void remove(NftId id) {
		existingNfts.remove(id);
		delegate.get().remove(fromNftId(id));
	}

	@Override
	public boolean contains(NftId id) {
		return existingNfts.contains(id);
	}

	@Override
	public Set<NftId> idSet() {
		return existingNfts;
	}
}
