package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import java.util.function.Supplier;

public class RewardCalculator {
	private static final EntityNum stakingFundAccount = EntityNum.fromLong(800L);
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final GlobalDynamicProperties properties;

	@Inject
	public RewardCalculator(final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final GlobalDynamicProperties properties) {
		this.accounts = accounts;
		this.properties = properties;
	}

	final void computeAndApply() {
		if (!areRewardsActivated()) {
			return;
		}

		final var rewardsTobeApplied = computeRewards();
	}

	final boolean areRewardsActivated() {
		final var stakingAccountBalance = accounts.get().get(stakingFundAccount).getBalance();
		return stakingAccountBalance >= properties.getStakingStartThreshold();
	}

	final long computeRewards() {

	}
}
