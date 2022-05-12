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
			if (aggregateStakes.containsKey(change.stakedAccount())) {
				final var newAdjustment = aggregateStakes.get(change.stakedAccount()) + change.adjustment();
				aggregateStakes.put(change.stakedAccount(), newAdjustment);
			} else {
				aggregateStakes.put(change.stakedAccount(), change.adjustment());
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

	public void updateStakeId(final List<StakeAdjustment> stakeChanges, final MerkleAccount entity,
			final long newStakedIdNum) {
		final long newStake = entity.getBalance();
		if (newStakedIdNum > 0) {
			final var newStakedId = EntityNum.fromLong(newStakedIdNum);
			stakeChanges.add(new StakeAdjustment(newStakedId, newStake));
		}

		final var oldStakedId = EntityNum.fromLong(entity.getStakedId());
		if (oldStakedId.longValue() > 0) {
			stakeChanges.add(new StakeAdjustment(oldStakedId, -newStake));
		}
	}
}
