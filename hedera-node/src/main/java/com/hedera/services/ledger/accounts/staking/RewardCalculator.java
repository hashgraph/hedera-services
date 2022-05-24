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

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;

import javax.inject.Inject;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.StakeChangeManager.finalBalanceGiven;
import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.isWithinRange;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.STAKE_PERIOD_START;

public class RewardCalculator {
	private final StakePeriodManager stakePeriodManager;
	private final StakingInfoManager stakingInfoManager;

	private long rewardsPaid;
	long accountReward;
	long accountUpdatedStakePeriodStart;

	@Inject
	public RewardCalculator(final StakePeriodManager stakePeriodManager,
			final StakingInfoManager stakingInfoManager) {
		this.stakePeriodManager = stakePeriodManager;
		this.stakingInfoManager = stakingInfoManager;
	}

	public final void computeRewards(final MerkleAccount account) {
		final long todayNumber = stakePeriodManager.currentStakePeriod();
		var stakePeriodStart = account.getStakePeriodStart();

		if (stakePeriodStart > -1 && stakePeriodStart < todayNumber - 365) {
			stakePeriodStart = todayNumber - 365;
		}

		if (isWithinRange(stakePeriodStart, todayNumber)) {
			final long reward = computeReward(account, account.getStakedId(), todayNumber, stakePeriodStart);
			stakePeriodStart = todayNumber - 1;
			this.accountReward = reward;
		} else {
			this.accountReward = 0L;
		}
		this.accountUpdatedStakePeriodStart = stakePeriodStart;
	}

	long computeReward(final MerkleAccount account, final long stakedNode, final long todayNumber,
			final long stakePeriodStart) {
		final var stakedNodeAccount = stakingInfoManager.mutableStakeInfoFor(stakedNode);
		final var rewardSumHistory = stakedNodeAccount.getRewardSumHistory();

		// stakedNode.rewardSumHistory[0] is the reward for all days up to and including the full day todayNumber - 1,
		// since today is not finished yet.
		return account.isDeclinedReward() ? 0 :
				account.getBalance() * (rewardSumHistory[0] - rewardSumHistory[(int) (todayNumber - 1 - (stakePeriodStart - 1))]);
	}

	public void updateRewardChanges(final MerkleAccount account, final Map<AccountProperty, Object> changes) {
		computeRewards(account);

		var balance = finalBalanceGiven(account, changes);

		changes.put(BALANCE, balance + accountReward);
		changes.put(STAKE_PERIOD_START, accountUpdatedStakePeriodStart);
		rewardsPaid += accountReward; // used for adding balance change for 0.0.800
	}

	public void reset() {
		rewardsPaid = 0L;
	}

	public long rewardsPaidInThisTxn() {
		return rewardsPaid;
	}

	public long getAccountReward() {
		return accountReward;
	}

	public long getAccountUpdatedStakePeriodStart() {
		return accountUpdatedStakePeriodStart;
	}
}
