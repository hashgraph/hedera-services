package com.hedera.services.ledger.interceptors;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

@Singleton
public class StakeChangeManager {
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private final List<StakeChange> stakeChanges;

	@Inject
	public StakeChangeManager(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.accounts = accounts;
		this.stakingInfo = stakingInfo;
		this.stakeChanges = new ArrayList<>();
	}

	public void aggregateAndCommitStakeAdjustments(CurrencyAdjustments changes) {
		Map<EntityNum, Long> aggregateStakes = new TreeMap<>();
		for (final var change : stakeChanges) {
			if (aggregateStakes.containsKey(change.stakedAccount())) {
				final var newAdjustment = aggregateStakes.get(change.stakedAccount()) + change.adjustment();
				aggregateStakes.put(change.stakedAccount(), newAdjustment);
			} else {
				aggregateStakes.put(change.stakedAccount(), change.adjustment());
			}
		}
		commitChanges(aggregateStakes, changes.asAccountAmountsList());
	}

	private void commitChanges(
			final Map<EntityNum, Long> stakedIdChanges,
			final List<AccountAmount> stakedBalanceChanges) {
		for (final var entry : stakedIdChanges.entrySet()) {
			final var mutableAccount = accounts.get().getForModify(entry.getKey());
			final var currentStake = mutableAccount.getStakedToMe();
			final var newStake = currentStake + entry.getValue();
			mutableAccount.setStakedToMe(newStake);
		}
		for (var aa : stakedBalanceChanges) {
			final var mutableAccount = accounts.get().getForModify(EntityNum.fromAccountId(aa.getAccountID()));
			final var stakedId = mutableAccount.getStakedId();
			if (stakedId == 0) {
				return;
			} else if (stakedId < 0) {
				final var mutableStakingInfo = stakingInfo.get().getForModify(EntityNum.fromLong(stakedId));
				if (mutableAccount.isDeclinedReward()) {
					mutableStakingInfo.setStakeToNotReward(mutableStakingInfo.getStakeToNotReward() + aa.getAmount());
				} else {
					mutableStakingInfo.setStakeToNotReward(mutableStakingInfo.getStakeToReward() + aa.getAmount());
				}
			} else {
				final var mutableStakedAccount = accounts.get().getForModify(EntityNum.fromLong(stakedId));
				mutableStakedAccount.setStakedToMe(mutableStakedAccount.getStakedToMe() + aa.getAmount());
			}
		}
	}

	public void recordStakeChanges(final MerkleAccount entity, final long newStakedIdNum) {
		final long newStake = entity.getBalance();
		final var newStakedId = EntityNum.fromLong(newStakedIdNum);
		if (newStakedIdNum > 0) {
			stakeChanges.add(new StakeChange(newStakedId, newStake, false));
		} else if (newStakedIdNum < 0) {
			stakeChanges.add(new StakeChange(newStakedId, newStake, true));
		}

		final var oldStakedId = EntityNum.fromLong(entity.getStakedId());
		if (oldStakedId.longValue() > 0) {
			stakeChanges.add(new StakeChange(oldStakedId, -newStake, false));
		} else if (oldStakedId.longValue() < 0) {
			stakeChanges.add(new StakeChange(oldStakedId, -newStake, true));
		}
	}
}
