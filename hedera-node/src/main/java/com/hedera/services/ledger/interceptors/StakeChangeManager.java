package com.hedera.services.ledger.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

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
			return account != null && account.isDeclinedReward();
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
			@NotNull final int stakeeI,
			final long delta,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var mutableChanges = new EnumMap<>(pendingChanges.changes(stakeeI));
		if (mutableChanges.containsKey(STAKED_TO_ME)) {
			mutableChanges.put(STAKED_TO_ME, (long) mutableChanges.get(STAKED_TO_ME) + delta);
		} else {
			mutableChanges.put(STAKED_TO_ME, delta);
		}
		pendingChanges.updateChange(stakeeI, mutableChanges);
	}

	public void updateBalance(
			final long delta,
			final int rewardAccountI,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var mutableChanges = new EnumMap<>(pendingChanges.changes(rewardAccountI));
		if (mutableChanges.containsKey(BALANCE)) {
			mutableChanges.put(BALANCE, (long) mutableChanges.get(BALANCE) + delta);
		} else {
			mutableChanges.put(BALANCE, delta);
		}
		pendingChanges.updateChange(rewardAccountI, mutableChanges);
	}

	static boolean isWithinRange(final long stakePeriodStart, final long latestRewardableStakePeriodStart) {
		return stakePeriodStart > -1 && stakePeriodStart < latestRewardableStakePeriodStart;
	}

	static boolean hasStakeFieldChanges(@NotNull final Map<AccountProperty, Object> changes) {
		return changes.containsKey(BALANCE) || changes.containsKey(DECLINE_REWARD) ||
				changes.containsKey(STAKED_ID) || changes.containsKey(STAKED_TO_ME);
	}
}
