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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;
import static com.hedera.services.ledger.interceptors.StakeChangeManager.finalBalanceGiven;
import static com.hedera.services.ledger.interceptors.StakeChangeManager.hasStakeFieldChanges;
import static com.hedera.services.ledger.interceptors.StakeChangeManager.isWithinRange;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	private final StakeChangeManager manager;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final RewardCalculator rewardCalculator;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties dynamicProperties;

	private boolean rewardsActivated;
	private boolean rewardBalanceChanged;
	private long newRewardBalance;

	private static final Logger log = LogManager.getLogger(StakeAwareAccountsCommitsInterceptor.class);
	private static final long STAKING_FUNDING_ACCOUNT_NUMBER = 800L;

	public StakeAwareAccountsCommitsInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager manager) {
		super(sideEffectsTracker);
		this.manager = manager;
		this.networkCtx = networkCtx;
		this.stakingInfo = stakingInfo;
		this.accounts = accounts;
		this.rewardCalculator = rewardCalculator;
		this.sideEffectsTracker = sideEffectsTracker;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		// if the rewards are activated previously they will not be activated again
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();
		rewardBalanceChanged = false;

		super.preview(pendingChanges);

		// The latest period by which an account must have started staking, if it can be eligible for a
		// reward; if staking is not active, this will return Long.MIN_VALUE so no account is eligible
		final var latestEligibleStart = rewardCalculator.latestRewardableStakePeriodStart();

		// boolean to track if the account has been rewarded already with one of the pending changes
		Set<Long> hasBeenRewarded = new HashSet<>();

		// Iterate through the change set, maintaining two invariants:
		//   1. At the beginning of iteration i, any account that is rewardable due to change in balance or
		//      stakedAccountId or stakedNodeId or declineRewards fields. Also checks the balance of funding account
		//      0.0.800 if it has reached the ONE TIME threshold to activate staking.
		//      NOTE that this activation happens only once.
		//   2. Any account whose stakedToMe balance is affected by a change in the [0, i) range has
		//      been, if not already present, added to the pendingChanges; and its changes include its
		//      new STAKED_TO_ME change
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			final var accountNum = pendingChanges.id(i).getAccountNum();

			checksFundingBalanceChange(changes, account, accountNum);

			// Update BALANCE and STAKE_PERIOD_START in the pending changes for this account, if reward-eligible
			if (isRewardable(account, changes, latestEligibleStart)) {
				payReward(account, changes, accountNum, hasBeenRewarded);
			}
			// Update any STAKED_TO_ME side effects of this change
			n = updateStakedToMeSideEffects(account, changes, pendingChanges, n, hasBeenRewarded, latestEligibleStart);
		}

		// Now iterate through the change set again to update node stakes; we do this is in a
		// separate loop to ensure all STAKED_TO_ME fields have their final values
		updateNodeStakes(pendingChanges);
		trackRewardsPaidByFunding(pendingChanges);

		activateRewardsIfValid();
	}

	private void checksFundingBalanceChange(final Map<AccountProperty, Object> changes,
			final MerkleAccount account, final long accountNum) {
		if (changes.containsKey(AccountProperty.BALANCE)) {
			final long newBalance = (long) changes.get(AccountProperty.BALANCE);
			if (account != null && (accountNum == STAKING_FUNDING_ACCOUNT_NUMBER)) {
				rewardBalanceChanged = true;
				newRewardBalance = newBalance;
			}
		}
	}

	private void trackRewardsPaidByFunding(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		final var rewardsPaid = rewardCalculator.rewardsPaidInThisTxn();
		final var rewardAccountI = findOrAdd(800L, pendingChanges);
		manager.updateBalance(-rewardsPaid, rewardAccountI, pendingChanges);
	}

	private void updateNodeStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);

			final var curNodeId = (account != null) ? account.getStakedId() : 0L;
			final var newNodeId = manager.getNodeStakeeNum(changes);
			if (curNodeId != 0 && curNodeId != newNodeId) {
				// Node stakee has been replaced, withdraw initial stake from ex-stakee
				manager.withdrawStake(
						Math.abs(curNodeId),
						account.getBalance() + account.getStakedToMe(),
						manager.finalDeclineRewardGiven(account, changes));
			}
			if (newNodeId != 0) {
				// Award updated stake to new node stakee
				manager.awardStake(
						Math.abs(newNodeId),
						finalBalanceGiven(account, changes) + manager.finalStakedToMeGiven(account, changes),
						manager.finalDeclineRewardGiven(account, changes));
			}
		}
	}

	private int updateStakedToMeSideEffects(
			final MerkleAccount account,
			final Map<AccountProperty, Object> changes,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			int changesSize,
			final Set<Long> hasBeenRewarded,
			final long latestEligibleStart) {
		final var curStakeeNum = (account != null) ? account.getStakedId() : 0L;
		final var newStakeeNum = manager.getAccountStakeeNum(changes);
		if (curStakeeNum != 0 && curStakeeNum != newStakeeNum) {
			// Stakee has been replaced, withdraw initial balance from ex-stakee
			final var exStakeeI = findOrAdd(curStakeeNum, pendingChanges);
			manager.updateStakedToMe(-account.getBalance(), exStakeeI, pendingChanges);
			if (exStakeeI == changesSize) {
				changesSize++;
			} else if (!hasBeenRewarded.contains(curStakeeNum)) {
				payRewardIfRewardable(pendingChanges, exStakeeI, hasBeenRewarded, latestEligibleStart);
			}
		}
		if (newStakeeNum != 0) {
			// Add pending balance to new stakee
			final var newStakeeI = findOrAdd(newStakeeNum, pendingChanges);
			manager.updateStakedToMe(finalBalanceGiven(account, changes), newStakeeI, pendingChanges);
			if (newStakeeI == changesSize) {
				changesSize++;
			} else if (!hasBeenRewarded.contains(newStakeeNum)) {
				payRewardIfRewardable(pendingChanges, newStakeeI, hasBeenRewarded, latestEligibleStart);
			}
		}
		return changesSize;
	}

	private void payRewardIfRewardable(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			final int newStakeeI,
			final Set<Long> hasBeenRewarded,
			final long latestEligibleStart) {
		final var account = pendingChanges.entity(newStakeeI);
		final var changes = pendingChanges.changes(newStakeeI);
		final var accountNum = pendingChanges.id(newStakeeI).getAccountNum();
		if (isRewardable(account, changes, latestEligibleStart)) {
			payReward(account, changes, accountNum, hasBeenRewarded);
		}
	}

	private void payReward(final MerkleAccount account,
			final Map<AccountProperty, Object> changes,
			final long accountNum,
			final Set<Long> hasBeenRewarded) {
		final var reward = rewardCalculator.updateRewardChanges(account, changes);
		sideEffectsTracker.trackRewardPayment(accountNum, reward);
		hasBeenRewarded.add(accountNum);
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
		Boolean changedDecline = (Boolean) changes.get(DECLINE_REWARD);
		return account != null
				&& account.getStakedId() < 0
				&& networkCtx.get().areRewardsActivated()
				&& hasStakeFieldChanges(changes)
				&& isWithinRange(account.getStakePeriodStart(), latestRewardableStakePeriodStart)
				&& !Boolean.TRUE.equals(changedDecline)
				&& (!account.isDeclinedReward() || Boolean.FALSE.equals(changedDecline));
	}

	void activateRewardsIfValid() {
		if (!shouldActivateStakingRewards()) {
			return;
		}
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

	/**
	 * If the balance on 0.0.800 changed in the current transaction and the balance reached above the specified
	 * threshold activates staking rewards
	 *
	 * @return true if rewards should be activated, false otherwise
	 */
	protected boolean shouldActivateStakingRewards() {
		return !rewardsActivated && rewardBalanceChanged && (newRewardBalance >= dynamicProperties.getStakingStartThreshold());
	}


	/* only used for unit tests */
	public boolean isRewardsActivated() {
		return rewardsActivated;
	}

	public void setRewardsActivated(final boolean rewardsActivated) {
		this.rewardsActivated = rewardsActivated;
	}

	public boolean isRewardBalanceChanged() {
		return rewardBalanceChanged;
	}

	public void setRewardBalanceChanged(final boolean rewardBalanceChanged) {
		this.rewardBalanceChanged = rewardBalanceChanged;
	}

	public long getNewRewardBalance() {
		return newRewardBalance;
	}

	public void setNewRewardBalance(final long newRewardBalance) {
		this.newRewardBalance = newRewardBalance;
	}
}
