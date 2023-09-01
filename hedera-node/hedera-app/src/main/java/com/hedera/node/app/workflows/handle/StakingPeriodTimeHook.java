/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.service.mono.ledger.accounts.staking.StakePeriodManager.DEFAULT_STAKING_PERIOD_MINS;
import static com.hedera.node.app.service.mono.utils.Units.MINUTES_TO_MILLISECONDS;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static java.time.ZoneOffset.UTC;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater;
import com.hedera.node.app.service.token.records.StakingContext;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link ConsensusTimeHook} implementation that handles the daily staking period updates
 */
@Singleton
public class StakingPeriodTimeHook implements ConsensusTimeHook {
    private static final Logger logger = LogManager.getLogger(StakingPeriodTimeHook.class);

    private final EndOfStakingPeriodUpdater stakingCalculator;
    private Instant consensusTimeOfLastHandledTxn;

    @Inject
    public StakingPeriodTimeHook(@NonNull final EndOfStakingPeriodUpdater stakingPeriodCalculator) {
        this.stakingCalculator = requireNonNull(stakingPeriodCalculator);
    }

    /**
     * This time hook is responsible for performing necessary staking updates and distributing staking
     * rewards. This should only be done during handling of the first transaction of each new staking
     * period, which staking period usually starts at midnight UTC.
     *
     * <p>The only exception to this rule is when {@code consensusTimeOfLastHandledTxn} is null,
     * <b>which should only happen on node startup.</b> The node should therefore run this process
     * to catch up on updates and distributions when first coming online.
     */
    @Override
    public void process(@NonNull final StakingContext context) {
        requireNonNull(context, "context must not be null");
        final var consensusTime = context.consensusTime();
        if (consensusTimeOfLastHandledTxn == null
                || consensusTime.getEpochSecond() > consensusTimeOfLastHandledTxn.getEpochSecond()
                        && isNextStakingPeriod(consensusTime, consensusTimeOfLastHandledTxn, context)) {
            // Handle the daily staking distributions and updates
            try {
                stakingCalculator.updateNodes(context);
            } catch (final Exception e) {
                logger.error("CATASTROPHIC failure updating end-of-day stakes", e);
            }

            // Advance the last consensus time to the given consensus time
            consensusTimeOfLastHandledTxn = consensusTime;
        }
    }

    @VisibleForTesting
    void setLastConsensusTime(@Nullable final Instant lastConsensusTime) {
        consensusTimeOfLastHandledTxn = lastConsensusTime;
    }

    @VisibleForTesting
    static boolean isNextStakingPeriod(
            @NonNull final Instant currentConsensusTime,
            @NonNull final Instant previousConsensusTime,
            @NonNull final StakingContext stakingContext) {
        final var stakingPeriod = stakingContext
                .configuration()
                .getConfigData(StakingConfig.class)
                .periodMins();
        if (stakingPeriod == DEFAULT_STAKING_PERIOD_MINS) {
            return isLaterUtcDay(currentConsensusTime, previousConsensusTime);
        } else {
            return getPeriod(currentConsensusTime, stakingPeriod * MINUTES_TO_MILLISECONDS)
                    > getPeriod(previousConsensusTime, stakingPeriod * MINUTES_TO_MILLISECONDS);
        }
    }

    private static boolean isLaterUtcDay(@NonNull final Instant now, @NonNull final Instant then) {
        final var nowDay = LocalDate.ofInstant(now, UTC);
        final var thenDay = LocalDate.ofInstant(then, UTC);
        return nowDay.isAfter(thenDay);
    }
}
