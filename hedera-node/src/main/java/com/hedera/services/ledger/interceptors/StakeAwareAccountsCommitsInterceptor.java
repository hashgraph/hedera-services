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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.StakeChangeManager.finalBalanceGiven;
import static com.hedera.services.ledger.accounts.staking.StakeChangeManager.hasStakeFieldChanges;
import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.isWithinRange;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	private final StakeChangeManager stakeChangeManager;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final RewardCalculator rewardCalculator;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties dynamicProperties;
	private final StakePeriodManager stakePeriodManager;
	private final StakeInfoManager stakeInfoManager;

	private boolean rewardsActivated;
	private boolean rewardBalanceChanged;
	private long newRewardBalance;
	private Set<Long> hasBeenRewarded;

	private static final Logger log = LogManager.getLogger(StakeAwareAccountsCommitsInterceptor.class);
	private static final long STAKING_FUNDING_ACCOUNT_NUMBER = 800L;

	public StakeAwareAccountsCommitsInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final GlobalDynamicProperties dynamicProperties,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager stakeChangeManager,
			final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager) {
		super(sideEffectsTracker);
		this.stakeChangeManager = stakeChangeManager;
		this.networkCtx = networkCtx;
		this.rewardCalculator = rewardCalculator;
		this.sideEffectsTracker = sideEffectsTracker;
		this.dynamicProperties = dynamicProperties;
		this.stakePeriodManager = stakePeriodManager;
		this.stakeInfoManager = stakeInfoManager;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		// if the rewards are activated previously they will not be activated again
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();
		rewardBalanceChanged = false;

		// Iterate through the change set, maintaining two invariants:
		//   1. At the beginning of iteration i, any account that is rewardable due to change in balance or
		//      stakedAccountId or stakedNodeId or declineRewards fields. Also checks the balance of funding account
		//      0.0.800 if it has reached the ONE TIME threshold to activate staking.
		//      NOTE that this activation happens only once.
		//   2. Any account whose stakedToMe balance is affected by a change in the [0, i) range has
		//      been, if not already present, added to the pendingChanges; and its changes include its
		//      new STAKED_TO_ME change
		updateAccountStakes(pendingChanges);

		// Now iterate through the change set again to update node stakes; we do this is in a
		// separate loop to ensure all STAKED_TO_ME fields have their final values
		updateNodeStakes(pendingChanges);
		updateFundingRewardBalances(pendingChanges);

		super.preview(pendingChanges);

		checkStakingRewardsActivation();
	}

	private void updateAccountStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		// The latest period by which an account must have started staking, if it can be eligible for a
		// reward; if staking is not active, this will return Long.MIN_VALUE so no account is eligible
		final var latestEligibleStart = stakePeriodManager.latestRewardableStakePeriodStart();

		// boolean to track if the account has been rewarded already with one of the pending changes
		hasBeenRewarded = new HashSet<>();

		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			final var accountNum = pendingChanges.id(i).getAccountNum();

