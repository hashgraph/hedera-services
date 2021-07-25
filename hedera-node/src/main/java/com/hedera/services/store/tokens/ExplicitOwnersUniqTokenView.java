package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.function.Supplier;

public class ExplicitOwnersUniqTokenView extends AbstractUniqTokenView {
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner;

	public ExplicitOwnersUniqTokenView(
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner
	) {
		super(tokens, nfts, nftsByType);

		this.nftsByOwner = nftsByOwner;
	}

	@Override
	public List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end) {
		final var accountId = EntityId.fromGrpcAccountId(owner);
		return accumulatedInfo(nftsByOwner.get(), accountId, (int) start, (int) end, null, owner);
	}
}
