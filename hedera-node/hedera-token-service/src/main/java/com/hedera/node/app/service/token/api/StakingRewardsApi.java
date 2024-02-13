/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.api;

import static com.hedera.node.app.service.token.Units.HBARS_TO_TINYBARS;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Methods for computing an account's pending staking rewards.
 */
public interface StakingRewardsApi {
    Logger log = LogManager.getLogger(StakingRewardsApi.class);
    int MINUTES_TO_SECONDS = 60;
    long MINUTES_TO_MILLISECONDS = 60_000L;
    long DAILY_STAKING_PERIOD_MINS = 1440L;
    ZoneId ZONE_UTC = ZoneId.of("UTC");

    /**
     * Assuming the given account began staking to a node with the given (possibly missing) staking information
     * in a particular period, computes the rewards for the account has earned up to a provided current stake period.
     *
     * @param account the account
     * @param nodeStakingInfo the staking information of the node
     * @param currentStakePeriod the current stake period
     * @param stakePeriodStart the effective period in which the account began staking
     * @return the pending rewards for the account
     */
    static long computeRewardFromDetails(
            @NonNull final Account account,
            @Nullable final StakingNodeInfo nodeStakingInfo,
            final long currentStakePeriod,
            final long stakePeriodStart) {
        requireNonNull(account);
        if (nodeStakingInfo == null || nodeStakingInfo.deleted()) {
            return 0L;
        }
        final var rewardSumHistory = nodeStakingInfo.rewardSumHistory();
        return rewardFor(account, rewardSumHistory, currentStakePeriod, stakePeriodStart);
    }

    /**
     * Estimate the pending rewards for the given account with the given network conditions.
     *
     * @param numStoredPeriods the number of periods being stored
     * @param stakePeriodMins the length of a stake period in minutes
     * @param areRewardsActive whether or not rewards are active
     * @param account the account for which the pending rewards are to be calculated
     * @param readableStakingInfoStore the store from which the staking info of the node is to be retrieved
     * @return the pending rewards for the account
     */
    static long estimatePendingReward(
            final int numStoredPeriods,
            final long stakePeriodMins,
            final boolean areRewardsActive,
            @NonNull final Account account,
            @NonNull final ReadableStakingInfoStore readableStakingInfoStore) {
        if (account.hasStakedNodeId() && !account.declineReward()) {
            final var currentStakePeriod = estimatedCurrentStakePeriod(stakePeriodMins);
            final var clampedStakePeriodStart =
                    clampedStakePeriodStart(account.stakePeriodStart(), currentStakePeriod, numStoredPeriods);
            if (isEstimatedRewardable(stakePeriodMins, clampedStakePeriodStart, areRewardsActive)) {
                return computeRewardFromDetails(
                        account,
                        readableStakingInfoStore.get(account.stakedNodeIdOrThrow()),
                        currentStakePeriod,
                        clampedStakePeriodStart);
            }
        }
        return 0;
    }

    /**
     * Returns the if the given stake period start is rewardable or not.
     *
     * @param stakePeriodMins the length of a stake period in minutes
     * @param stakePeriodStart the stake period start
     * @param areRewardsActive whether or not rewards are active
     * @return true if the given stake period start is rewardable, false otherwise
     */
    static boolean isEstimatedRewardable(
            final long stakePeriodMins, final long stakePeriodStart, final boolean areRewardsActive) {
        return stakePeriodStart > -1
                && stakePeriodStart < estimatedFirstNonRewardableStakePeriod(stakePeriodMins, areRewardsActive);
    }

    /**
     * Gives the estimated current stake period.
     *
     * @param stakingPeriodMins the length of a stake period in minutes
     * @return the estimated current stake period
     */
    static long estimatedCurrentStakePeriod(final long stakingPeriodMins) {
        return stakePeriodAt(Instant.now(), stakingPeriodMins);
    }

    /**
     * Gives the stake period at the given instant.
     *
     * @param then the instant
     * @param stakePeriodMins the length of a stake period in minutes
     * @return the stake period at the given instant
     */
    static long stakePeriodAt(@NonNull final Instant then, final long stakePeriodMins) {
        if (stakePeriodMins == DAILY_STAKING_PERIOD_MINS) {
            return LocalDate.ofInstant(then, ZONE_UTC).toEpochDay();
        } else {
            return getPeriod(then, stakePeriodMins * MINUTES_TO_MILLISECONDS);
        }
    }

    /**
     * Gives the epoch second at the start of the given stake period.
     *
     * @param stakePeriod the stake period
     * @param stakePeriodMins the length of a stake period in minutes
     * @return the epoch second at the start of the given stake period
     */
    static long epochSecondAtStartOfPeriod(final long stakePeriod, final long stakePeriodMins) {
        if (stakePeriodMins == DAILY_STAKING_PERIOD_MINS) {
            return LocalDate.ofEpochDay(stakePeriod).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        } else {
            return stakePeriod * stakePeriodMins * MINUTES_TO_SECONDS;
        }
    }

    /**
     * "Clamps" the given stake period start to the current stake period minus the number of stored periods (assuming
     * it is not already the sentinel value -1, which means the account never started staking).
     *
     * @param stakePeriodStart the stake period start
     * @param currentStakePeriod the current stake period
     * @param numStoredPeriods the number of stored periods
     * @return the clamped stake period start
     */
    static long clampedStakePeriodStart(
            final long stakePeriodStart, final long currentStakePeriod, final int numStoredPeriods) {
        if (stakePeriodStart > -1 && stakePeriodStart < currentStakePeriod - numStoredPeriods) {
            return currentStakePeriod - numStoredPeriods;
        }
        return stakePeriodStart;
    }

    private static long rewardFor(
            @NonNull final Account account,
            @NonNull final List<Long> rewardSumHistory,
            final long currentStakePeriod,
            final long effectiveStart) {
        final var rewardFrom = (int) (currentStakePeriod - 1 - effectiveStart);
        if (rewardFrom <= 0) {
            return 0;
        }

        final var firstRewardSum = rewardSumHistory.get(0);
        final var rewardFromSum = rewardSumHistory.get(rewardFrom);
        if (account.stakeAtStartOfLastRewardedPeriod() != -1) {
            final var rewardFromMinus1Sum = rewardSumHistory.get(rewardFrom - 1);

            // Two-step computation; first, the reward from the last period the account changed its
            // stake in...
            return account.stakeAtStartOfLastRewardedPeriod()
                            / HBARS_TO_TINYBARS
                            * (rewardFromMinus1Sum - rewardFromSum)
                    // ...and second, the reward for all following periods
                    + totalStake(account) / HBARS_TO_TINYBARS * (firstRewardSum - rewardFromMinus1Sum);
        } else {
            return totalStake(account) / HBARS_TO_TINYBARS * (firstRewardSum - rewardFromSum);
        }
    }

    private static long totalStake(@NonNull final Account account) {
        return account.tinybarBalance() + account.stakedToMe();
    }

    private static long estimatedFirstNonRewardableStakePeriod(
            final long stakingPeriodMins, final boolean stakingRewardsActive) {
        return stakingRewardsActive ? estimatedCurrentStakePeriod(stakingPeriodMins) - 1 : Long.MIN_VALUE;
    }
}
