package com.hedera.services.ledger.interceptors;

import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.utils.MapValueListUtils.inPlaceInsertAtMapValueListHead;
import static com.hedera.services.utils.MapValueListUtils.removeFromMapValueList;

@Singleton
public class TokenRelsLinkManager {
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;

	@Inject
	public TokenRelsLinkManager(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels
	) {
		this.accounts = accounts;
		this.tokenRels = tokenRels;
	}

	/**
	 * Updates the linked list in the {@code tokenRels} map for the given account, including
	 * its head token number in the {@code accounts} map if needed.
	 *
	 * <b>IMPORTANT:</b> each new {@link MerkleTokenRelStatus} must have its {@code numbers} field set; that is,
	 * {@link MerkleTokenRelStatus#getRelatedTokenNum()} cannot return zero! This contract is respected by the sole
	 * client of this class, the {@link LinkAwareTokenRelsCommitInterceptor}.
	 *
	 * @param accountNum
	 * @param dissociatedTokenNums
	 * @param newTokenRels
	 */
	void updateLinks(
			final EntityNum accountNum,
			@Nullable final List<EntityNum> dissociatedTokenNums,
			@Nullable final List<MerkleTokenRelStatus> newTokenRels
	) {
		final var literalNum = accountNum.longValue();
		final var curTokenRels = tokenRels.get();
		final var listMutation = new TokenRelsListMutation(literalNum, curTokenRels);

		final var curAccounts = accounts.get();
		final var mutableAccount = curAccounts.getForModify(accountNum);
		var rootKey = rootKeyOf(mutableAccount);
		if (rootKey != null && dissociatedTokenNums != null) {
			for (final var tokenNum : dissociatedTokenNums) {
				final var tbdKey = EntityNumPair.fromNums(accountNum, tokenNum);
				rootKey = removeFromMapValueList(tbdKey, rootKey, listMutation);
			}
		}
		if (newTokenRels != null) {
			MerkleTokenRelStatus rootRel = null;
			for (final var newRel : newTokenRels) {
				final var literalTokenNum = newRel.getRelatedTokenNum();
				final var newKey = EntityNumPair.fromLongs(literalNum, literalTokenNum);
				rootKey = inPlaceInsertAtMapValueListHead(newKey, newRel, rootKey, rootRel, listMutation);
				rootRel = newRel;
			}
		}
		final var newHeadTokenId = (rootKey == null) ? 0 : rootKey.getLowOrderAsLong();
		mutableAccount.setHeadTokenId(newHeadTokenId);
	}

	@Nullable
	private EntityNumPair rootKeyOf(final MerkleAccount account) {
		final var headNum = account.getHeadTokenId();
		return headNum == 0 ? null : EntityNumPair.fromLongs(account.getKey().longValue(), headNum);
	}
}
