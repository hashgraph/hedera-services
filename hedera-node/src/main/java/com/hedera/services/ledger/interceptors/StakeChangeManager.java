package com.hedera.services.ledger.interceptors;

import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_TO_ME;

@Singleton
public class StakeChangeManager {
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;

	@Inject
	public StakeChangeManager(final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.stakingInfo = stakingInfo;
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

	public long getAccountStakeeNum(final Map<AccountProperty, Object> changes) {
		final var entityId = (long) changes.getOrDefault(STAKED_ID, 0L);
		// Node ids are negative
		return (entityId < 0) ? 0 : entityId;
	}

	long getNodeStakeeNum(final Map<AccountProperty, Object> changes) {
		final var entityId = (long) changes.getOrDefault(STAKED_ID, 0L);
		// Node ids are negative
		return (entityId < 0) ? entityId : 0;
	}

	public static long finalBalanceGiven(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (changes.containsKey(BALANCE)) {
			return (long) changes.get(BALANCE);
		} else {
			return (account == null) ? 0 : account.getBalance();
		}
	}

	public boolean finalDeclineRewardGiven(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (changes.containsKey(DECLINE_REWARD)) {
			return (Boolean) changes.get(DECLINE_REWARD);
		} else {
			return (account == null) ? false : account.isDeclinedReward();
		}
	}

	public long finalStakedToMeGiven(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (changes.containsKey(STAKED_TO_ME)) {
			return (long) changes.get(STAKED_TO_ME);
		} else {
			return (account == null) ? 0 : account.getStakedToMe();
		}
	}

	public void updateStakedToMe(
			final long stakedToMeDelta,
			@NotNull final int stakeeI,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var mutableChanges = new EnumMap<>(pendingChanges.changes(stakeeI));
		if (mutableChanges.containsKey(STAKED_TO_ME)) {
			mutableChanges.put(STAKED_TO_ME, (long) mutableChanges.get(STAKED_TO_ME) + stakedToMeDelta);
		} else {
			mutableChanges.put(STAKED_TO_ME, stakedToMeDelta);
		}
		pendingChanges.updateChange(stakeeI, STAKED_TO_ME, mutableChanges);
	}

	public void updateBalance(
			final long stakedToMeDelta,
			final int rewardAccountI,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var mutableChanges = new EnumMap<>(pendingChanges.changes(rewardAccountI));
		if (mutableChanges.containsKey(BALANCE)) {
			mutableChanges.put(BALANCE, (long) mutableChanges.get(BALANCE) + stakedToMeDelta);
		} else {
			mutableChanges.put(BALANCE, stakedToMeDelta);
		}
		pendingChanges.updateChange(rewardAccountI, BALANCE, mutableChanges);
	}

	static boolean isWithinRange(final long stakePeriodStart, final long latestRewardableStakePeriodStart) {
		return stakePeriodStart > -1 && stakePeriodStart < latestRewardableStakePeriodStart;
	}

	static boolean hasStakeFieldChanges(@NotNull final Map<AccountProperty, Object> changes) {
		return changes.containsKey(BALANCE) || changes.containsKey(DECLINE_REWARD) ||
				changes.containsKey(STAKED_ID) || changes.containsKey(STAKED_TO_ME);
	}

}
