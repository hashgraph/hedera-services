package com.hedera.services.state.expiry;

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MapValueListRemoval;
import com.swirlds.merkle.map.MerkleMap;
import org.jetbrains.annotations.Nullable;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;

public class TokenRelsListRemoval implements MapValueListRemoval<EntityNumPair, MerkleTokenRelStatus> {
	final long accountNum;
	final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;

	public TokenRelsListRemoval(
			final long accountNum,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels
	) {
		this.tokenRels = tokenRels;
		this.accountNum = accountNum;
	}

	/**
	 * {@inheritDoc}
	 */
	@Nullable
	@Override
	public MerkleTokenRelStatus get(final EntityNumPair key) {
		return tokenRels.get(key);
	}

	/**
	 * {@inheritDoc}
	 */
	@Nullable
	@Override
	public MerkleTokenRelStatus getForModify(final EntityNumPair key) {
		return tokenRels.getForModify(key);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(final EntityNumPair key) {
		tokenRels.remove(key);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void markAsHead(final MerkleTokenRelStatus node) {
		node.setPrev(MISSING_NUM.longValue());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void markAsTail(final MerkleTokenRelStatus node) {
		node.setNext(MISSING_NUM.longValue());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updatePrev(final MerkleTokenRelStatus node, final EntityNumPair prevKey) {
		node.setPrev(prevKey.getLowOrderAsLong());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateNext(final MerkleTokenRelStatus node, final EntityNumPair nextKey) {
		node.setNext(nextKey.getLowOrderAsLong());
	}

	/**
	 * {@inheritDoc}
	 */
	@Nullable
	@Override
	public EntityNumPair prev(final MerkleTokenRelStatus node) {
		return EntityNumPair.fromLongs(accountNum, node.prevKey());
	}

	/**
	 * {@inheritDoc}
	 */
	@Nullable
	@Override
	public EntityNumPair next(final MerkleTokenRelStatus node) {
		return EntityNumPair.fromLongs(accountNum, node.nextKey());
	}
}
