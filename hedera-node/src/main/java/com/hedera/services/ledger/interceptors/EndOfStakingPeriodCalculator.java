package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

@Singleton
public class EndOfStakingPeriodCalculator {

	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier;
	private final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier;
	private final PropertySource properties;

	@Inject
	public EndOfStakingPeriodCalculator(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier,
			final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier,
			@CompositeProps PropertySource properties
	) {
		this.accounts = accounts;
		this.stakingInfoSupplier = stakingInfoSupplier;
		this.merkleNetworkContextSupplier = merkleNetworkContextSupplier;
		this.properties = properties;
	}

	public void updateNodes() {
		final var stakingInfo = stakingInfoSupplier.get();
		final var merkleNetworkContext = merkleNetworkContextSupplier.get();
		final var maxHistory = properties.getIntProperty("staking.rewardHistory.numStoredPeriods");
		final var rewardRate = Math.min( accounts.get().get(EntityNum.fromInt(800)).getBalance(),
				properties.getDoubleProperty("staking.rewardRate"));

		long updatedTotalStakedRewardStart = 0L;
		long updatedTotalStakedStart = 0L;

		for (final var nodeNum : stakingInfo.keySet()) {
			final var merkleStakingInfo = stakingInfo.getForModify(nodeNum);
			final var rewardSumHistory = merkleStakingInfo.getRewardSumHistory();
			for (int i = maxHistory; i > 0; i--) {
				rewardSumHistory[i] = rewardSumHistory[i-1];
				// TODO : check if node was active.
				/*
					if this node was "active", then it should give rewards for this staking period
					if (node.numRoundsWithJudge / numRoundsInPeriod >= activeThreshold)
				 */
				rewardSumHistory[0] = (long) (rewardRate * merkleStakingInfo.getStakeRewardStart()
										/ merkleNetworkContext.getTotalStakedRewardStart() / 100_000_000);
			}

			final var totalStake = merkleStakingInfo.getStakeToReward() + merkleStakingInfo.getStakeToNotReward();
			final var minStake = merkleStakingInfo.getMinStake();
			final var maxStake = merkleStakingInfo.getMaxStake();

			if (totalStake > maxStake) {
				merkleStakingInfo.setStake(maxStake);
			} else if (totalStake < minStake) {
				merkleStakingInfo.setStake(0);
			} else {
				merkleStakingInfo.setStake(totalStake);
			}

			final var stakedRewardUsed = Math.min(merkleStakingInfo.getStakeToReward(), merkleStakingInfo.getStake());
			updatedTotalStakedRewardStart += stakedRewardUsed;
			updatedTotalStakedStart += merkleStakingInfo.getStake();
		}
		merkleNetworkContext.setTotalStakedRewardStart(updatedTotalStakedRewardStart);
		merkleNetworkContext.setTotalStakedStart(updatedTotalStakedStart);
	}
}
