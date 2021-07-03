package com.hedera.services.state.initialization;

import com.hedera.services.state.merkle.MerkleBatchedUniqTokens;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

public class ViewBuilder {
	public static void rebuildUniqueTokenViews(
			FCMap<MerkleUniqueTokenId, MerkleBatchedUniqTokens> uniqueTokens,
			FCOneToManyRelation<EntityId, MerkleUniqueTokenId> uniqueTokenAssociations,
			FCOneToManyRelation<EntityId, MerkleUniqueTokenId> uniqueOwnershipAssociations
	) {
		uniqueTokens.forEach((id, uniq) -> {
			final var tokenId = id.tokenId();
			final var numHere = uniq.getMintedSoFar();
			for (int i = 0; i < numHere; i++) {
				if (!uniq.isBurned(i)) {
					final var adjId = new MerkleUniqueTokenId(id.tokenId(), id.serialNumber() + i);
					uniqueTokenAssociations.associate(tokenId, adjId);
					uniqueOwnershipAssociations.associate(uniq.getOwner(i), adjId);
				}
			}
		});
	}
}
