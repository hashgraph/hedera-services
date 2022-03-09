package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.List;
import java.util.Map;

/**
 * A {@link CommitInterceptor} implementation that tracks the hbar adjustments being committed,
 * and throws {@link IllegalStateException} if the adjustments do not sum to zero.
 *
 * In future work, this interceptor will be extended to capture <i>all</i> externalized side-effects
 * of a transaction on the {@code accounts} ledger, including (for example) approved allowances and
 * new aliases.
 */
public class AccountsCommitInterceptor implements CommitInterceptor<AccountID, MerkleAccount, AccountProperty> {
	private final SideEffectsTracker sideEffectsTracker;

	public AccountsCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
		this.sideEffectsTracker = sideEffectsTracker;
	}

	/**
	 * Accepts a pending change set, including creations. Removals are not supported.
	 *
	 * @throws IllegalStateException
	 * 		if these changes are invalid
	 */
	@Override
	public void preview(final List<EntityChanges<AccountID, MerkleAccount, AccountProperty>> changesToCommit) {
		for (final var changeToCommit : changesToCommit) {
			final var account = changeToCommit.id();
			final var merkleAccount = changeToCommit.entity();
			final var changedProperties = changeToCommit.changes();

			trackBalanceChangeIfAny(changedProperties, account, merkleAccount);
		}
		assertZeroSum();
	}

	private void trackBalanceChangeIfAny(
			final Map<AccountProperty, Object> changedProperties,
			final AccountID account,
			final MerkleAccount merkleAccount
	) {
		if (changedProperties.containsKey(AccountProperty.BALANCE)) {
			final long balancePropertyValue = (long) changedProperties.get(AccountProperty.BALANCE);
			final long balanceChange = merkleAccount != null ?
					balancePropertyValue - merkleAccount.getBalance() : balancePropertyValue;

			sideEffectsTracker.trackHbarChange(account, balanceChange);
		}
	}

	private void assertZeroSum() {
		if (sideEffectsTracker.getNetHbarChange() != 0) {
			throw new IllegalStateException("Invalid balance changes!");
		}
	}
}