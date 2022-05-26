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

import javax.inject.Inject;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.StakeChangeManager.finalBalanceGiven;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.STAKE_PERIOD_START;

public class RewardCalculator {
	private final StakePeriodManager stakePeriodManager;
	private final StakeInfoManager stakeInfoManager;

	private long rewardsPaid;
	long accountReward;
	long accountUpdatedStakePeriodStart;

	@Inject
	public RewardCalculator(final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager) {
		this.stakePeriodManager = stakePeriodManager;
		this.stakeInfoManager = stakeInfoManager;
	}

	public final void computePendingRewards(final MerkleAccount account) {
		final var currentPeriod = stakePeriodManager.currentStakePeriod();

		// Staking rewards only accumulate for a finite # of periods (currently 365 days), so get the effective start
		var effectiveStart = stakePeriodManager.effectivePeriod(account.getStakePeriodStart());

		// Check if effectiveStart is within the range for receiving rewards
		if (stakePeriodManager.isRewardable(effectiveStart)) {
			this.accountReward = computeReward(account, currentPeriod, effectiveStart);
			// After we've got our rewards till the last full period, it becomes our effective start
			effectiveStart = currentPeriod - 1;
		} else {
			this.accountReward = 0;
		}

		this.accountUpdatedStakePeriodStart = effectiveStart;
	}

	long computeReward(final MerkleAccount account, final long currentStakePeriod, final long effectiveStart) {
		final long stakedNode = account.getStakedId();
		final var stakedNodeAccount = stakeInfoManager.mutableStakeInfoFor(stakedNode);
		final var rewardSumHistory = stakedNodeAccount.getRewardSumHistory();

		// stakedNode.rewardSumHistory[0] is the reward for all days up to and including the full day
		// currentStakePeriod - 1, since today is not finished yet.
		return account.isDeclinedReward() ? 0 :
				account.getBalance() * (rewardSumHistory[0] - rewardSumHistory[(int) (currentStakePeriod - 1 - (effectiveStart - 1))]);
	}

	public void updateRewardChanges(final MerkleAccount account, final Map<AccountProperty, Object> changes) {
		computePendingRewards(account);

		var balance = finalBalanceGiven(account, changes);

		changes.put(BALANCE, balance + accountReward);
		changes.put(STAKE_PERIOD_START, accountUpdatedStakePeriodStart);
		rewardsPaid += accountReward; // used for adding balance change for 0.0.800
	}

	public void resetRewardsPaid() {
		rewardsPaid = 0L;
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
}
