package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapBackingNfts implements BackingStore<NftId, MerkleUniqueToken> {
	private Map<NftId, MerkleUniqueToken> nfts = new HashMap<>();

	@Override
	public MerkleUniqueToken getRef(NftId id) {
		return nfts.get(id);
	}

	@Override
	public MerkleUniqueToken getImmutableRef(NftId id) {
		return nfts.get(id);
	}

	@Override
	public void put(NftId id, MerkleUniqueToken nft) {
		nfts.put(id, nft);
	}

	@Override
	public void remove(NftId id) {
		nfts.remove(id);
	}

	@Override
	public boolean contains(NftId id) {
		return nfts.containsKey(id);
	}

	@Override
	public Set<NftId> idSet() {
		return nfts.keySet();
	}
}
