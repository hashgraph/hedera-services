package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TokenRelsCommitInterceptor implements CommitInterceptor<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
	private final SideEffectsTracker sideEffectsTracker;

	public TokenRelsCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
		this.sideEffectsTracker = sideEffectsTracker;
	}

	/**
	 * Accepts a pending change set, including creations and removals.
	 *
	 * @throws IllegalStateException if these changes are invalid
	 */
	@Override
	public void preview(final List<MerkleLeafChanges<Pair<AccountID, TokenID>, MerkleTokenRelStatus,
			TokenRelProperty>> changesToCommit) {
		final List<Long> balances = new ArrayList<>();
		for (final var changeToCommit : changesToCommit) {
			final var accountTokenPair = changeToCommit.id();
			final var tokenRelStatus = changeToCommit.merkleLeaf();
			final var changedProperties = changeToCommit.changes();

			trackTokenBalance(changedProperties, tokenRelStatus, accountTokenPair, balances);
		}

		doZeroSum(balances);
	}

	private void trackTokenBalance(final Map<TokenRelProperty, Object> changedProperties,
								   final MerkleTokenRelStatus tokenRelStatus,
								   final Pair<AccountID, TokenID> accountTokenPair,
								   final List<Long> balances) {
		if (tokenRelStatus != null && changedProperties.containsKey(TokenRelProperty.TOKEN_BALANCE) &&
				(long) changedProperties.get(TokenRelProperty.TOKEN_BALANCE) != tokenRelStatus.getBalance()) {
			final var tokenBalanceChange =
					(long) changedProperties.get(TokenRelProperty.TOKEN_BALANCE) - tokenRelStatus.getBalance();
			balances.add(tokenBalanceChange);
			sideEffectsTracker.trackTokenUnitsChange(accountTokenPair.getValue(), accountTokenPair.getKey(), tokenBalanceChange);
		}
	}

	private void doZeroSum(final List<Long> balances) {
		if (balances.size() > 1) {
			final var sum = getArrayLongSum(balances);

			if (sum != 0) {
				throw new IllegalStateException("Invalid balance changes!");
			}
		}
	}

	private long getArrayLongSum(final List<Long> balances) {
		int sum = 0;
		for (long value : balances) {
			sum += value;
		}
		return sum;
	}
}