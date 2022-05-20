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

	public void withdrawStake(final long curNodeId, final long amount, final boolean declinedReward) {
		final var node = stakingInfo.get().getForModify(EntityNum.fromLong(curNodeId));
		if (declinedReward) {
			node.setStakeToNotReward(node.getStakeToNotReward() - amount);
		} else {
			node.setStakeToReward(node.getStakeToReward() - amount);
		}
	}

	public void awardStake(final long newNodeId, final long amount, final boolean declinedReward) {
		final var node = stakingInfo.get().getForModify(EntityNum.fromLong(newNodeId));
		if (declinedReward) {
			node.setStakeToNotReward(node.getStakeToNotReward() + amount);
		} else {
			node.setStakeToReward(node.getStakeToReward() + amount);
		}
	}
}
