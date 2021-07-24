package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;

import java.util.List;

public interface UniqTokenView {
	List<MerkleUniqueToken> ownedAssociations(EntityId owner, int start, int end);
	List<MerkleUniqueToken> typedAssociations(EntityId type, int start, int end);
}
