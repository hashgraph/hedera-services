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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
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
	public RewardCalculator(
			final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager
	) {
		this.stakePeriodManager = stakePeriodManager;
		this.stakeInfoManager = stakeInfoManager;
	}

	public void reset() {
		rewardsPaid = 0;
	}

	public long computePendingReward(final MerkleAccount account) {
		final var rewardOffered = computeRewardFromDetails(
				account,
				stakeInfoManager.mutableStakeInfoFor(account.getStakedNodeAddressBookId()),
				stakePeriodManager.currentStakePeriod(),
				stakePeriodManager.effectivePeriod(account.getStakePeriodStart()));
		return account.isDeclinedReward() ? 0 : rewardOffered;
	}

	public boolean applyReward(
			final long reward,
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (reward > 0) {
			final var isDeclined = (account != null)
					? account.isDeclinedReward()
					: (boolean) changes.getOrDefault(AccountProperty.DECLINE_REWARD, false);
			if (isDeclined) {
				return false;
			}
			final var balance = finalBalanceGiven(account, changes);
			changes.put(BALANCE, balance + reward);
			rewardsPaid += reward;
		}
		return true;
	}

	public long rewardsPaidInThisTxn() {
		return rewardsPaid;
	}

	public long estimatePendingRewards(final MerkleAccount account, final MerkleStakingInfo nodeStakingInfo) {
		final var rewardOffered = computeRewardFromDetails(
				account,
				nodeStakingInfo,
				stakePeriodManager.estimatedCurrentStakePeriod(),
				stakePeriodManager.effectivePeriod(account.getStakePeriodStart()));
		return account.isDeclinedReward() ? 0 : rewardOffered;
	}

	public long epochSecondAtStartOfPeriod(final long stakePeriod) {
		return stakePeriodManager.epochSecondAtStartOfPeriod(stakePeriod);
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
		System.out.println("  * Subtracted rewardSumHistory[" +
				(int) (currentStakePeriod - 1 - (effectiveStart)) + "]=" +
				rewardSumHistory[(int) (currentStakePeriod - 1 - (effectiveStart))]);
		// Recall that rewardSumHistory[0] is the cumulative reward rate for all periods up to and including
		// [currentStakePeriod - 1]; since the current period is not finished, we do not know how to reward for it.
		// Now suppose we call currentStakePeriod "today", and Alice's effectiveStart is [currentStakePeriod - 2]
		// (two days ago), so we should reward Alice for exactly [currentStakePeriod - 1] (yesterday). This reward
		// rate is the difference in the cumulative rates between yesterday and two days ago; that is,
		// 		rate = rewardSumHistory[0] - rewardSumHistory[1]
		// This is equivalent to,
		//      rate = rewardSumHistory[0] - rewardSumHistory[(currentStakePeriod - 1) - effectiveStart]
		// because,
		//      (currentStakePeriod - 1) - effectiveStart = (currentStakePeriod - 1) - (currentStakePeriod - 2)
		//                                                = -1 + 2
		//                                                = 1
		// It follows by induction the same difference is correct for all earlier values of effectiveStart.
		return rewardFor(account, rewardSumHistory, currentStakePeriod, effectiveStart);
	}

	private long rewardFor(
			final MerkleAccount account,
			final long[] rewardSumHistory,
			final long currentStakePeriod,
			final long effectiveStart) {
		final var rewardFrom = (int) (currentStakePeriod - 1 - effectiveStart);

		if (rewardFrom == 0) {
			return 0;
		}

		if (account.getStakeAtStartOfLastRewardedPeriod() != -1) {
			return ((account.getBalance() + account.getStakedToMe()) / HBARS_TO_TINYBARS)
					* (rewardSumHistory[0] - rewardSumHistory[rewardFrom - 1]) +
					((account.getStakeAtStartOfLastRewardedPeriod()) / HBARS_TO_TINYBARS)
							* (rewardSumHistory[rewardFrom - 1] - rewardSumHistory[rewardFrom]);
		} else {
			return ((account.getBalance() + account.getStakedToMe()) / HBARS_TO_TINYBARS)
					* (rewardSumHistory[0] - rewardSumHistory[rewardFrom]);
		}
	}

	@VisibleForTesting
	public void setRewardsPaidInThisTxn(long rewards) {
		rewardsPaid = rewards;
	}
}
