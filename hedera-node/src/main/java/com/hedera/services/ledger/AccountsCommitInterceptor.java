package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class AccountsCommitInterceptor implements
		CommitInterceptor<AccountID, MerkleAccount, AccountProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
	private final SideEffectsTracker sideEffectsTracker;

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
		final List<Long> balances = new ArrayList<>();
		for (final var changeToCommit : changesToCommit) {
			final var account = changeToCommit.id();
			final var merkleAccount = changeToCommit.merkleLeaf();
			final var changedProperties = changeToCommit.changes();

			if (merkleAccount == null) {
				balances.add((long) changedProperties.get(AccountProperty.BALANCE));
				sideEffectsTracker.trackHbarChange(account,
						(long) changedProperties.get(AccountProperty.BALANCE));
				continue;
			}
//
			trackHBarTransfer(changedProperties, account, merkleAccount, balances);
		}

		doZeroSum(balances);
	}

	private void trackHBarTransfer(final Map<AccountProperty, Object> changedProperties,
								   final AccountID account, final MerkleAccount merkleAccount,
								   final List<Long> balances) {
		if (changedProperties.containsKey(AccountProperty.BALANCE)) {
			final long balanceChange =
					(long) changedProperties.get(AccountProperty.BALANCE) - merkleAccount.getBalance();

			if(balanceChange!=0L) {
				balances.add(balanceChange);
				sideEffectsTracker.trackHbarChange(account, balanceChange);
			}
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