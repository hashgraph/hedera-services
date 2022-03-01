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
	
	public void setSideEffectsTracker(final SideEffectsTracker sideEffectsTracker) {
		this.sideEffectsTracker = sideEffectsTracker;
	}

	/**
	 * Accepts a pending change set, including creations and removals.
	 *
	 * @throws IllegalStateException if these changes are invalid
	 */
	@Override
	public void preview(final List<MerkleLeafChanges<AccountID, MerkleAccount, AccountProperty>> changesToCommit) {
		long sum = 0;

		sideEffectsTracker.clearNetHbarChanges();
		for (final var changeToCommit : changesToCommit) {
			final var account = changeToCommit.id();
			final var merkleAccount = changeToCommit.merkleLeaf();
			final var changedProperties = changeToCommit.changes();

			sum += trackHBarTransfer(changedProperties, account, merkleAccount);
		}

		if (changesToCommit.size() > 1) {
			checkSum(sum);
		}
	}

	private long trackHBarTransfer(final Map<AccountProperty, Object> changedProperties,
								   final AccountID account, final MerkleAccount merkleAccount) {

		if (changedProperties.containsKey(AccountProperty.BALANCE)) {
			final long balancePropertyValue = (long) changedProperties.get(AccountProperty.BALANCE);
			final long balanceChange = merkleAccount != null ?
					balancePropertyValue - merkleAccount.getBalance() : balancePropertyValue;

			if (balanceChange != 0L) {
				sideEffectsTracker.trackHbarChange(account, balanceChange);
			}
			return balanceChange;
		}

		return 0L;
	}

	private void checkSum(long sum) {
		if (sum != 0) {
			throw new IllegalStateException("Invalid balance changes!");
		}
	}
}