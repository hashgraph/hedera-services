package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import java.util.function.Supplier;

public class RewardCalculator {
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final GlobalDynamicProperties properties;

	@Inject
	public RewardCalculator(final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final GlobalDynamicProperties properties) {
		this.accounts = accounts;
		this.properties = properties;
	}

	final boolean areRewardsActivated(){
		
	}

	final long computeRewards() {
		final var stakingAccount = "0.0.800";
		final var startThreshold = 100;

		if (accounts.get().get(stakingAccount).getBalance() < startThreshold) {
			return;
		}


	}
}
