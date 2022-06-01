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
import com.hedera.services.config.AccountNumbers;
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
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalBalanceGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalDeclineRewardGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalStakedToMeGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.getAccountStakeeNum;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.getNodeStakeeNum;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.hasStakeFieldChanges;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateBalance;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateStakedToMe;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	private final StakeChangeManager stakeChangeManager;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final RewardCalculator rewardCalculator;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties dynamicProperties;
	private final StakePeriodManager stakePeriodManager;
	private final StakeInfoManager stakeInfoManager;
	private final AccountNumbers accountNumbers;

	private boolean rewardsActivated;
	// boolean to track if the account has been rewarded already with one of the pending changes
	private boolean[] hasBeenRewarded = new boolean[64];

	private long newFundingBalance;

	private static final Logger log = LogManager.getLogger(StakeAwareAccountsCommitsInterceptor.class);

	public StakeAwareAccountsCommitsInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final GlobalDynamicProperties dynamicProperties,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager stakeChangeManager,
			final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager,
			final AccountNumbers accountNumbers) {
		super(sideEffectsTracker);
		this.stakeChangeManager = stakeChangeManager;
		this.networkCtx = networkCtx;
		this.rewardCalculator = rewardCalculator;
		this.sideEffectsTracker = sideEffectsTracker;
		this.dynamicProperties = dynamicProperties;
		this.stakePeriodManager = stakePeriodManager;
		this.stakeInfoManager = stakeInfoManager;
		this.accountNumbers = accountNumbers;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		if (pendingChanges.size() == 0) {
			return;
		}
		// if the rewards are activated previously they will not be activated again
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();

		// initialize finding balance
		newFundingBalance = -1;

		// Iterate through the change set, maintaining two invariants:
		//   1. At the beginning of iteration i, any account that is rewardable due to change in balance or
		//      stakedAccountId or stakedNodeId or declineRewards fields. Also checks the balance of funding account
		//      0.0.800 [stakingRewardAccount] if it has reached the ONE TIME threshold to activate staking.
		//      NOTE that this activation happens only once.
		//   2. Any account whose stakedToMe balance is affected by a change in the [0, i) range has
		//      been, if not already present, added to the pendingChanges; and its changes include its
		//      new STAKED_TO_ME change
		updateAccountStakes(pendingChanges);

		// Iterate through the change set again to update node stakes; we do this is in a
		// separate loop to ensure all STAKED_TO_ME fields have their final values
		updateNodeStakes(pendingChanges);
		updateFundingRewardBalances(pendingChanges);

		super.preview(pendingChanges);

		if (!rewardsActivated && newFundingBalance > dynamicProperties.getStakingStartThreshold()) {
			activateStakingRewards();
		}
	}

	private void updateAccountStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			hasBeenRewarded[i] = false;

			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);

			// Update BALANCE and STAKE_PERIOD_START in the pending changes for this account, if reward-eligible
			if (isRewardable(account, changes)) {
				payReward(i, account, changes);
			}
			// Update any STAKED_TO_ME side effects of this change
			n = updateStakedToMeSideEffects(account, changes, pendingChanges);

			if (!rewardsActivated && pendingChanges.id(i).getAccountNum() == accountNumbers.stakingRewardAccount()) {
				newFundingBalance = finalBalanceGiven(account, changes);
			}
		}
	}

	private void updateFundingRewardBalances(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		final var rewardsPaid = rewardCalculator.rewardsPaidInThisTxn();
		if (rewardsPaid > 0) {
			final var rewardAccountI = stakeChangeManager.findOrAdd(
					accountNumbers.stakingRewardAccount(), pendingChanges);
			updateBalance(-rewardsPaid, rewardAccountI, pendingChanges);
			// no need to update newFundingBalance because if rewardsPaid > 0, we will not be needing newFundingBalance
			// for rewards activation, since rewards are already activated
		}
	}

	private void updateNodeStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);

			final var curNodeId = (account != null) ? account.getStakedId() : 0L;
			final var newNodeId = getNodeStakeeNum(changes);
			if (curNodeId < 0 && curNodeId != newNodeId) { // should include newNodeId != 0 ?
				// Node stakee has been replaced, withdraw stakeRewarded or stakeNotRewarded from ex-stakee based on
				// isDeclineReward option
				stakeChangeManager.withdrawStake(
						account.getStakedNodeAddressBookId(), // since nodeId is saved as  -nodeId -1
						account.getBalance() + account.getStakedToMe(),
						account != null && account.isDeclinedReward());
			}
			if (newNodeId < 0) {
				// Award updated stake to new node stakee to the fields stakeRewarded or stakeNotRewarded from
				// ex-stakee based on isDeclineReward option
				stakeChangeManager.awardStake(
						Math.abs(newNodeId + 1), // since nodeId is saved as  -nodeId -1
						finalBalanceGiven(account, changes) + finalStakedToMeGiven(account, changes),
						finalDeclineRewardGiven(account, changes));
			}
		}
	}

	int updateStakedToMeSideEffects(
			final MerkleAccount account,
			final Map<AccountProperty, Object> changes,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		int changesSize = pendingChanges.size();

		final var curStakeeNum = (account != null) ? account.getStakedId() : 0L;
		final var newStakeeNum = getAccountStakeeNum(changes);

		if (curStakeeNum > 0 && curStakeeNum != newStakeeNum) { // should add newStakeeNum != 0 ?
			// Stakee has been replaced, withdraw initial balance from ex-stakee
			final var exStakeeI = stakeChangeManager.findOrAdd(curStakeeNum, pendingChanges);
			updateStakedToMe(exStakeeI, -account.getBalance(), pendingChanges);
			if (exStakeeI == changesSize) {
				changesSize++;
				// If the changesSize is more than hasBeenRewarded array size, double hasBeenRewarded.
				// This may happen very rarely.
				checkHasBeenRewardedSize(changesSize);
			} else if (!hasBeenRewarded[exStakeeI]) {
				payRewardIfRewardable(pendingChanges, exStakeeI);
			}
		}
		if (newStakeeNum > 0) {
			// Add pending balance to new stakee
			final var newStakeeI = stakeChangeManager.findOrAdd(newStakeeNum, pendingChanges);
			updateStakedToMe(newStakeeI, finalBalanceGiven(account, changes), pendingChanges);
			if (newStakeeI == changesSize) {
				changesSize++;
				// If the changesSize is more than hasBeenRewarded array size, double hasBeenRewarded.
				// This may happen very rarely.
				checkHasBeenRewardedSize(changesSize);
			} else if (!hasBeenRewarded[newStakeeI]) {
				payRewardIfRewardable(pendingChanges, newStakeeI);
			}
		}
		return changesSize;
	}

	private void checkHasBeenRewardedSize(final int changesSize) {
		if (changesSize >= hasBeenRewarded.length) {
			final var newHasBeenRewarded = new boolean[hasBeenRewarded.length * 2];
			System.arraycopy(hasBeenRewarded, 0, newHasBeenRewarded, 0, hasBeenRewarded.length);
			hasBeenRewarded = newHasBeenRewarded;
		}
	}

	private void payRewardIfRewardable(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			final int stakeeI) {
		final var account = pendingChanges.entity(stakeeI);
		final var changes = pendingChanges.changes(stakeeI);
		if (isRewardable(account, changes)) {
			payReward(stakeeI, account, changes);
		}
	}

	private void payReward(final int accountI,
			final MerkleAccount account,
			final Map<AccountProperty, Object> changes) {
		rewardCalculator.updateRewardChanges(account, changes);
		final var reward = rewardCalculator.getAccountReward();
		sideEffectsTracker.trackRewardPayment(account.number(), reward);
		hasBeenRewarded[accountI] = true;
	}

	/**
	 * Checks if the account is eligible for rewards.
	 *
	 * @param account
	 * 		account that is being checked
	 * @param changes
	 * 		account property changes
	 * @return true if rewardable, false otherwise
	 */
	boolean isRewardable(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		Boolean changedDecline = (Boolean) changes.get(DECLINE_REWARD);
		return account != null
				&& account.getStakedId() < 0
				&& rewardsActivated
				&& hasStakeFieldChanges(changes)
				&& stakePeriodManager.isRewardable(account.getStakePeriodStart())
				&& !Boolean.TRUE.equals(changedDecline)
				&& (!account.isDeclinedReward() || Boolean.FALSE.equals(changedDecline));
	}

	/**
	 * Checks and activates staking rewards if the staking funding account balance reaches threshold
	 */
	private void activateStakingRewards() {
		long todayNumber = stakePeriodManager.currentStakePeriod();

		networkCtx.get().setStakingRewardsActivated(true);
		stakeInfoManager.clearRewardsHistory();
		stakeChangeManager.setStakePeriodStart(todayNumber);
		log.info("Staking rewards is activated and rewardSumHistory is cleared");
	}

	/* only used for unit tests */
	@VisibleForTesting
	public void setRewardsActivated(final boolean rewardsActivated) {
		this.rewardsActivated = rewardsActivated;
	}

	@VisibleForTesting
	public boolean[] getHasBeenRewarded() {
		return hasBeenRewarded;
	}

	@VisibleForTesting
	public void setHasBeenRewarded(final boolean[] hasBeenRewarded) {
		this.hasBeenRewarded = hasBeenRewarded;
	}
}
