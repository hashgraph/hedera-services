package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.List;
import java.util.Map;

public class AccountsCommitInterceptor implements
		CommitInterceptor<AccountID, MerkleAccount, AccountProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
	private SideEffectsTracker sideEffectsTracker;

	public AccountsCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
		this.sideEffectsTracker = sideEffectsTracker;
	}

	/**
	 * Accepts a pending change set, including creations and removals.
	 *
	 * @throws IllegalStateException if these changes are invalid
	 */
	@Override
	public void preview(final List<MerkleLeafChanges<AccountID, MerkleAccount, AccountProperty>> changesToCommit) {
		for (final var changeToCommit : changesToCommit) {
			final var account = changeToCommit.id();
			final var merkleAccount = changeToCommit.merkleLeaf();
			final var changedProperties = changeToCommit.changes();

			trackHBarTransfer(changedProperties, account, merkleAccount);
		}

		if (changesToCommit.size() > 1) {
			checkSum();
		}
	}

	private void trackHBarTransfer(final Map<AccountProperty, Object> changedProperties,
								   final AccountID account, final MerkleAccount merkleAccount) {

		if (changedProperties.containsKey(AccountProperty.BALANCE)) {
			final long balancePropertyValue = (long) changedProperties.get(AccountProperty.BALANCE);
			final long balanceChange = merkleAccount != null ?
					balancePropertyValue - merkleAccount.getBalance() : balancePropertyValue;

			sideEffectsTracker.trackHbarChange(account, balanceChange);
		}
	}

	private void checkSum() {
		if (sideEffectsTracker.getNetHbarChange() != 0) {
			throw new IllegalStateException("Invalid balance changes!");
		}
	}
}