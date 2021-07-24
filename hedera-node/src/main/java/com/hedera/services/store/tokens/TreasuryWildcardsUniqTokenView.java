package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.function.Supplier;

public class TreasuryWildcardsUniqTokenView implements UniqTokenView {
	private final TokenStore tokenStore;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	private final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts;
	private final Supplier<FCOneToManyRelation<EntityId,MerkleUniqueTokenId>> nftsByType;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> treasuryNftsByType;

	public TreasuryWildcardsUniqTokenView(
			TokenStore tokenStore,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> treasuryNftsByType
	) {
		this.nfts = nfts;
		this.tokens = tokens;
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
		this.treasuryNftsByType = treasuryNftsByType;

		this.tokenStore = tokenStore;
	}

	@Override
	public List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public List<TokenNftInfo> typedAssociations(TokenID type, long start, long end) {
		throw new AssertionError("Not implemented!");
	}
}
