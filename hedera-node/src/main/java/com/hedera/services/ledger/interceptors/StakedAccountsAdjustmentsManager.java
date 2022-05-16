package com.hedera.services.ledger.interceptors;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class StakedAccountsAdjustmentsManager {
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	@Inject
	public StakedAccountsAdjustmentsManager(final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts) {
		this.accounts = accounts;
	}

	public void aggregateAndCommitStakeAdjustments(List<StakeAdjustment> changes) {
		Map<EntityNum, Long> aggregateStakes = new TreeMap<>();
		for (final var change : changes) {
			if (aggregateStakes.containsKey(change.account())) {
				final var newAdjustment = aggregateStakes.get(change.account()) + change.adjustment();
				aggregateStakes.put(change.account(), newAdjustment);
			} else {
				aggregateStakes.put(change.account(), change.adjustment());
			}
		}
		commitChanges(aggregateStakes);
	}

	private void commitChanges(final Map<EntityNum, Long> aggregateStakes) {
		for (final var entry : aggregateStakes.entrySet()) {
			final var mutableAccount = accounts.get().getForModify(entry.getKey());
			final var currentStake = mutableAccount.getStakedToMe();
			final var newStake = currentStake + entry.getValue();
			mutableAccount.setStakedToMe(newStake);
		}
	}
}
