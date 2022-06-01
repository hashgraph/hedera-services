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
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalBalanceGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalDeclineRewardGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalStakedToMeGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.hasStakeFieldChanges;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateBalance;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateStakedToMe;
import static com.hedera.services.ledger.interceptors.StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	private static final Logger log = LogManager.getLogger(StakeAwareAccountsCommitsInterceptor.class);
	private static final int UNREALISTIC_NUM_CHANGES = 64;

	private final StakeChangeManager stakeChangeManager;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final RewardCalculator rewardCalculator;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties dynamicProperties;
	private final StakePeriodManager stakePeriodManager;
	private final StakeInfoManager stakeInfoManager;
	private final AccountNumbers accountNumbers;

	// The current and new staked id's of the change being previewed
	private long curStakedId;
	private long newStakedId;
	private long newFundingBalance;
	private boolean rewardsActivated;
	// Tracks if the account has been rewarded already with one of the pending changes
	private boolean[] hasBeenRewarded = new boolean[UNREALISTIC_NUM_CHANGES];
	private StakeChangeScenario[] stakeChangeScenarios = new StakeChangeScenario[UNREALISTIC_NUM_CHANGES];

	public StakeAwareAccountsCommitsInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final GlobalDynamicProperties dynamicProperties,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager stakeChangeManager,
			final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager,
			final AccountNumbers accountNumbers
	) {
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
		final var n = pendingChanges.size();
		if (n == 0) {
			return;
		}
		prepareAuxiliaryArraysFor(n);

		// Once rewards are activated, they remain activated
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();
		// Initialize funding reward balance (will only be updated and consulted if rewards are not active)
		newFundingBalance = -1;

		// Iterates through the change set, maintaining two invariants:
		//   1. At the beginning of iteration i, any account in the [0, i) range that is reward-able due to
		//      change in balance, stakedAccountId, stakedNodeId, declineRewards fields; _has_ been rewarded.
		//   2. Any account whose stakedToMe balance was affected by one or more changes in the [0, i) range
		//      has been, if not already present, added to the pendingChanges; and its changes reflect all
		//      these stakedToMe change
		updateAccountStakes(pendingChanges);
		// Iterates through the change set to update node stakes; requires a separate loop so all stakedToMe are final
		updateNodeStakes(pendingChanges);
		finalizeRewardBalanceChange(pendingChanges);

		super.preview(pendingChanges);

		if (!rewardsActivated && newFundingBalance > dynamicProperties.getStakingStartThreshold()) {
			activateStakingRewards();
		}
	}

	private void updateAccountStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		final var origN = pendingChanges.size();
		// We re-compute pendingChanges.size() in the for condition b/c stakeToMe side effects can increase it
		for (int i = 0; i < pendingChanges.size(); i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			stakeChangeScenarios[i] = scenarioFor(account, changes);

			if (!hasBeenRewarded[i] && isRewardable(account, changes)) {
				payReward(i, account, changes);
			}
			// If we are outside the original change set, this is a stakee account; and its stakedId cannot
			// have changed directly. Furthermore, its balance can only have changed via reward---but if so,
			// it must be staked to a node, and again staked-to-me side effects are impossible
			if (i < origN) {
				updateStakedToMeSideEffects(account, stakeChangeScenarios[i], changes, pendingChanges);
			}
			if (!rewardsActivated && pendingChanges.id(i).getAccountNum() == accountNumbers.stakingRewardAccount()) {
				newFundingBalance = finalBalanceGiven(account, changes);
			}
		}
	}

	private StakeChangeScenario scenarioFor(
			@Nullable final MerkleAccount account,
			@NotNull Map<AccountProperty, Object> changes
	) {
		setCurrentAndNewIds(account, changes);
		return StakeChangeScenario.forCase(curStakedId, newStakedId);
	}

	private void finalizeRewardBalanceChange(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var rewardsPaid = rewardCalculator.rewardsPaidInThisTxn();
		if (rewardsPaid > 0) {
			final var rewardAccountI = stakeChangeManager.findOrAdd(
					accountNumbers.stakingRewardAccount(), pendingChanges);
			updateBalance(-rewardsPaid, rewardAccountI, pendingChanges);
			// No need to update newFundingBalance because if rewardsPaid > 0, rewards
			// are already activated, and we don't need to consult newFundingBalance
		}
	}

	private void updateNodeStakes(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var scenario = stakeChangeScenarios[i];
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			setCurrentAndNewIds(account, changes);
			// Because awardStake() and withdrawStake() are very fast, we don't worry about optimizing
			// the FROM_NODE_TO_NODE case with curStakedId == newStakedId, despite how common it is
			if (scenario.withdrawsFromNode()) {
				stakeChangeManager.withdrawStake(
						-curStakedId - 1,
						account.getBalance() + account.getStakedToMe(),
						account.isDeclinedReward());
			}
			if (scenario.awardsToNode()) {
				stakeChangeManager.awardStake(
						-newStakedId - 1,
						finalBalanceGiven(account, changes) + finalStakedToMeGiven(account, changes),
						finalDeclineRewardGiven(account, changes));
			}
		}
	}

	@VisibleForTesting
	void updateStakedToMeSideEffects(
			final MerkleAccount account,
			final StakeChangeScenario scenario,
			final Map<AccountProperty, Object> changes,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		if (scenario == FROM_ACCOUNT_TO_ACCOUNT && curStakedId == newStakedId) {
			// Common case that deserves performance optimization
			final var delta = finalBalanceGiven(account, changes) - account.getBalance();
			alterStakedToMe(curStakedId, delta, pendingChanges);
		} else {
			if (scenario.withdrawsFromAccount()) {
				alterStakedToMe(curStakedId, -account.getBalance(), pendingChanges);
			}
			if (scenario.awardsToAccount()) {
				alterStakedToMe(newStakedId, finalBalanceGiven(account, changes), pendingChanges);
			}
		}
	}

	private void alterStakedToMe(
			final long accountNum,
			final long delta,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		if (delta != 0) {
			final var stakeeI = stakeChangeManager.findOrAdd(accountNum, pendingChanges);
			updateStakedToMe(stakeeI, delta, pendingChanges);
			if (!hasBeenRewarded[stakeeI]) {
				// If this stakee has already been previewed, and wasn't rewarded, we should
				// re-check if this stakedToMe change has now made it eligible for a reward
				payRewardIfRewardable(pendingChanges, stakeeI);
			}
		}
	}

	private void payRewardIfRewardable(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			final int stakeeI
	) {
		final var account = pendingChanges.entity(stakeeI);
		final var changes = pendingChanges.changes(stakeeI);
		if (isRewardable(account, changes)) {
			payReward(stakeeI, account, changes);
		}
	}

	private void payReward(
			final int accountI,
			@NotNull final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
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
	@VisibleForTesting
	boolean isRewardable(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		return account != null
				&& rewardsActivated
				&& account.getStakedId() < 0
				&& hasStakeFieldChanges(changes)
				&& stakePeriodManager.isRewardable(account.getStakePeriodStart());
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

	private void prepareAuxiliaryArraysFor(final int n) {
		// Each pending change could potentially affect stakedToMe of two accounts not yet included in the
		// change set; and if rewards were paid without 0.0.800 in the change set, it will be included too
		if (hasBeenRewarded.length < 3 * n + 1) {
			hasBeenRewarded = new boolean[3 * n + 1];
			stakeChangeScenarios = new StakeChangeScenario[3 * n + 1];
		}
		Arrays.fill(hasBeenRewarded, false);
	}

	private void setCurrentAndNewIds(
			@Nullable final MerkleAccount account,
			@NotNull Map<AccountProperty, Object> changes
	) {
		curStakedId = account == null ? 0L : account.getStakedId();
		newStakedId = (long) changes.getOrDefault(STAKED_ID, curStakedId);
	}

	/* only used for unit tests */
	@VisibleForTesting
	public void setRewardsActivated(final boolean rewardsActivated) {
		this.rewardsActivated = rewardsActivated;
	}

	@VisibleForTesting
	boolean[] getHasBeenRewarded() {
		return hasBeenRewarded;
	}

	@VisibleForTesting
	StakeChangeScenario[] getStakeChangeScenarios() {
		return stakeChangeScenarios;
	}

	@VisibleForTesting
	void setHasBeenRewarded(final boolean[] hasBeenRewarded) {
		this.hasBeenRewarded = hasBeenRewarded;
	}

	@VisibleForTesting
	void setCurStakedId(long curStakedId) {
		this.curStakedId = curStakedId;
	}

	@VisibleForTesting
	void setNewStakedId(long newStakedId) {
		this.newStakedId = newStakedId;
	}
}
