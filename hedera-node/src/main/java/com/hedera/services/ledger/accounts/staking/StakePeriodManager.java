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
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleNetworkContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;

import static com.hedera.services.utils.Units.SECS_IN_MINUTE;

@Singleton
public class StakePeriodManager {
	private final TransactionContext txnCtx;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final PropertySource properties;
	private long currentStakePeriod;
	private long prevConsensusSecs;

	public static final ZoneId ZONE_UTC = ZoneId.of("UTC");
	private static final long DEFAULT_STAKING_PERIOD_MINS = 1440L;

	@Inject
	public StakePeriodManager(final TransactionContext txnCtx,
			final Supplier<MerkleNetworkContext> networkCtx,
			final @CompositeProps PropertySource properties) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
		this.properties = properties;
	}

	public long currentStakePeriod() {
		final var currentConsensusSecs = txnCtx.consensusTime().getEpochSecond();

		if (prevConsensusSecs != currentConsensusSecs) {
			prevConsensusSecs = currentConsensusSecs;

			final long stakingPeriod = properties.getLongProperty("staking.periodMins");
			if (stakingPeriod != DEFAULT_STAKING_PERIOD_MINS) {
				currentStakePeriod = currentConsensusSecs / (stakingPeriod * SECS_IN_MINUTE);
			} else {
				currentStakePeriod = LocalDate.ofInstant(txnCtx.consensusTime(), ZONE_UTC).toEpochDay();
			}
		}
		return currentStakePeriod;
	}

	public long firstNonRewardableStakePeriod() {
		// The latest period by which an account must have started staking, if it can be eligible for a
		// reward; if staking is not active, this will return Long.MIN_VALUE so no account is eligible
		return networkCtx.get().areRewardsActivated() ? currentStakePeriod() - 1 : Long.MIN_VALUE;
	}

	public boolean isRewardable(final long stakePeriodStart) {
		// firstNonRewardableStakePeriod is currentStakePeriod -1.
		// if stakePeriodStart = -1 then it is not staked or staked to an account
		// If it equals currentStakePeriod, that means the staking changed today (later than the start of today),
		// so it had no effect on consensus weights today, and should never be rewarded for helping consensus
		// throughout today.  If it equals currentStakePeriod-1, that means it either started yesterday or has already been
		// rewarded for yesterday. Either way, it might be rewarded for today after today ends, but shouldn't yet be
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

	@VisibleForTesting
	public long getPrevConsensusSecs() {
		return prevConsensusSecs;
	}
}
