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
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;

@Singleton
public class RewardCalculator {
	private final StakeInfoManager stakeInfoManager;
	private final StakePeriodManager stakePeriodManager;

	private long rewardsPaid;

	@Inject
	public RewardCalculator(final StakePeriodManager stakePeriodManager, final StakeInfoManager stakeInfoManager) {
		this.stakePeriodManager = stakePeriodManager;
		this.stakeInfoManager = stakeInfoManager;
	}

	public void reset() {
		rewardsPaid = 0;
	}

	public long computeAndApplyReward(final MerkleAccount account, final Map<AccountProperty, Object> changes) {
		final var reward = computePendingRewards(account);
		if (reward > 0) {
			final var balance = finalBalanceGiven(account, changes);
			changes.put(BALANCE, balance + reward);
		}
		rewardsPaid += reward;
		return reward;
	}

	public long rewardsPaidInThisTxn() {
		return rewardsPaid;
	}

	public long estimatePendingRewards(final MerkleAccount account, final MerkleStakingInfo nodeStakingInfo) {
		return computeRewardFromDetails(
				account,
				nodeStakingInfo,
				stakePeriodManager.estimatedCurrentStakePeriod(),
				stakePeriodManager.effectivePeriod(account.getStakePeriodStart()));
	}

	private long computePendingRewards(final MerkleAccount account) {
		return computeRewardFromDetails(
				account,
				stakeInfoManager.mutableStakeInfoFor(account.getStakedNodeAddressBookId()),
				stakePeriodManager.currentStakePeriod(),
				stakePeriodManager.effectivePeriod(account.getStakePeriodStart()));
	}

	private long computeRewardFromDetails(
			final MerkleAccount account,
			final MerkleStakingInfo nodeStakingInfo,
			final long currentStakePeriod,
			final long effectiveStart
	) {
		if (!stakePeriodManager.isRewardable(effectiveStart)) {
			return 0L;
		}
		final var rewardSumHistory = nodeStakingInfo.getRewardSumHistory();

		// stakedNode.rewardSumHistory[0] is the reward for all periods up to and including the full staking period
		// (currentStakePeriod - 1); since this period is not finished yet, we do not know how to reward for it
		return account.isDeclinedReward() ? 0 :
				(account.getBalance() / HBARS_TO_TINYBARS)
						* (rewardSumHistory[0] - rewardSumHistory[(int) (currentStakePeriod - 1 - (effectiveStart - 1))]);
	}

	@VisibleForTesting
	public void setRewardsPaidInThisTxn(long rewards) {
		rewardsPaid = rewards;
	}
}
