package com.hedera.services.state.initialization;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

public class ViewBuilder {
	public static void rebuildUniqueTokenViews(
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens,
			FCOneToManyRelation<EntityId, MerkleUniqueTokenId> uniqueTokenAssociations,
			FCOneToManyRelation<EntityId, MerkleUniqueTokenId> uniqueOwnershipAssociations
	) {
		uniqueTokens.forEach((id, uniq) -> {
			uniqueTokenAssociations.associate(id.tokenId(), id);
			uniqueOwnershipAssociations.associate(uniq.getOwner(), id);
		});
	}
}
