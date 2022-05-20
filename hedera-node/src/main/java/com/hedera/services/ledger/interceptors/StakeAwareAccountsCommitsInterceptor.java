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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_TO_ME;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	private final StakeChangeManager stakeChangeManager;

	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final RewardCalculator rewardCalculator;
	private final SideEffectsTracker sideEffectsTracker;

	private static final Logger log = LogManager.getLogger(AccountsCommitInterceptor.class);

	public StakeAwareAccountsCommitsInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager manager) {
		super(sideEffectsTracker, networkCtx, stakingInfo, dynamicProperties, accounts);
		stakeChangeManager = manager;
		this.networkCtx = networkCtx;
		this.stakingInfo = stakingInfo;
		this.accounts = accounts;
		this.rewardCalculator = rewardCalculator;
		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		super.preview(pendingChanges);

		// The latest period by which an account must have started staking, if it can be eligible for a
		// reward; if staking is not active, this will return Long.MIN_VALUE so no account is eligible
		final var latestEligibleStart = rewardCalculator.latestRewardableStakePeriodStart();

		// Iterate through the change set, maintaining two invariants:
		//   1. All changed accounts in the range [0, i) that have received rewards if they are eligible
		//   2. Any account whose stakedToMe balance is affected by a change in the [0, i) range has
		//      been, if not already present, added to the pendingChanges; and its changes include its
		//      new STAKED_TO_ME change
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			final var accountNum = pendingChanges.id(i).getAccountNum();

			// Update BALANCE and STAKE_PERIOD_START in the pending changes for this account, if reward-eligible
			if (isRewardable(account, changes, latestEligibleStart)) {
				final var reward = rewardCalculator.updateRewardChanges(account, changes);
				sideEffectsTracker.trackRewardPayment(accountNum, reward);
			}
			// Update any STAKED_TO_ME side effects of this change
			updateStakedToMeSideEffects(account, changes, pendingChanges, n);
		}

		// Now iterate through the change set again to update node stakes; we do this is in a
		// separate loop to ensure all STAKED_TO_ME fields have their final values
		updateNodeStakes(pendingChanges);
		checkRewardActivation();
	}

	private void updateNodeStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);

			final var curNodeId = (account != null) ? account.getStakedId() : 0L;
			final var newNodeId = getNodeStakeeNum(changes);
			if (curNodeId != 0 && curNodeId != newNodeId) {
				// Node stakee has been replaced, withdraw initial stake from ex-stakee
				stakeChangeManager.withdrawStake(
						Math.abs(curNodeId),
						account.getBalance() + account.getStakedToMe(),
						account.isDeclinedReward());
			}
			if (newNodeId != 0) {
				// Award updated stake to new node stakee
				stakeChangeManager.awardStake(
						Math.abs(newNodeId),
						finalBalanceGiven(account, changes) + finalStakedToMeGiven(account, changes),
						account.isDeclinedReward());
			}
		}
	}

	private void updateStakedToMeSideEffects(
			final MerkleAccount account,
			final Map<AccountProperty, Object> changes,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			int changesSize) {
		final var curStakeeNum = (account != null) ? account.getStakedId() : 0L;
		final var newStakeeNum = getAccountStakeeNum(changes);
		if (curStakeeNum != 0 && curStakeeNum != newStakeeNum) {
			// Stakee has been replaced, withdraw initial balance from ex-stakee
			final var exStakeeI = findOrAdd(curStakeeNum, pendingChanges);
			if (exStakeeI == changesSize) {
				changesSize++;
			}
			updateStakedToMe(-account.getBalance(), exStakeeI, pendingChanges);
		}
		if (newStakeeNum != 0) {
			// Add pending balance to new stakee
			final var newStakeeI = findOrAdd(newStakeeNum, pendingChanges);
			if (newStakeeI == changesSize) {
				changesSize++;
			}
			updateStakedToMe(finalBalanceGiven(account, changes), newStakeeI, pendingChanges);
		}
	}

	private long getAccountStakeeNum(final Map<AccountProperty, Object> changes) {
		final var entityId = (long) changes.getOrDefault(STAKED_ID, 0L);
		// Node ids are negative
		return (entityId < 0) ? 0 : entityId;
	}

	private long getNodeStakeeNum(final Map<AccountProperty, Object> changes) {
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

	private long finalStakedToMeGiven(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (changes.containsKey(STAKED_TO_ME)) {
			return (long) changes.get(STAKED_TO_ME);
		} else {
			return (account == null) ? 0 : account.getStakedToMe();
		}
	}

	private void updateStakedToMe(
			final long stakedToMeDelta,
			@NotNull final int stakeeI,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var mutableChanges = new HashMap<>(pendingChanges.changes(stakeeI));
		if (mutableChanges.containsKey(STAKED_TO_ME)) {
			mutableChanges.put(STAKED_TO_ME, (long) mutableChanges.get(STAKED_TO_ME) + stakedToMeDelta);
		} else {
			mutableChanges.put(STAKED_TO_ME, stakedToMeDelta);
		}
		pendingChanges.updateChange(stakeeI, STAKED_TO_ME, mutableChanges);
	}

	private int findOrAdd(
			final long accountNum,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var n = pendingChanges.size();
		for (int i = 0; i < n; i++) {
			if (pendingChanges.id(i).getAccountNum() == accountNum) {
				return (int) accountNum;
			}
		}
		// This account wasn't in the current change set
		pendingChanges.include(
				STATIC_PROPERTIES.scopedAccountWith(accountNum),
				accounts.get().get(EntityNum.fromLong(accountNum)),
				Map.of());
		return n;
	}

	boolean isRewardable(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes,
			final long latestRewardableStakePeriodStart
	) {
		Boolean changedDecline;
		return account != null
				&& account.getStakedId() < 0
				&& networkCtx.get().areRewardsActivated()
				&& hasStakeFieldChanges(changes)
				&& isWithinRange(account.getStakePeriodStart(), latestRewardableStakePeriodStart)
				&& !Boolean.TRUE.equals(changedDecline = (Boolean) changes.get(DECLINE_REWARD))
				&& (!account.isDeclinedReward() || Boolean.FALSE.equals(changedDecline));
	}

	boolean isWithinRange(final long stakePeriodStart, final long latestRewardableStakePeriodStart) {
		return stakePeriodStart > -1 && stakePeriodStart < latestRewardableStakePeriodStart;
	}

	boolean hasStakeFieldChanges(@NotNull final Map<AccountProperty, Object> changes) {
		return changes.containsKey(BALANCE) || changes.containsKey(DECLINE_REWARD) ||
				changes.containsKey(STAKED_ID) || changes.containsKey(STAKED_TO_ME);
	}

	void checkRewardActivation() {
		if (shouldActivateStakingRewards()) {
			networkCtx.get().setStakingRewards(true);
			stakingInfo.get().forEach((entityNum, info) -> info.clearRewardSumHistory());

			long todayNumber = LocalDate.now(zoneUTC).toEpochDay();
			accounts.get().forEach(((entityNum, account) -> {
				if (account.getStakedId() < 0) {
					account.setStakePeriodStart(todayNumber);
				}
			}));
			log.info("Staking rewards is activated and rewardSumHistory is cleared");
		}
	}
}
