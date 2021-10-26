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
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static com.hedera.services.context.domain.trackers.IssEventStatus.ONGOING_ISS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.SECONDS;

@Singleton
public class NetworkCtxManager {
	private static final Logger log = LogManager.getLogger(NetworkCtxManager.class);

	private final int issResetPeriod;

	private boolean consensusSecondJustChanged = false;

	private final IssEventInfo issInfo;
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
			IssEventInfo issInfo,
			NodeLocalProperties nodeLocalProperties,
			HapiOpCounters opCounters,
			HbarCentExchange exchange,
			SystemFilesManager systemFilesManager,
			FeeMultiplierSource feeMultiplierSource,
			GlobalDynamicProperties dynamicProperties,
			@HandleThrottle FunctionalityThrottling handleThrottling,
			Supplier<MerkleNetworkContext> networkCtx,
			TransactionContext txnCtx
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
			log.info("Loaded observable files. {}", networkCtxNow);
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

	public ResponseCodeEnum prepareForIncorporating(TxnAccessor accessor) {
		/* This is used to monitor the current network usage for automated congestion pricing
			and throttle ContractCreate and ContractCall TXs by gas limit.*/
		if(handleThrottling.shouldThrottleTxn(accessor, false) &&
				(accessor.getFunction().equals(ContractCall) || accessor.getFunction().equals(ContractCreate))){
			return ResponseCodeEnum.BUSY;
		}

		feeMultiplierSource.updateMultiplier(networkCtx.get().consensusTimeOfLastHandledTxn());
		return ResponseCodeEnum.OK;
	}

	public void finishIncorporating(HederaFunctionality op) {
		opCounters.countHandled(op);

		if(op.equals(HederaFunctionality.ContractCall) || op.equals(HederaFunctionality.ContractCreate)){
			var gasUsed = op.equals(HederaFunctionality.ContractCall) ?
					txnCtx.recordSoFar().getContractCallResult().getGasUsed() :
					txnCtx.recordSoFar().getContractCreateResult().getGasUsed();
			final var txGasLimit = op == ContractCreate ?
					txnCtx.accessor().getTxn().getContractCreateInstance().getGas() :
					txnCtx.accessor().getTxn().getContractCall().getGas();
			handleThrottling.leakUnusedGasPreviouslyReserved(txGasLimit - gasUsed);
		}

		var networkCtxNow = networkCtx.get();
		networkCtxNow.syncThrottling(handleThrottling);
		networkCtxNow.syncMultiplierSource(feeMultiplierSource);
	}

	public static boolean inSameUtcDay(Instant now, Instant then) {
		return LocalDateTime.ofInstant(now, UTC).getDayOfYear() == LocalDateTime.ofInstant(then, UTC).getDayOfYear();
	}

	int getIssResetPeriod() {
		return issResetPeriod;
	}

	void setShouldUpdateMidnightRates(BiPredicate<Instant, Instant> shouldUpdateMidnightRates) {
		this.shouldUpdateMidnightRates = shouldUpdateMidnightRates;
	}

	BiPredicate<Instant, Instant> getShouldUpdateMidnightRates() {
		return shouldUpdateMidnightRates;
	}
}
