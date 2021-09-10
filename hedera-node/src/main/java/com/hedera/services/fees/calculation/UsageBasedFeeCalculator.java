package com.hedera.services.fees.calculation;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.FeeObject;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.hedera.services.fees.calculation.BasicFcfsUsagePrices.DEFAULT_RESOURCE_PRICES;
import static com.hedera.services.keys.HederaKeyTraversal.numSimpleKeys;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAccountAutoRenew;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static com.hederahashgraph.fee.FeeBuilder.getFeeObject;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;

/**
 * Implements a {@link FeeCalculator} in terms of injected usage prices,
 * exchange rates, and collections of estimators which can infer the
 * resource usage of various transactions and queries.
 */
@Singleton
public class UsageBasedFeeCalculator implements FeeCalculator {
	private static final Logger log = LogManager.getLogger(UsageBasedFeeCalculator.class);

	private final AutoRenewCalcs autoRenewCalcs;
	private final HbarCentExchange exchange;
	private final FeeMultiplierSource feeMultiplierSource;
	private final UsagePricesProvider usagePrices;
	private final PricedUsageCalculator pricedUsageCalculator;
	private final List<QueryResourceUsageEstimator> queryUsageEstimators;
	private final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators;

	@Inject
	public UsageBasedFeeCalculator(
			AutoRenewCalcs autoRenewCalcs,
			HbarCentExchange exchange,
			UsagePricesProvider usagePrices,
			FeeMultiplierSource feeMultiplierSource,
			PricedUsageCalculator pricedUsageCalculator,
			Set<QueryResourceUsageEstimator> queryUsageEstimators,
			Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators
	) {
		this.exchange = exchange;
		this.usagePrices = usagePrices;
		this.feeMultiplierSource = feeMultiplierSource;
		this.autoRenewCalcs = autoRenewCalcs;
		this.txnUsageEstimators = txnUsageEstimators;
		this.queryUsageEstimators = new ArrayList<>(queryUsageEstimators);
		this.pricedUsageCalculator = pricedUsageCalculator;
	}

	@Override
	public void init() {
		usagePrices.loadPriceSchedules();
		autoRenewCalcs.setCryptoAutoRenewPriceSeq(usagePrices.activePricingSequence(CryptoAccountAutoRenew));
	}

	@Override
	public AutoRenewCalcs.RenewAssessment assessCryptoAutoRenewal(
			MerkleAccount expiredAccount,
			long requestedRenewal,
			Instant now
	) {
		return autoRenewCalcs.maxRenewalAndFeeFor(expiredAccount, requestedRenewal, now, exchange.activeRate(now));
	}

	@Override
	public FeeObject computePayment(
			Query query,
			FeeData usagePrices,
			StateView view,
			Timestamp at,
			Map<String, Object> queryCtx
	) {
		return compute(query, usagePrices, at, estimator -> estimator.usageGiven(query, view, queryCtx));
	}

	@Override
	public FeeObject estimatePayment(
			Query query,
			FeeData usagePrices,
			StateView view,
			Timestamp at,
			ResponseType type
	) {
		return compute(query, usagePrices, at, estimator -> estimator.usageGivenType(query, view, type));
	}

	private FeeObject compute(
			Query query,
			FeeData usagePrices,
			Timestamp at,
			Function<QueryResourceUsageEstimator, FeeData> usageFn
	) {
		var usageEstimator = getQueryUsageEstimator(query);
		var queryUsage = usageFn.apply(usageEstimator);
		return getFeeObject(usagePrices, queryUsage, exchange.rate(at));
	}

	@Override
	public FeeObject computeFee(TxnAccessor accessor, JKey payerKey, StateView view, Instant now) {
		return feeGiven(
				accessor, payerKey, view, usagePrices.activePrices(accessor), exchange.activeRate(now), true);
	}

	@Override
	public FeeObject estimateFee(TxnAccessor accessor, JKey payerKey, StateView view, Timestamp at) {
		Map<SubType, FeeData> prices = uncheckedPricesGiven(accessor, at);

		return feeGiven(accessor, payerKey, view, prices, exchange.rate(at), false);
	}

