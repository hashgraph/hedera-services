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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Singleton
public class StakePeriodManager {
	private final TransactionContext txnCtx;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private long currentStakePeriod;
	private long prevConsensusSecs;

	public static final ZoneId zoneUTC = ZoneId.of("UTC");

	@Inject
	public StakePeriodManager(final TransactionContext txnCtx, final Supplier<MerkleNetworkContext> networkCtx) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
	}

	@Nullable
	public Consumer<MerkleAccount> finisherFor(
			final long curStakedId,
			final long newStakedId,
			final long stakedToMeUpdate,
			final boolean rewarded
	) {
		final long stakePeriodStartUpdate = stakePeriodStartUpdateFor(curStakedId, newStakedId, rewarded);
		if (stakedToMeUpdate == -1 && stakePeriodStartUpdate == -1) {
			return null;
		} else {
			return account -> {
				if (stakePeriodStartUpdate != -1) {
					account.setStakePeriodStart(stakePeriodStartUpdate);
				}
				if (stakedToMeUpdate != -1) {
					account.setStakedToMe(stakedToMeUpdate);
				}
			};
		}
	}

	public long currentStakePeriod() {
		final var currentConsensusSecs = txnCtx.consensusTime().getEpochSecond();
		if (prevConsensusSecs != currentConsensusSecs) {
			prevConsensusSecs = currentConsensusSecs;
			currentStakePeriod = LocalDate.ofInstant(txnCtx.consensusTime(), zoneUTC).toEpochDay();
		}
		return currentStakePeriod;
	}

	public long estimatedCurrentStakePeriod() {
		return currentStakePeriod;
	}

	public long firstNonRewardableStakePeriod() {
		// The earliest period by which an account can have started staking, _without_ becoming
		// eligible for a reward; if staking is not active, this will return Long.MIN_VALUE so
		// no account can ever be eligible
		return networkCtx.get().areRewardsActivated() ? currentStakePeriod() - 1 : Long.MIN_VALUE;
	}

	public boolean isRewardable(final long stakePeriodStart) {
		// firstNonRewardableStakePeriod is currentStakePeriod -1.
		// if stakePeriodStart = -1 then it is not staked or staked to an account
		// If it equals currentStakePeriod, that means the staking changed today (later than the start of today),
		// so it had no effect on consensus weights today, and should never be rewarded for helping consensus
		// throughout today.  If it equals currentStakePeriod-1, that means it either started yesterday or has already
		// been rewarded for yesterday. Either way, it might be rewarded for today after today ends, but shouldn't yet be
		// rewarded for today, because today hasn't finished yet.
		return stakePeriodStart > -1 && stakePeriodStart < firstNonRewardableStakePeriod();
	}

	public long effectivePeriod(final long stakePeriodStart) {
		// currentStakePeriod will be already set before calling this method in RewardCalculator
		if (stakePeriodStart > -1 && stakePeriodStart < currentStakePeriod - 365) {
			return currentStakePeriod - 365;
		}
		return stakePeriodStart;
	}

	private long stakePeriodStartUpdateFor(
			final long curStakedId,
			final long newStakedId,
			final boolean rewarded
	) {
		// There's no reason to update stakedPeriodStart for an account not staking to
		// a node; the value will never be used, since it cannot be eligible for a reward
		if (newStakedId < 0) {
			if (curStakedId >= 0) {
				// We just started staking to a node
				return currentStakePeriod();
			} else {
				// If we were just rewarded, stake period start is yesterday; otherwise, unchanged
				return !rewarded ? -1 : currentStakePeriod() - 1;
			}
		} else {
			return -1;
		}
	}

	@VisibleForTesting
	long getPrevConsensusSecs() {
		return prevConsensusSecs;
	}
}
