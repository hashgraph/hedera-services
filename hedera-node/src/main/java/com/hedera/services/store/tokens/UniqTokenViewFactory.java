package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

public class UniqTokenViewFactory {
	private final boolean shouldUseTreasuryWildcards;

	public UniqTokenViewFactory(boolean shouldUseTreasuryWildcards) {
		this.shouldUseTreasuryWildcards = shouldUseTreasuryWildcards;
	}

	public UniqTokenView viewFor(
			TokenStore tokenStore,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> treasuryNftsByType
	) {
		if (shouldUseTreasuryWildcards) {
			return new TreasuryWildcardsUniqTokenView(
					tokenStore, tokens, nfts, nftsByType, nftsByOwner, treasuryNftsByType);
		} else {
			return new ExplicitOwnersUniqTokenView(tokens, nfts, nftsByType, nftsByOwner);
		}
	}
}
