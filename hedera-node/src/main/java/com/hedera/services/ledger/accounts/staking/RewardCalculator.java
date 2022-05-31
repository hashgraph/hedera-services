package com.hedera.services.ledger.accounts.staking;

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
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalBalanceGiven;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.STAKE_PERIOD_START;
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;
@Singleton
public class RewardCalculator {
	private final StakePeriodManager stakePeriodManager;
	private final StakeInfoManager stakeInfoManager;

	private long rewardsPaid;
	private long accountReward;
	private long accountUpdatedStakePeriodStart;

	@Inject
	public RewardCalculator(final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager) {
		this.stakePeriodManager = stakePeriodManager;
		this.stakeInfoManager = stakeInfoManager;
	}

	public void updateRewardChanges(final MerkleAccount account, final Map<AccountProperty, Object> changes) {
		computePendingRewards(account);

		if (accountReward > 0) {
			final var balance = finalBalanceGiven(account, changes);
			changes.put(BALANCE, balance + accountReward);
		}

		changes.put(STAKE_PERIOD_START, accountUpdatedStakePeriodStart);
		rewardsPaid += accountReward; // used for adding balance change for 0.0.800
	}

	public long estimatePendingRewards(final MerkleAccount account, final MerkleStakingInfo stakingNode) {
		return computeRewardFromDetails(
				account,
				stakingNode,
				stakePeriodManager.currentStakePeriod(),
				stakePeriodManager.effectivePeriod(account.getStakePeriodStart()));
	}

	private final void computePendingRewards(final MerkleAccount account) {
		final var currentPeriod = stakePeriodManager.currentStakePeriod();

		// Staking rewards only accumulate for a finite # of periods (currently 365 days), so get the effective start
		var effectiveStart = stakePeriodManager.effectivePeriod(account.getStakePeriodStart());

		// Check if effectiveStart is within the range for receiving rewards
		if (stakePeriodManager.isRewardable(effectiveStart)) {
			final long stakedNode = account.getStakedNodeAddressBookId();
			final var stakedNodeAccount = stakeInfoManager.mutableStakeInfoFor(stakedNode);

			this.accountReward = computeRewardFromDetails(account, stakedNodeAccount, currentPeriod, effectiveStart);
			// After we've got our rewards till the last full period, it becomes our effective start
			effectiveStart = currentPeriod - 1;
		} else {
			this.accountReward = 0;
		}

		this.accountUpdatedStakePeriodStart = effectiveStart;
	}

	private long computeRewardFromDetails(
			final MerkleAccount account,
			final MerkleStakingInfo stakedNodeAccount,
			final long currentStakePeriod,
			final long effectiveStart) {
		final var rewardSumHistory = stakedNodeAccount.getRewardSumHistory();

		// stakedNode.rewardSumHistory[0] is the reward for all days up to and including the full day
		// currentStakePeriod - 1, since today is not finished yet.
		return account.isDeclinedReward() ? 0 :
				(account.getBalance() / HBARS_TO_TINYBARS) * (rewardSumHistory[0] - rewardSumHistory[(int) (currentStakePeriod - 1 - (effectiveStart - 1))]);
	}

	public void reset() {
		rewardsPaid = 0;
		accountReward = 0;
		accountUpdatedStakePeriodStart = 0;
	}

	public long rewardsPaidInThisTxn() {
		return rewardsPaid;
	}

	public long getAccountReward() {
		return accountReward;
	}

	@VisibleForTesting
	public long getAccountUpdatedStakePeriodStart() {
		return accountUpdatedStakePeriodStart;
	}

	@VisibleForTesting
	public void setRewardsPaidInThisTxn(long rewards) {
		rewardsPaid = rewards;
	}
}
