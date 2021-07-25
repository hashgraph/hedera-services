package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.utils.MultiSourceRange;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.function.Supplier;

public class TreasuryWildcardsUniqTokenView extends AbstractUniqTokenView {
	private final TokenStore tokenStore;
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
		super(tokens, nfts, nftsByType);

		this.nftsByOwner = nftsByOwner;
		this.treasuryNftsByType = treasuryNftsByType;

		this.tokenStore = tokenStore;
	}

	@Override
	public List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end) {
		final var accountId = EntityId.fromGrpcAccountId(owner);
		final var curNftsByOwner = nftsByOwner.get();
		final var multiSourceRange = new MultiSourceRange((int) start, (int) end, curNftsByOwner.getCount(accountId));

		final var range = multiSourceRange.rangeForCurrentSource();
		final var answer = accumulatedInfo(nftsByOwner.get(), accountId, range[0], range[1], null, owner);
		if (!multiSourceRange.isRequestedRangeExhausted()) {
			tryToCompleteWithTreasuryOwned(owner, accountId, multiSourceRange, answer);
		}
		return answer;
	}

	private void tryToCompleteWithTreasuryOwned(
			AccountID owner,
			EntityId accountId,
			MultiSourceRange multiSourceRange,
			List<TokenNftInfo> answer
	) {
		final var curTreasuryNftsByType = treasuryNftsByType.get();
		final var allServed = tokenStore.listOfTokensServed(owner);
		for (var served : allServed) {
			final var tokenId = EntityId.fromGrpcTokenId(served);
			multiSourceRange.moveToNewSource(curTreasuryNftsByType.getCount(tokenId));
			final var range = multiSourceRange.rangeForCurrentSource();
			final var infoHere = accumulatedInfo(curTreasuryNftsByType, tokenId, range[0], range[1], served, owner);
			answer.addAll(infoHere);
			if (multiSourceRange.isRequestedRangeExhausted()) {
				break;
			}
		}
	}
}
