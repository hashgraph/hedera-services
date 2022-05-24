package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import java.util.function.Supplier;

public class StakeInfoManager {
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;

	@Inject
	public StakeInfoManager(final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.stakingInfo = stakingInfo;
	}

	public MerkleStakingInfo mutableStakeInfoFor(long nodeId) {
		return stakingInfo.get().getForModify(EntityNum.fromLong(nodeId));
	}

	public void clearRewardsHistory() {
		stakingInfo.get().forEach((entityNum, info) -> info.clearRewardSumHistory());
	}
}
