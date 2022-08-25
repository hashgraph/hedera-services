/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hedera.services.context.domain.trackers.IssEventStatus.ONGOING_ISS;
import static com.hedera.services.context.properties.PropertyNames.STAKING_PERIOD_MINS;
import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.DEFAULT_STAKING_PERIOD_MINS;
import static com.hedera.services.utils.MiscUtils.isGasThrottled;
import static com.hedera.services.utils.Units.MINUTES_TO_MILLISECONDS;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.accounts.staking.EndOfStakingPeriodCalculator;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class NetworkCtxManager {
    private static final Logger log = LogManager.getLogger(NetworkCtxManager.class);

    private final int issResetPeriod;

    private long gasUsedThisConsSec = 0L;
    private boolean consensusSecondJustChanged = false;

    private final long stakingPeriod;
    private final IssEventInfo issInfo;
    private final ExpiryThrottle expiryThrottle;
    private final MiscRunningAvgs runningAvgs;
    private final HapiOpCounters opCounters;
    private final HbarCentExchange exchange;
    private final SystemFilesManager systemFilesManager;
    private final FeeMultiplierSource feeMultiplierSource;
    private final GlobalDynamicProperties dynamicProperties;
    private final FunctionalityThrottling handleThrottling;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final EndOfStakingPeriodCalculator endOfStakingPeriodCalculator;
    private final TransactionContext txnCtx;

    private BiPredicate<Instant, Instant> isNextDay = (now, then) -> !inSameUtcDay(now, then);

    @Inject
    public NetworkCtxManager(
            final IssEventInfo issInfo,
            final ExpiryThrottle expiryThrottle,
            final NodeLocalProperties nodeLocalProperties,
            final HapiOpCounters opCounters,
            final HbarCentExchange exchange,
            final SystemFilesManager systemFilesManager,
            final FeeMultiplierSource feeMultiplierSource,
            final GlobalDynamicProperties dynamicProperties,
            final @HandleThrottle FunctionalityThrottling handleThrottling,
            final Supplier<MerkleNetworkContext> networkCtx,
            final TransactionContext txnCtx,
            final MiscRunningAvgs runningAvgs,
            final EndOfStakingPeriodCalculator endOfStakingPeriodCalculator,
            final @CompositeProps PropertySource propertySource) {
        issResetPeriod = nodeLocalProperties.issResetPeriod();

        this.issInfo = issInfo;
        this.expiryThrottle = expiryThrottle;
        this.opCounters = opCounters;
        this.exchange = exchange;
        this.networkCtx = networkCtx;
        this.systemFilesManager = systemFilesManager;
        this.feeMultiplierSource = feeMultiplierSource;
        this.handleThrottling = handleThrottling;
        this.dynamicProperties = dynamicProperties;
        this.runningAvgs = runningAvgs;
        this.txnCtx = txnCtx;
        this.endOfStakingPeriodCalculator = endOfStakingPeriodCalculator;
        this.stakingPeriod = propertySource.getLongProperty(STAKING_PERIOD_MINS);
    }

    public void setObservableFilesNotLoaded() {
        systemFilesManager.setObservableFilesNotLoaded();
    }

    public void loadObservableSysFilesIfNeeded() {
        if (!systemFilesManager.areObservableFilesLoaded()) {
            var networkCtxNow = networkCtx.get();
            log.info("Observable files not yet loaded, doing now");
            systemFilesManager.loadObservableSystemFiles();
            log.info("Loaded observable files");
            networkCtxNow.resetThrottlingFromSavedSnapshots(handleThrottling);
            feeMultiplierSource.resetExpectations();
            networkCtxNow.resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);
            networkCtxNow.resetExpiryThrottleFromSavedSnapshot(expiryThrottle);
        }
    }

    public void advanceConsensusClockTo(Instant consensusTime) {
        final var networkCtxNow = networkCtx.get();
        final var lastConsensusTime = networkCtxNow.consensusTimeOfLastHandledTxn();

        if (lastConsensusTime == null
                || consensusTime.getEpochSecond() > lastConsensusTime.getEpochSecond()) {
            consensusSecondJustChanged = true;
            // We're in a new second, so check if it's the first of a UTC calendar day; there are
            // some special actions that trigger on the first transaction after midnight
            if (lastConsensusTime == null || isNextPeriod(lastConsensusTime, consensusTime)) {
                networkCtxNow.midnightRates().replaceWith(exchange.activeRates());
                endOfStakingPeriodCalculator.updateNodes(consensusTime);
            }
        } else {
            consensusSecondJustChanged = false;
        }

        networkCtxNow.setConsensusTimeOfLastHandledTxn(consensusTime);

        if (issInfo.status() == ONGOING_ISS) {
            issInfo.consensusTimeOfRecentAlert()
                    .ifPresentOrElse(
                            recentAlertTime -> {
                                var resetTime = recentAlertTime.plus(issResetPeriod, SECONDS);
                                if (consensusTime.isAfter(resetTime)) {
                                    issInfo.relax();
                                }
                            },
                            issInfo::relax);
        }
    }

    @VisibleForTesting
    boolean isNextPeriod(final Instant lastConsensusTime, final Instant consensusTime) {
        if (stakingPeriod == DEFAULT_STAKING_PERIOD_MINS) {
            return isNextDay.test(lastConsensusTime, consensusTime);
        } else {
            return getPeriod(consensusTime, stakingPeriod * MINUTES_TO_MILLISECONDS)
                    != getPeriod(lastConsensusTime, stakingPeriod * MINUTES_TO_MILLISECONDS);
        }
    }

    public boolean currentTxnIsFirstInConsensusSecond() {
        return consensusSecondJustChanged;
    }

    /**
     * Callback used by the processing flow to notify the context manager it should update any
     * network context that derives from the side effects of handled transactions.
     *
     * <p>At present, this context includes:
     *
     * <ol>
     *   <li>The {@code *Handled} counts.
     *   <li>The {@code gasPerConsSec} running average.
     *   <li>The "in-handle" throttles.
     *   <li>The congestion pricing multiplier.
     * </ol>
     *
     * @param op the type of transaction just handled
     */
    public void finishIncorporating(@Nonnull HederaFunctionality op) {
        opCounters.countHandled(op);
        if (consensusSecondJustChanged) {
            runningAvgs.recordGasPerConsSec(gasUsedThisConsSec);
            gasUsedThisConsSec = 0L;
        }

        if (isGasThrottled(op) && txnCtx.hasContractResult()) {
            final var gasUsed = txnCtx.getGasUsedForContractTxn();
            gasUsedThisConsSec += gasUsed;
            if (dynamicProperties.shouldThrottleByGas()) {
                final var excessAmount = txnCtx.accessor().getGasLimitForContractTx() - gasUsed;
                handleThrottling.leakUnusedGasPreviouslyReserved(txnCtx.accessor(), excessAmount);
            }
        }

        var networkCtxNow = networkCtx.get();
        networkCtxNow.syncThrottling(handleThrottling);
        networkCtxNow.syncMultiplierSource(feeMultiplierSource);
    }

    public static boolean inSameUtcDay(Instant now, Instant then) {
        return LocalDateTime.ofInstant(now, UTC).getDayOfYear()
                == LocalDateTime.ofInstant(then, UTC).getDayOfYear();
    }

    /* --- Only used in unit tests --- */
    int getIssResetPeriod() {
        return issResetPeriod;
    }

    void setConsensusSecondJustChanged(boolean consensusSecondJustChanged) {
        this.consensusSecondJustChanged = consensusSecondJustChanged;
    }

    long getGasUsedThisConsSec() {
        return gasUsedThisConsSec;
    }

    void setGasUsedThisConsSec(long gasUsedThisConsSec) {
        this.gasUsedThisConsSec = gasUsedThisConsSec;
    }

    void setIsNextDay(BiPredicate<Instant, Instant> isNextDay) {
        this.isNextDay = isNextDay;
    }

    BiPredicate<Instant, Instant> getIsNextDay() {
        return isNextDay;
    }
}
