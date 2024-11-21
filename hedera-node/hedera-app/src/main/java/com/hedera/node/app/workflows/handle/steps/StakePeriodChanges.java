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

import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static java.time.ZoneOffset.UTC;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TssConfig;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.common.RosterStateId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.WritableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Orchestrates changes that happen before the first transaction in a new staking period. See
 * {@link StakePeriodChanges#process(Dispatch, SavepointStackImpl, TokenContext, StreamMode, boolean, Instant)}
 * for details.
 */
@Singleton
public class StakePeriodChanges {
    private static final Logger logger = LogManager.getLogger(StakePeriodChanges.class);

    private static final long DEFAULT_STAKING_PERIOD_MINS = 1440L;
    private static final long MINUTES_TO_MILLISECONDS = 60_000L;

    private final EndOfStakingPeriodUpdater endOfStakingPeriodUpdater;
    private final ExchangeRateManager exchangeRateManager;
    private final TssBaseService tssBaseService;
    private final StoreMetricsService storeMetricsService;

    @Inject
    public StakePeriodChanges(
            @NonNull final EndOfStakingPeriodUpdater endOfStakingPeriodUpdater,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final TssBaseService tssBaseService,
            @NonNull final StoreMetricsService storeMetricsService) {
        this.endOfStakingPeriodUpdater = requireNonNull(endOfStakingPeriodUpdater);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.tssBaseService = requireNonNull(tssBaseService);
        this.storeMetricsService = requireNonNull(storeMetricsService);
    }

    /**
     * Orchestrates changes that happen before the first transaction in a new staking period, as follows:
     * <ol>
     *     <li>Saves the current exchange rates as the "midnight rates" that tether the rates within the
     *     following period to a bounded interval, barring an explicit admin override.</li>
     *     <li>Updates node staking metadata (in particular, the nodes' reward rates earned for the just-ending
     *     period and their weight for the just-starting period); and exports this to the block stream.</li>
     *     <li>If appropriate, triggers rekeying a new candidate roster based on a snapshot of the node
     *     information computed in the previous step, and all dynamic address book (DAB) transactions
     *     handled up to this consensus time.</li>
     * </ol>
     *
     * @param dispatch the dispatch
     * @param stack the savepoint stack
     * @param tokenContext the token context
     * @param streamMode the stream mode
     * @param isGenesis whether the current transaction is the genesis transaction
     * @param lastIntervalProcessTime if known, the last instant when time-based events were processed
     */
    public void process(
            @NonNull final Dispatch dispatch,
            @NonNull final SavepointStackImpl stack,
            @NonNull final TokenContext tokenContext,
            @NonNull final StreamMode streamMode,
            final boolean isGenesis,
            @NonNull final Instant lastIntervalProcessTime) {
        requireNonNull(stack);
        requireNonNull(dispatch);
        requireNonNull(tokenContext);
        requireNonNull(streamMode);
        requireNonNull(lastIntervalProcessTime);
        if (isGenesis || isStakingPeriodBoundary(streamMode, tokenContext, lastIntervalProcessTime)) {
            try {
                exchangeRateManager.updateMidnightRates(stack);
                stack.commitSystemStateChanges();
            } catch (Exception e) {
                logger.error("CATASTROPHIC failure updating midnight rates", e);
                stack.rollbackFullStack();
            }
            final var config = tokenContext.configuration();
            try {
                final var nodeStore = newWritableNodeStore(stack, config);
                final BiConsumer<Long, Integer> weightUpdates = (nodeId, weight) -> nodeStore.put(
                        nodeStore.get(nodeId).copyBuilder().weight(weight).build());
                final var streamBuilder = endOfStakingPeriodUpdater.updateNodes(
                        tokenContext, exchangeRateManager.exchangeRates(), weightUpdates);
                if (streamBuilder != null) {
                    stack.commitTransaction(streamBuilder);
                }
            } catch (Exception e) {
                logger.error("CATASTROPHIC failure updating end-of-day stakes", e);
                stack.rollbackFullStack();
            }
            if (config.getConfigData(TssConfig.class).keyCandidateRoster()) {
                tssBaseService.regenerateKeyMaterial(stack);
                startKeyingCandidateRoster(dispatch.handleContext(), newWritableRosterStore(stack, config));
            }
        }
    }

    private boolean isStakingPeriodBoundary(
            @NonNull final StreamMode streamMode,
            @NonNull final TokenContext tokenContext,
            @NonNull final Instant lastIntervalProcessTime) {
        final var consensusTime = tokenContext.consensusTime();
        if (streamMode == RECORDS) {
            final var blockStore = tokenContext.readableStore(ReadableBlockRecordStore.class);
            final var lastHandleTime = blockStore.getLastBlockInfo().consTimeOfLastHandledTxnOrThrow();
            if (consensusTime.getEpochSecond() > lastHandleTime.seconds()) {
                return isNextStakingPeriod(
                        consensusTime,
                        Instant.ofEpochSecond(lastHandleTime.seconds(), lastHandleTime.nanos()),
                        tokenContext);
            }
        } else {
            if (consensusTime.getEpochSecond() > lastIntervalProcessTime.getEpochSecond()) {
                return isNextStakingPeriod(consensusTime, lastIntervalProcessTime, tokenContext);
            }
        }
        return false;
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

    private void startKeyingCandidateRoster(
            @NonNull final HandleContext handleContext, @NonNull final WritableRosterStore rosterStore) {
        final var nodeStore = handleContext.storeFactory().readableStore(ReadableNodeStore.class);
        final var roster = nodeStore.snapshotOfFutureRoster();
        if (!Objects.equals(roster, rosterStore.getCandidateRoster())
                && !Objects.equals(roster, rosterStore.getActiveRoster())) {
            rosterStore.putCandidateRoster(roster);
            tssBaseService.setCandidateRoster(roster, handleContext);
        }
    }

    private WritableRosterStore newWritableRosterStore(
            @NonNull final SavepointStackImpl stack, @NonNull final Configuration config) {
        final var writableFactory = new WritableStoreFactory(stack, RosterStateId.NAME, config, storeMetricsService);
        return writableFactory.getStore(WritableRosterStore.class);
    }

    private WritableNodeStore newWritableNodeStore(
            @NonNull final SavepointStackImpl stack, @NonNull final Configuration config) {
        final var writableFactory =
                new WritableStoreFactory(stack, AddressBookService.NAME, config, storeMetricsService);
        return writableFactory.getStore(WritableNodeStore.class);
    }

    private static boolean isLaterUtcDay(@NonNull final Instant now, @NonNull final Instant then) {
        final var nowDay = LocalDate.ofInstant(now, UTC);
        final var thenDay = LocalDate.ofInstant(then, UTC);
        return nowDay.isAfter(thenDay);
    }
}
