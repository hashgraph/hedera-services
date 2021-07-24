package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;

import java.util.List;

public class ExplicitOwnersUniqTokenView implements UniqTokenView {
	@Override
	public List<MerkleUniqueToken> ownedAssociations(EntityId owner, int start, int end) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public List<MerkleUniqueToken> typedAssociations(EntityId type, int start, int end) {
		throw new AssertionError("Not implemented!");
	}
}