//			rewardBalanceChanged |= accountNum == STAKING_FUNDING_ACCOUNT_NUMBER;
			checksFundingBalanceChange(changes, accountNum);

			// Update BALANCE and STAKE_PERIOD_START in the pending changes for this account, if reward-eligible
			if (isRewardable(account, changes, latestEligibleStart)) {
				payReward(account, changes, accountNum);
			}
			// Update any STAKED_TO_ME side effects of this change
			n = updateStakedToMeSideEffects(i, pendingChanges, latestEligibleStart);
		}
	}

	private void checksFundingBalanceChange(final Map<AccountProperty, Object> changes, final long accountNum) {
		if (changes.containsKey(AccountProperty.BALANCE)) {
			final long newBalance = (long) changes.get(AccountProperty.BALANCE);
			if (accountNum == STAKING_FUNDING_ACCOUNT_NUMBER) {
				rewardBalanceChanged = true;
				newRewardBalance = newBalance;
			}
		}
	}


	private void updateFundingRewardBalances(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		final var rewardsPaid = rewardCalculator.rewardsPaidInThisTxn();
		rewardBalanceChanged |= rewardsPaid > 0;
		if (rewardsPaid > 0) {
			final var rewardAccountI = stakeChangeManager.findOrAdd(800L, pendingChanges);
			newRewardBalance += stakeChangeManager.updateBalance(-rewardsPaid, rewardAccountI, pendingChanges);
		}
	}

	private void updateNodeStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);

			final var curNodeId = (account != null) ? account.getStakedId() : 0L;
			final var newNodeId = stakeChangeManager.getNodeStakeeNum(changes);
			if (curNodeId < 0 && curNodeId != newNodeId) {
				// Node stakee has been replaced, withdraw stakeRewarded or stakeNotRewarded from ex-stakee based on
				// isDeclineReward option
				stakeChangeManager.withdrawStake(
						Math.abs(curNodeId + 1),
						account.getBalance() + account.getStakedToMe(),
						stakeChangeManager.finalDeclineRewardGiven(account, changes));
			}
			if (newNodeId < 0) {
				// Award updated stake to new node stakee to the fields stakeRewarded or stakeNotRewarded from
				// ex-stakee based on isDeclineReward option
				stakeChangeManager.awardStake(
						Math.abs(newNodeId),
						finalBalanceGiven(account, changes) + stakeChangeManager.finalStakedToMeGiven(account, changes),
						stakeChangeManager.finalDeclineRewardGiven(account, changes));
			}
		}
	}

	int updateStakedToMeSideEffects(
			final int entityI,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			final long latestEligibleStart) {
		int changesSize = pendingChanges.size();
		final var account = pendingChanges.entity(entityI);
		final var changes = pendingChanges.changes(entityI);

		final var curStakeeNum = (account != null) ? account.getStakedId() : 0L;
		final var newStakeeNum = stakeChangeManager.getAccountStakeeNum(changes);

		if (curStakeeNum > 0 && curStakeeNum != newStakeeNum) {
			// Stakee has been replaced, withdraw initial balance from ex-stakee
			final var exStakeeI = stakeChangeManager.findOrAdd(curStakeeNum, pendingChanges);
			stakeChangeManager.updateStakedToMe(exStakeeI, -account.getBalance(), pendingChanges);
			if (exStakeeI == changesSize) {
				changesSize++;
			} else if (!hasBeenRewarded.contains(curStakeeNum)) {
				payRewardIfRewardable(pendingChanges, exStakeeI, latestEligibleStart);
			}
		}
		if (newStakeeNum > 0) {
			// Add pending balance to new stakee
			final var newStakeeI = stakeChangeManager.findOrAdd(newStakeeNum, pendingChanges);
			stakeChangeManager.updateStakedToMe(newStakeeI, finalBalanceGiven(account, changes), pendingChanges);
			if (newStakeeI == changesSize) {
				changesSize++;
			} else if (!hasBeenRewarded.contains(newStakeeNum)) {
				payRewardIfRewardable(pendingChanges, newStakeeI, latestEligibleStart);
			}
		}
		return changesSize;
	}

	void payRewardIfRewardable(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			final int stakeeI,
			final long latestEligibleStart) {
		final var account = pendingChanges.entity(stakeeI);
		final var changes = pendingChanges.changes(stakeeI);
		final var accountNum = pendingChanges.id(stakeeI).getAccountNum();
		if (isRewardable(account, changes, latestEligibleStart)) {
			payReward(account, changes, accountNum);
		}
	}

	private void payReward(final MerkleAccount account,
			final Map<AccountProperty, Object> changes,
			final long accountNum) {
		rewardCalculator.updateRewardChanges(account, changes);
		final var reward = rewardCalculator.getAccountReward();
		sideEffectsTracker.trackRewardPayment(accountNum, reward);
		hasBeenRewarded.add(accountNum);
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

	void checkStakingRewardsActivation() {
		if (!shouldActivateStakingRewards()) {
			return;
		}
		long todayNumber = stakePeriodManager.currentStakePeriod();

		networkCtx.get().setStakingRewards(true);
		stakeInfoManager.clearRewardsHistory();
		stakeChangeManager.setStakePeriodStart(todayNumber);
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
		// newRewardBalance needs to check BALANCE changes for 0.0.800
	}


	/* only used for unit tests */
	@VisibleForTesting
	public boolean isRewardsActivated() {
		return rewardsActivated;
	}

	@VisibleForTesting
	public void setRewardsActivated(final boolean rewardsActivated) {
		this.rewardsActivated = rewardsActivated;
	}

	@VisibleForTesting
	public boolean isRewardBalanceChanged() {
		return rewardBalanceChanged;
	}

	@VisibleForTesting
	public void setRewardBalanceChanged(final boolean rewardBalanceChanged) {
		this.rewardBalanceChanged = rewardBalanceChanged;
	}

	@VisibleForTesting
	public long getNewRewardBalance() {
		return newRewardBalance;
	}

	@VisibleForTesting
	public void setNewRewardBalance(final long newRewardBalance) {
		this.newRewardBalance = newRewardBalance;
	}

	@VisibleForTesting
	public Set<Long> getHasBeenRewarded() {
		return hasBeenRewarded;
	}

	@VisibleForTesting
	public void setHasBeenRewarded(final Set<Long> hasBeenRewarded) {
		this.hasBeenRewarded = hasBeenRewarded;
	}
}
