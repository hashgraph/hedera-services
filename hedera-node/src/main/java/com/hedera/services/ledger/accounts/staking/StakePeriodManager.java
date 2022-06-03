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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.utils.Units.SECS_IN_MINUTE;

@Singleton
public class StakePeriodManager {
	private final TransactionContext txnCtx;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private long currentStakePeriod;
	private long prevConsensusSecs;
	final long stakingPeriod;
	public static final ZoneId ZONE_UTC = ZoneId.of("UTC");
	public static final long DEFAULT_STAKING_PERIOD_MINS = 1440L;

	@Inject
	public StakePeriodManager(final TransactionContext txnCtx,
			final Supplier<MerkleNetworkContext> networkCtx,
			final @CompositeProps PropertySource properties
	) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
		this.stakingPeriod = properties.getLongProperty("staking.periodMins");
	}

	@Nullable
	public Consumer<MerkleAccount> finisherFor(
			final long curStakedId,
			final long newStakedId,
			final long stakedToMeUpdate,
			final boolean rewarded,
			boolean stakeMetaChanged
	) {
		final long stakePeriodStartUpdate = stakePeriodStartUpdateFor(
				curStakedId, newStakedId, rewarded, stakeMetaChanged);
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
			if (stakingPeriod != DEFAULT_STAKING_PERIOD_MINS) {
				currentStakePeriod = currentConsensusSecs / (stakingPeriod * SECS_IN_MINUTE);
			} else {
				currentStakePeriod = LocalDate.ofInstant(txnCtx.consensusTime(), ZONE_UTC).toEpochDay();
			}
		}
		return currentStakePeriod;
	}

	public long estimatedCurrentStakePeriod() {
		if (stakingPeriod != DEFAULT_STAKING_PERIOD_MINS) {
			final var thisSecond = Instant.now().getEpochSecond();
			return thisSecond / (stakingPeriod * SECS_IN_MINUTE);
		} else {
			return LocalDate.ofInstant(Instant.now(), ZONE_UTC).toEpochDay();
		}
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

	/**
	 * Given the current and new staked ids for an account, as well as if it received a reward in
	 * this transaction, returns the new {@code stakePeriodStart} for this account:
	 * <ol>
	 *     <li>{@code -1} if the {@code stakePeriodStart} doesn't need to change; or,</li>
	 *     <li>The value to which the {@code stakePeriodStart} should be changed.</li>
	 * </ol>
	 *
	 * @param curStakedId
	 * 		the id the account was staked to at the beginning of the transaction
	 * @param newStakedId
	 * 		the id the account was staked to at the end of the transaction
	 * @param rewarded
	 * 		whether the account was rewarded during the transaction
	 * @param stakeMetaChanged
	 * @return either -1 for no new stakePeriodStart, or the new value
	 */
	private long stakePeriodStartUpdateFor(
			final long curStakedId,
			final long newStakedId,
			final boolean rewarded,
			boolean stakeMetaChanged
	) {
		// There's no reason to update stakedPeriodStart for an account not staking to
		// a node; the value will never be used, since it cannot be eligible for a reward
		if (newStakedId < 0) {
			if (curStakedId >= 0) {
				// We just started staking to a node today
				return currentStakePeriod();
			} else {
				// If we were just rewarded, stake period start is yesterday
				if (rewarded) {
					return currentStakePeriod() - 1;
				} else {
					if (stakeMetaChanged) {
						return currentStakePeriod();
					}
					return -1;
				}
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
