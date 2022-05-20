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
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.ledger.interceptors.StakeAwareAccountsCommitsInterceptor.finalBalanceGiven;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.STAKE_PERIOD_START;

public class RewardCalculator {
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private long rewardsPaid;

	public static final ZoneId zoneUTC = ZoneId.of("UTC");

	@Inject
	public RewardCalculator(
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo,
			final Supplier<MerkleNetworkContext> networkCtx) {
		this.stakingInfo = stakingInfo;
		this.networkCtx = networkCtx;
	}

	public final Pair<Long, Long> computeRewards(final MerkleAccount account) {
		long todayNumber = LocalDate.now(zoneUTC).toEpochDay();
		var stakePeriodStart = account.getStakePeriodStart();

		if (stakePeriodStart > -1 && stakePeriodStart < todayNumber - 365) {
			stakePeriodStart = todayNumber - 365;
		}

		if (isWithinRange(stakePeriodStart, todayNumber)) {
			final long reward = computeReward(account, account.getStakedId(), todayNumber, stakePeriodStart);
			stakePeriodStart = todayNumber - 1;
			return Pair.of(reward, stakePeriodStart);
		}
		return Pair.of(0L, stakePeriodStart);
	}

	boolean isWithinRange(final long stakePeriodStart, final long todayNumber) {
		// if stakePeriodStart = -1 then it is not staked or staked to an account
		// If it equals todayNumber, that means the staking changed today (later than the start of today),
		// so it had no effect on consensus weights today, and should never be rewarded for helping consensus
		// throughout today.  If it equals todayNumber-1, that means it either started yesterday or has already been
		// rewarded for yesterday. Either way, it might be rewarded for today after today ends, but shouldn't yet be
		// rewarded for today, because today hasn't finished yet.

		return stakePeriodStart > -1 && stakePeriodStart < todayNumber - 1;
	}

	long computeReward(final MerkleAccount account, final long stakedNode, final long todayNumber,
			final long stakePeriodStart) {
		final var stakedNodeAccount = stakingInfo.get().get(EntityNum.fromLong(stakedNode));
		final var rewardSumHistory = stakedNodeAccount.getRewardSumHistory();

		// stakedNode.rewardSumHistory[0] is the reward for all days up to and including the full day todayNumber - 1,
		// since today is not finished yet.
		return account.isDeclinedReward() ? 0 :
				account.getBalance() * (rewardSumHistory[0] - rewardSumHistory[(int) (todayNumber - 1 - (stakePeriodStart - 1))]);
	}

	public long latestRewardableStakePeriodStart() {
		return networkCtx.get().areRewardsActivated() ? LocalDate.now(zoneUTC).toEpochDay() - 1 : Long.MIN_VALUE;
	}

	public long updateRewardChanges(final MerkleAccount account, final Map<AccountProperty, Object> changes) {
		final var result = computeRewards(account);

		var balance = finalBalanceGiven(account, changes);
		final var reward = result.getLeft();
		final var stakePeriodStart = result.getRight();

		changes.put(BALANCE, balance + reward);
		changes.put(STAKE_PERIOD_START, stakePeriodStart);
		rewardsPaid += reward; // used for adding balance change for 0.0.800
		return reward;
	}

	public void reset() {
		rewardsPaid = 0L;
	}

	public long rewardsPaidInThisTxn() {
		return rewardsPaid;
	}
}
