package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class BackingNftOwners implements BackingStore<NftId, MerkleUniqueToken> {
	Set<Pair<AccountID, TokenID>> existingRels = new HashSet<>();
	private final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> delegate;

	public BackingNftOwners(Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> delegate) {
		this.delegate = delegate;
	}

	@Override
	public MerkleUniqueToken getRef(NftId id) {
		return null;
	}

	@Override
	public MerkleUniqueToken getImmutableRef(NftId id) {
		return null;
	}

	@Override
	public void put(NftId id, MerkleUniqueToken account) {

	}

	@Override
	public void remove(NftId id) {

	}

	@Override
	public boolean contains(NftId id) {
		return false;
	}

	@Override
	public Set<NftId> idSet() {
		return null;
	}
}
