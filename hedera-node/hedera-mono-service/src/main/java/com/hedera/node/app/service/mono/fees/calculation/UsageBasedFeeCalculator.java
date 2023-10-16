/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getFeeObject;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getTinybarsFromTinyCents;
import static com.hedera.node.app.service.mono.keys.HederaKeyTraversal.numSimpleKeys;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractAutoRenew;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAccountAutoRenew;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.annotations.GenericPriceMultiplier;
import com.hedera.node.app.service.mono.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.node.app.service.mono.fees.congestion.FeeMultiplierSource;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements a {@link FeeCalculator} in terms of injected usage prices, exchange rates, and
 * collections of estimators which can infer the resource usage of various transactions and queries.
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
            final AutoRenewCalcs autoRenewCalcs,
            final HbarCentExchange exchange,
            final AutoCreationLogic autoCreationLogic,
            final UsagePricesProvider usagePrices,
            final @GenericPriceMultiplier FeeMultiplierSource feeMultiplierSource,
            final PricedUsageCalculator pricedUsageCalculator,
            final Set<QueryResourceUsageEstimator> queryUsageEstimators,
            final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.feeMultiplierSource = feeMultiplierSource;
        this.autoRenewCalcs = autoRenewCalcs;
        this.txnUsageEstimators = txnUsageEstimators;
        this.queryUsageEstimators = new ArrayList<>(queryUsageEstimators);
        this.pricedUsageCalculator = pricedUsageCalculator;

        autoCreationLogic.setFeeCalculator(this);
    }

    @Override
    public void init() {
        usagePrices.loadPriceSchedules();
        autoRenewCalcs.setAccountRenewalPriceSeq(usagePrices.activePricingSequence(CryptoAccountAutoRenew));
        autoRenewCalcs.setContractRenewalPriceSeq(usagePrices.activePricingSequence(ContractAutoRenew));
    }

    @Override
    public RenewAssessment assessCryptoAutoRenewal(
            final HederaAccount expiredAccountOrContract,
            final long requestedRenewal,
            final Instant now,
            final HederaAccount payer) {
        return autoRenewCalcs.assessCryptoRenewal(
                expiredAccountOrContract, requestedRenewal, now, exchange.activeRate(now), payer);
    }

    @Override
    public FeeObject computePayment(
            Query query, FeeData usagePrices, StateView view, Timestamp at, Map<String, Object> queryCtx) {
        return compute(query, usagePrices, at, estimator -> estimator.usageGiven(query, view, queryCtx));
    }

    @Override
    public FeeObject estimatePayment(
            Query query, FeeData usagePrices, StateView view, Timestamp at, ResponseType type) {
        return compute(query, usagePrices, at, estimator -> estimator.usageGivenType(query, view, type));
    }

    private FeeObject compute(
            Query query, FeeData usagePrices, Timestamp at, Function<QueryResourceUsageEstimator, FeeData> usageFn) {
        var usageEstimator = getQueryUsageEstimator(query);
        var queryUsage = usageFn.apply(usageEstimator);
        return computeFromQueryResourceUsage(queryUsage, usagePrices, at);
    }

    /**
     * Computes the fees for a query, given the query's resource usage, the current prices,
     * and the estimated consensus time.
     *
     * @param queryUsage the resource usage of the query
     * @param usagePrices the current prices
     * @param at the estimated consensus time
     * @return the fees for the query
     */
    public FeeObject computeFromQueryResourceUsage(
            final FeeData queryUsage, final FeeData usagePrices, final Timestamp at) {
        return getFeeObject(usagePrices, queryUsage, exchange.rate(at));
    }

    @Override
    public FeeObject computeFee(TxnAccessor accessor, JKey payerKey, StateView view, Instant now) {
        return feeGiven(accessor, payerKey, view, usagePrices.activePrices(accessor), exchange.activeRate(now), true);
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
            case EthereumTransaction:
                return -accessor.getTxn().getEthereumTransaction().getMaxGasAllowance();
            default:
                return 0L;
        }
    }

    private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    public Map<SubType, FeeData> uncheckedPricesGiven(TxnAccessor accessor, Timestamp at) {
        try {
            return usagePrices.pricesGiven(accessor.getFunction(), at);
        } catch (Exception e) {
            log.warn("Using default usage prices to calculate fees for {}!", accessor.getSignedTxnWrapper(), e);
        }
        return BasicFcfsUsagePrices.DEFAULT_RESOURCE_PRICES;
    }

    private FeeObject feeGiven(
            final TxnAccessor accessor,
            final JKey payerKey,
            final StateView view,
            final Map<SubType, FeeData> prices,
            final ExchangeRate rate,
            final boolean inHandle) {
        final var function = accessor.getFunction();
        if (pricedUsageCalculator.supports(function)) {
            final var applicablePrices = prices.get(accessor.getSubType());
            return inHandle
                    ? pricedUsageCalculator.inHandleFees(accessor, applicablePrices, rate, payerKey)
                    : pricedUsageCalculator.extraHandleFees(accessor, applicablePrices, rate, payerKey);
        } else {
            var sigUsage = getSigUsage(accessor, payerKey);
            var usageEstimator = getTxnUsageEstimator(accessor);
            final var usage = usageEstimator.usageGiven(accessor.getTxn(), sigUsage, view);
            final var applicablePrices = prices.get(usage.getSubType());
            return feesIncludingCongestion(usage, applicablePrices, accessor, rate);
        }
    }

    public FeeObject feesIncludingCongestion(
            final FeeData usage, final FeeData typedPrices, final TxnAccessor accessor, final ExchangeRate rate) {
        return getFeeObject(typedPrices, usage, rate, feeMultiplierSource.currentMultiplier(accessor));
    }

    private QueryResourceUsageEstimator getQueryUsageEstimator(Query query) {
        for (final QueryResourceUsageEstimator estimator : queryUsageEstimators) {
            if (estimator.applicableTo(query)) {
                return estimator;
            }
        }
        throw new NoSuchElementException("No estimator exists for the given query");
    }

    private TxnResourceUsageEstimator getTxnUsageEstimator(TxnAccessor accessor) {
        var txn = accessor.getTxn();
        var estimators = Optional.ofNullable(txnUsageEstimators.get(accessor.getFunction()))
                .orElse(Collections.emptyList());
        for (TxnResourceUsageEstimator estimator : estimators) {
            if (estimator.applicableTo(txn)) {
                return estimator;
            }
        }
        throw new NoSuchElementException("No estimator exists for the given transaction");
    }

    public SigValueObj getSigUsage(TxnAccessor accessor, JKey payerKey) {
        int numPayerKeys = numSimpleKeys(payerKey);
        final var sigUsage = accessor.usageGiven(numPayerKeys);
        return new SigValueObj(sigUsage.numSigs(), numPayerKeys, sigUsage.sigsSize());
    }
}
