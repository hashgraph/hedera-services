/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.ledger.accounts.staking;

import static com.hedera.services.context.properties.PropertyNames.STAKING_PERIOD_MINS;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.NA;
import static com.hedera.services.utils.Units.MINUTES_TO_MILLISECONDS;
import static com.hedera.services.utils.Units.MINUTES_TO_SECONDS;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StakePeriodManager {
    public static final ZoneId ZONE_UTC = ZoneId.of("UTC");
    public static final long DEFAULT_STAKING_PERIOD_MINS = 1440L;

    private final int numStoredPeriods;
    private final long stakingPeriodMins;
    private final TransactionContext txnCtx;
    private final Supplier<MerkleNetworkContext> networkCtx;

    private long currentStakePeriod;
    private long prevConsensusSecs;

    @Inject
    public StakePeriodManager(
            final TransactionContext txnCtx,
            final Supplier<MerkleNetworkContext> networkCtx,
            final @CompositeProps PropertySource properties) {
        this.txnCtx = txnCtx;
        this.networkCtx = networkCtx;
        this.numStoredPeriods =
                properties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS);
        this.stakingPeriodMins = properties.getLongProperty(STAKING_PERIOD_MINS);
    }

    public long epochSecondAtStartOfPeriod(final long stakePeriod) {
        if (stakingPeriodMins == DEFAULT_STAKING_PERIOD_MINS) {
            return LocalDate.ofEpochDay(stakePeriod).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        } else {
            return stakePeriod * stakingPeriodMins * MINUTES_TO_SECONDS;
        }
    }

    public long currentStakePeriod() {
        final var now = txnCtx.consensusTime();
        final var currentConsensusSecs = now.getEpochSecond();
        if (prevConsensusSecs != currentConsensusSecs) {
            if (stakingPeriodMins == DEFAULT_STAKING_PERIOD_MINS) {
                currentStakePeriod =
                        LocalDate.ofInstant(txnCtx.consensusTime(), ZONE_UTC).toEpochDay();
            } else {
                currentStakePeriod = getPeriod(now, stakingPeriodMins * MINUTES_TO_MILLISECONDS);
            }
            prevConsensusSecs = currentConsensusSecs;
        }
        return currentStakePeriod;
    }

    public long estimatedCurrentStakePeriod() {
        final var now = Instant.now();
        if (stakingPeriodMins == DEFAULT_STAKING_PERIOD_MINS) {
            return LocalDate.ofInstant(now, ZONE_UTC).toEpochDay();
        } else {
            return getPeriod(now, stakingPeriodMins * MINUTES_TO_MILLISECONDS);
        }
    }

    public long firstNonRewardableStakePeriod() {
        // The earliest period by which an account can have started staking, _without_ becoming
        // eligible for a reward; if staking is not active, this will return Long.MIN_VALUE so
        // no account can ever be eligible.
        return networkCtx.get().areRewardsActivated() ? currentStakePeriod() - 1 : Long.MIN_VALUE;
    }

    public boolean isRewardable(final long stakePeriodStart) {
        return stakePeriodStart > -1 && stakePeriodStart < firstNonRewardableStakePeriod();
    }

    public long effectivePeriod(final long stakePeriodStart) {
        if (stakePeriodStart > -1 && stakePeriodStart < currentStakePeriod - numStoredPeriods) {
            return currentStakePeriod - numStoredPeriods;
        }
        return stakePeriodStart;
    }

    /**
     * Given the current and new staked ids for an account, as well as if it received a reward in
     * this transaction, returns the new {@code stakePeriodStart} for this account:
     *
     * <ol>
     *   <li>{@link StakingUtils#NA} if the {@code stakePeriodStart} doesn't need to change; or,
     *   <li>The value to which the {@code stakePeriodStart} should be changed.
     * </ol>
     *
     * @param curStakedId the id the account was staked to at the beginning of the transaction
     * @param newStakedId the id the account was staked to at the end of the transaction
     * @param rewarded whether the account was rewarded during the transaction
     * @param stakeMetaChanged whether the account's stake metadata changed
     * @return either NA for no new stakePeriodStart, or the new value
     */
    public long startUpdateFor(
            final long curStakedId,
            final long newStakedId,
            final boolean rewarded,
            final boolean stakeMetaChanged) {
        // Only worthwhile to update stakedPeriodStart for an account staking to a node
        if (newStakedId < 0) {
            if (curStakedId >= 0 || stakeMetaChanged) {
                // We just started staking to a node today
                return currentStakePeriod();
            } else if (rewarded) {
                // If we were just rewarded, stake period start is yesterday
                return currentStakePeriod() - 1;
            }
        }
        return NA;
    }

    @VisibleForTesting
    long getPrevConsensusSecs() {
        return prevConsensusSecs;
    }
}
