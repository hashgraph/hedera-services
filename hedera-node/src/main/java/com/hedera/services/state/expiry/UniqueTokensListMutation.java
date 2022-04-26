package com.hedera.services.state.expiry;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MapValueListMutation;
import com.swirlds.merkle.map.MerkleMap;
import org.jetbrains.annotations.Nullable;

import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;

public class UniqueTokensListMutation implements MapValueListMutation<EntityNumPair, MerkleUniqueToken> {

	final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens;

	public UniqueTokensListMutation(
			final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens
	) {
		this.uniqueTokens = uniqueTokens;
	}

	@Nullable
	@Override
	public MerkleUniqueToken get(final EntityNumPair key) {
		return uniqueTokens.get(key);
	}

	@Nullable
	@Override
	public MerkleUniqueToken getForModify(final EntityNumPair key) {
		return uniqueTokens.getForModify(key);
	}

	@Override
	public void put(final EntityNumPair key, final MerkleUniqueToken value) {
		uniqueTokens.put(key, value);
	}

	@Override
	public void remove(final EntityNumPair key) {
		uniqueTokens.remove(key);
	}

	@Override
	public void markAsHead(final MerkleUniqueToken node) {
		node.setPrev(MISSING_NFT_NUM_PAIR);
		// TODO : also update pointers on the account -- head nft id and head serial num ??
	}

	@Override
	public void markAsTail(final MerkleUniqueToken node) {
		node.setNext(MISSING_NFT_NUM_PAIR);
	}

	@Override
	public void updatePrev(final MerkleUniqueToken node, final EntityNumPair prev) {
		node.setPrev(prev.asNftNumPair());
	}

	@Override
	public void updateNext(final MerkleUniqueToken node, final EntityNumPair next) {
		node.setNext(next.asNftNumPair());
	}

	@Nullable
	@Override
	public EntityNumPair next(final MerkleUniqueToken node) {
		final var nextKey = node.getNext();
		return nextKey == MISSING_NFT_NUM_PAIR ? null : nextKey.asEntityNumPair();
	}

	@Nullable
	@Override
	public EntityNumPair prev(final MerkleUniqueToken node) {
		final var prevKey = node.getPrev();
		return prevKey == MISSING_NFT_NUM_PAIR ? null : prevKey.asEntityNumPair();
	}
}
