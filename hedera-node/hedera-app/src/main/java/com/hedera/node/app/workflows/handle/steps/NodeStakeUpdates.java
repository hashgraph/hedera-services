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

package com.hedera.node.app.workflows.handle.steps;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static java.time.ZoneOffset.UTC;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.TssBaseService.TssContext;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.LocalDate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles the daily staking period updates
 */
@Singleton
public class NodeStakeUpdates {
    private static final Logger logger = LogManager.getLogger(NodeStakeUpdates.class);

    private static final long DEFAULT_STAKING_PERIOD_MINS = 1440L;
    private static final long MINUTES_TO_MILLISECONDS = 60_000L;

    private final EndOfStakingPeriodUpdater stakingCalculator;
    private final ExchangeRateManager exchangeRateManager;
    private final TssBaseService tssBaseService;

    @Inject
    public NodeStakeUpdates(
            @NonNull final EndOfStakingPeriodUpdater stakingPeriodCalculator,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final TssBaseService tssBaseService) {
        this.stakingCalculator = requireNonNull(stakingPeriodCalculator);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.tssBaseService = requireNonNull(tssBaseService);
    }

    /**
     * This time hook is responsible for performing necessary staking updates and distributing staking
     * rewards. This should only be done during handling of the first transaction of each new staking
     * period, which staking period usually starts at midnight UTC.
     *
     * <p>The only exception to this rule is when {@code consensusTimeOfLastHandledTxn} is null,
     * <b>which should only happen on node startup.</b> The node should therefore run this process
     * to catch up on updates and distributions when first coming online.
     *
     * @param stack the savepoint stack
     * @param tokenContext the token context
     * @param isGenesis whether the current transaction is the genesis transaction
     * @param networkInfo the network information
     */
    public void process(
            @NonNull final SavepointStackImpl stack,
            @NonNull final TokenContext tokenContext,
            final boolean isGenesis,
            @NonNull final NetworkInfo networkInfo) {
        requireNonNull(stack, "stack must not be null");
        requireNonNull(tokenContext, "tokenContext must not be null");
        final var blockStore = tokenContext.readableStore(ReadableBlockRecordStore.class);
        var shouldExport = isGenesis;
        if (!shouldExport) {
            final var consensusTime = tokenContext.consensusTime();
            final var lastHandleTime = blockStore.getLastBlockInfo().consTimeOfLastHandledTxnOrThrow();
            if (consensusTime.getEpochSecond() > lastHandleTime.seconds()) {
                shouldExport = isNextStakingPeriod(
                        consensusTime,
                        Instant.ofEpochSecond(lastHandleTime.seconds(), lastHandleTime.nanos()),
                        tokenContext);
            }
        }
        if (shouldExport) {
            try {
                // Update the exchange rate
                exchangeRateManager.updateMidnightRates(stack);
                stack.commitSystemStateChanges();
            } catch (final Exception e) {
                // If anything goes wrong, we log the error and continue
                logger.error("CATASTROPHIC failure updating midnight rates", e);
                stack.rollbackFullStack();
            }
            try {
                // handle staking updates
                final var streamBuilder =
                        stakingCalculator.updateNodes(tokenContext, exchangeRateManager.exchangeRates());
                if (streamBuilder != null) {
                    stack.commitTransaction(streamBuilder);
                }
            } catch (final Exception e) {
                // If anything goes wrong, we log the error and continue
                logger.error("CATASTROPHIC failure updating end-of-day stakes", e);
                stack.rollbackFullStack();
            }
            final var config = tokenContext.configuration();
            final var tssConfig = config.getConfigData(TssConfig.class);
            if (tssConfig.keyCandidateRoster()) {
                final var context =
                        new TssContext(config, networkInfo.selfNodeInfo().accountId(), tokenContext.consensusTime());
                tssBaseService.startKeyingCandidate(Roster.DEFAULT, context);
            }
        }
    }

    @VisibleForTesting
    public static boolean isNextStakingPeriod(
            @NonNull final Instant currentConsensusTime,
            @NonNull final Instant previousConsensusTime,
            @NonNull final TokenContext tokenContext) {
        final var stakingPeriod =
                tokenContext.configuration().getConfigData(StakingConfig.class).periodMins();
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
