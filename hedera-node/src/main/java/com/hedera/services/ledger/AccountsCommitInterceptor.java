package com.hedera.services.ledger;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class AccountsCommitInterceptor  implements
		CommitInterceptor<AccountID, MerkleAccount, AccountProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
	private final SideEffectsTracker sideEffectsTracker;

	public AccountsCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void preview(final List<AccountChanges<AccountID, MerkleAccount, AccountProperty>> changesToCommit) {
		final List<Long> balances = new ArrayList<>();
		for (final AccountChanges<AccountID, MerkleAccount, AccountProperty> accountChanges : changesToCommit) {
			if (accountChanges.changes().containsKey(AccountProperty.BALANCE)) {
				final long newBalance =
						(long) accountChanges.changes().get(AccountProperty.BALANCE) - accountChanges.account().getBalance();
				balances.add(newBalance);
				sideEffectsTracker.trackHbarChange(accountChanges.id(), newBalance);
			}
		}

		doZeroSum(balances);
	}

	private void doZeroSum(final List<Long> balances) {
		if(balances.size() > 1) {
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