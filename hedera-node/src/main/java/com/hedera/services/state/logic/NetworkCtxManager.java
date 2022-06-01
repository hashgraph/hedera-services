package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static com.hedera.services.context.domain.trackers.IssEventStatus.ONGOING_ISS;
import static com.hedera.services.utils.MiscUtils.isGasThrottled;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.SECONDS;

@Singleton
public class NetworkCtxManager {
	private static final Logger log = LogManager.getLogger(NetworkCtxManager.class);

	private final int issResetPeriod;

	private long gasUsedThisConsSec = 0L;
	private boolean consensusSecondJustChanged = false;

	private final IssEventInfo issInfo;
	private final MiscRunningAvgs runningAvgs;
	private final HapiOpCounters opCounters;
	private final HbarCentExchange exchange;
	private final SystemFilesManager systemFilesManager;
	private final FeeMultiplierSource feeMultiplierSource;
	private final GlobalDynamicProperties dynamicProperties;
	private final FunctionalityThrottling handleThrottling;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final TransactionContext txnCtx;

	private BiPredicate<Instant, Instant> shouldUpdateMidnightRates = (now, then) -> !inSameUtcDay(now, then);

	@Inject
	public NetworkCtxManager(
			final IssEventInfo issInfo,
			final NodeLocalProperties nodeLocalProperties,
			final HapiOpCounters opCounters,
			final HbarCentExchange exchange,
			final SystemFilesManager systemFilesManager,
			final FeeMultiplierSource feeMultiplierSource,
			final GlobalDynamicProperties dynamicProperties,
			final @HandleThrottle FunctionalityThrottling handleThrottling,
			final Supplier<MerkleNetworkContext> networkCtx,
			final TransactionContext txnCtx,
			final MiscRunningAvgs runningAvgs
	) {
		issResetPeriod = nodeLocalProperties.issResetPeriod();

		this.issInfo = issInfo;
		this.opCounters = opCounters;
		this.exchange = exchange;
		this.networkCtx = networkCtx;
		this.systemFilesManager = systemFilesManager;
		this.feeMultiplierSource = feeMultiplierSource;
		this.handleThrottling = handleThrottling;
		this.dynamicProperties = dynamicProperties;
		this.runningAvgs = runningAvgs;
		this.txnCtx = txnCtx;
	}

	public void setObservableFilesNotLoaded() {
		systemFilesManager.setObservableFilesNotLoaded();
	}

	public void loadObservableSysFilesIfNeeded() {
		if (!systemFilesManager.areObservableFilesLoaded()) {
			var networkCtxNow = networkCtx.get();
			log.info("Observable files not yet loaded, doing now.");
			systemFilesManager.loadObservableSystemFiles();
			log.info("Loaded observable files");
			networkCtxNow.resetThrottlingFromSavedSnapshots(handleThrottling);
			feeMultiplierSource.resetExpectations();
			networkCtxNow.resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);
		}
	}

	public void advanceConsensusClockTo(Instant consensusTime) {
		final var networkCtxNow = networkCtx.get();
		final var lastConsensusTime = networkCtxNow.consensusTimeOfLastHandledTxn();
		final var lastMidnightBoundaryCheck = networkCtxNow.lastMidnightBoundaryCheck();

		if (lastMidnightBoundaryCheck != null) {
			final long intervalSecs = dynamicProperties.ratesMidnightCheckInterval();
			final long elapsedInterval = consensusTime.getEpochSecond() - lastMidnightBoundaryCheck.getEpochSecond();

			/* We only check whether the midnight rates should be updated every intervalSecs in consensus time */
			if (elapsedInterval >= intervalSecs) {
				/* If the lastMidnightBoundaryCheck was in a different UTC day, we update the midnight rates */
				if (shouldUpdateMidnightRates.test(lastMidnightBoundaryCheck, consensusTime)) {
					networkCtxNow.midnightRates().replaceWith(exchange.activeRates());
				}
				/* And mark this as the last time we checked the midnight boundary */
				networkCtxNow.setLastMidnightBoundaryCheck(consensusTime);
			}
		} else {
			/* The first transaction after genesis will initialize the lastMidnightBoundaryCheck */
			networkCtxNow.setLastMidnightBoundaryCheck(consensusTime);
		}

		if (lastConsensusTime == null || consensusTime.getEpochSecond() > lastConsensusTime.getEpochSecond()) {
			consensusSecondJustChanged = true;
		} else {
			consensusSecondJustChanged = false;
		}

		networkCtxNow.setConsensusTimeOfLastHandledTxn(consensusTime);

		if (issInfo.status() == ONGOING_ISS) {
			issInfo.consensusTimeOfRecentAlert().ifPresentOrElse(recentAlertTime -> {
				var resetTime = recentAlertTime.plus(issResetPeriod, SECONDS);
				if (consensusTime.isAfter(resetTime)) {
					issInfo.relax();
				}
			}, issInfo::relax);
		}
	}

	public boolean currentTxnIsFirstInConsensusSecond() {
		return consensusSecondJustChanged;
	}

	/**
	 * Callback used by the processing flow to notify the context manager it should update any network
	 * context that derives from the side effects of handled transactions.
	 *
	 * At present, this context includes:
	 * <ol>
	 *     <li>The {@code *Handled} counts.</li>
	 *     <li>The {@code gasPerConsSec} running average.</li>
	 *     <li>The "in-handle" throttles.</li>
	 *     <li>The congestion pricing multiplier.</li>
	 * </ol>
	 *
	 * @param op the transaction just handled
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
		return LocalDateTime.ofInstant(now, UTC).getDayOfYear() == LocalDateTime.ofInstant(then, UTC).getDayOfYear();
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

	void setShouldUpdateMidnightRates(BiPredicate<Instant, Instant> shouldUpdateMidnightRates) {
		this.shouldUpdateMidnightRates = shouldUpdateMidnightRates;
	}

	BiPredicate<Instant, Instant> getShouldUpdateMidnightRates() {
		return shouldUpdateMidnightRates;
	}
}
