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
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.store.models.NftId;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleUniqueTokenId.fromNftId;

public class BackingNfts implements BackingStore<NftId, MerkleUniqueToken> {
	static final Logger log = LogManager.getLogger(BackingNfts.class);

	private final Set<NftId> existing = new HashSet<>();

	private final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> delegate;

	public BackingNfts(Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> delegate) {
		this.delegate = delegate;
		rebuildFromSources();
	}

	@Override
	public void rebuildFromSources() {
		existing.clear();
		delegate.get().keySet().stream()
				.map(MerkleUniqueTokenId::asNftId)
				.forEach(existing::add);
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
		if (!existing.contains(id)) {
			delegate.get().put(fromNftId(id), nft);
			existing.add(id);
		}
	}

	@Override
	public void remove(NftId id) {
		existing.remove(id);
		delegate.get().remove(fromNftId(id));
	}

	@Override
	public boolean contains(NftId id) {
		return existing.contains(id);
	}

	@Override
	public Set<NftId> idSet() {
		return existing;
	}

	public void addToExistingNfts(NftId nftId)	{
		existing.add(nftId);
	}

	public void removeFromExistingNfts(NftId nftId) {
		existing.remove(nftId);
	}
}
