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


import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleNetworkContext;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;

public class StakePeriodManager {
	private final TransactionContext txnCtx;
	private final Supplier<MerkleNetworkContext> networkCtx;

	public static final ZoneId zoneUTC = ZoneId.of("UTC");

	@Inject
	public StakePeriodManager(final TransactionContext txnCtx,
			final Supplier<MerkleNetworkContext> networkCtx) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
	}

	public long currentStakePeriod() {
		return LocalDate.ofInstant(txnCtx.consensusTime(), zoneUTC).toEpochDay(); // optimization ?
	}

	public long latestRewardableStakePeriodStart() {
		return networkCtx.get().areRewardsActivated() ? currentStakePeriod() - 1 : Long.MIN_VALUE;
	}

	public static boolean isWithinRange(final long stakePeriodStart, final long latestRewardableStakePeriodStart) {
		// latestRewardableStakePeriodStart is todayNumber -1.
		// if stakePeriodStart = -1 then it is not staked or staked to an account
		// If it equals todayNumber, that means the staking changed today (later than the start of today),
		// so it had no effect on consensus weights today, and should never be rewarded for helping consensus
		// throughout today.  If it equals todayNumber-1, that means it either started yesterday or has already been
		// rewarded for yesterday. Either way, it might be rewarded for today after today ends, but shouldn't yet be
		// rewarded for today, because today hasn't finished yet.
		return stakePeriodStart > -1 && stakePeriodStart < latestRewardableStakePeriodStart;
	}
}