	@Override
	public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
		var rates = exchange.rate(at);
		var prices = usagePrices.defaultPricesGiven(function, at);
		return gasPriceInTinybars(prices, rates);
	}

	@Override
	public long estimatedNonFeePayerAdjustments(TxnAccessor accessor, Timestamp at) {
		switch (accessor.getFunction()) {
			case CryptoCreate:
				var cryptoCreateOp = accessor.getTxn().getCryptoCreateAccount();
				return -cryptoCreateOp.getInitialBalance();
			case CryptoTransfer:
				var payer = accessor.getPayer();
				var cryptoTransferOp = accessor.getTxn().getCryptoTransfer();
				var adjustments = cryptoTransferOp.getTransfers().getAccountAmountsList();
				long cryptoTransferNet = 0L;
				for (AccountAmount adjustment : adjustments) {
					if (payer.equals(adjustment.getAccountID())) {
						cryptoTransferNet += adjustment.getAmount();
					}
				}
				return cryptoTransferNet;
			case ContractCreate:
				var contractCreateOp = accessor.getTxn().getContractCreateInstance();
				return -contractCreateOp.getInitialBalance()
						- contractCreateOp.getGas() * estimatedGasPriceInTinybars(ContractCreate, at);
			case ContractCall:
				var contractCallOp = accessor.getTxn().getContractCall();
				return -contractCallOp.getAmount()
						- contractCallOp.getGas() * estimatedGasPriceInTinybars(ContractCall, at);
			default:
				return 0L;
		}
	}

	private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
		long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
		long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
		return Math.max(priceInTinyBars, 1L);
	}

	private Map<SubType, FeeData> uncheckedPricesGiven(TxnAccessor accessor, Timestamp at) {
		try {
			return usagePrices.pricesGiven(accessor.getFunction(), at);
		} catch (Exception e) {
			log.warn("Using default usage prices to calculate fees for {}!", accessor.getSignedTxnWrapper(), e);
		}
		return DEFAULT_RESOURCE_PRICES;
	}

	private FeeObject feeGiven(
			TxnAccessor accessor,
			JKey payerKey,
			StateView view,
			Map<SubType, FeeData> prices,
			ExchangeRate rate,
			boolean inHandle
	) {
		final var function = accessor.getFunction();
		if (pricedUsageCalculator.supports(function)) {
			SubType subType = getSubTypeFor(accessor, view);
			final var applicablePrices = prices.get(subType);
			return inHandle
					? pricedUsageCalculator.inHandleFees(accessor, applicablePrices, rate, payerKey)
					: pricedUsageCalculator.extraHandleFees(accessor, applicablePrices, rate, payerKey);
		} else {
			var sigUsage = getSigUsage(accessor, payerKey);
			var usageEstimator = getTxnUsageEstimator(accessor);
			try {
				final var usage = usageEstimator.usageGiven(accessor.getTxn(), sigUsage, view);
				final var applicablePrices = prices.get(usage.getSubType());
				return getFeeObject(applicablePrices, usage, rate, feeMultiplierSource.currentMultiplier());
			} catch (InvalidTxBodyException e) {
				log.warn(
						"Argument accessor={} malformed for implied estimator {}!",
						accessor.getSignedTxnWrapper(), usageEstimator);
				throw new IllegalArgumentException(e);
			}
		}
	}

	private QueryResourceUsageEstimator getQueryUsageEstimator(Query query) {
		Optional<QueryResourceUsageEstimator> usageEstimator = queryUsageEstimators
				.stream()
				.filter(estimator -> estimator.applicableTo(query))
				.findAny();
		if (usageEstimator.isPresent()) {
			return usageEstimator.get();
		}
		throw new NoSuchElementException("No estimator exists for the given query");
	}

	private TxnResourceUsageEstimator getTxnUsageEstimator(TxnAccessor accessor) {
		var txn = accessor.getTxn();
		var estimators = Optional
				.ofNullable(txnUsageEstimators.get(accessor.getFunction()))
				.orElse(Collections.emptyList());
		for (TxnResourceUsageEstimator estimator : estimators) {
			if (estimator.applicableTo(txn)) {
				return estimator;
			}
		}
		throw new NoSuchElementException("No estimator exists for the given transaction");
	}

	private SigValueObj getSigUsage(TxnAccessor accessor, JKey payerKey) {
		return new SigValueObj(accessor.numSigPairs(), numSimpleKeys(payerKey), accessor.sigMapSize());
	}

	private SubType getSubTypeFor(final TxnAccessor accessor, final StateView view) {
		if (accessor.getSubType() != SubType.DEFAULT) {
			return accessor.getSubType();
		}
		if(isTokenTxn(accessor)) {
			return getTokenTxnSubType(accessor, view);
		}

		return SubType.DEFAULT;
	}

	private boolean isTokenTxn(final TxnAccessor accessor) {
		final var function = accessor.getFunction();
		return function == TokenMint || function == TokenBurn || function == TokenAccountWipe;
	}

	private SubType getTokenTxnSubType(final TxnAccessor accessor, final StateView view) {
		final var function = accessor.getFunction();
		TokenID tokenID = null;
		if (function == TokenBurn) {
			tokenID = accessor.getTxn().getTokenBurn().getToken();
		} else if (function == TokenMint) {
			tokenID = accessor.getTxn().getTokenMint().getToken();
		} else if (function == TokenAccountWipe) {
			tokenID = accessor.getTxn().getTokenWipe().getToken();
		}
		Optional<TokenType> tokenType = view.tokenType(tokenID);
		if (tokenType.isPresent() && tokenType.get() == TokenType.NON_FUNGIBLE_UNIQUE) {
			return SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
		}
		return SubType.TOKEN_FUNGIBLE_COMMON;
	}
}
