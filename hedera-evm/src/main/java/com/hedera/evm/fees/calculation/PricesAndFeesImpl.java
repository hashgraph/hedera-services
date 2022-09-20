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
package com.hedera.evm.fees.calculation;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.evm.context.primitives.StateView;
import com.hedera.evm.fees.FeeCalculator;
import com.hedera.evm.fees.PricesAndFeesProvider;
import com.hedera.evm.store.contracts.precompile.Precompile;
import com.hedera.evm.utils.accessors.AccessorFactory;
import com.hedera.evm.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.fee.FeeObject;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class PricesAndFeesImpl implements PricesAndFeesProvider {
    private static final Logger log = LogManager.getLogger(PricesAndFeesImpl.class);
    private final AccessorFactory accessorFactory;
    private Timestamp currFunctionUsagePricesExpiry;
    private Timestamp nextFunctionUsagePricesExpiry;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;
    private static final long DEFAULT_FEE = 100_000L;
    private final Provider<FeeCalculator> feeCalculator;
    private final StateView currentView;
    public static final Bytes[] EMPTY_KEY = new Bytes[0];
    private static final FeeComponents DEFAULT_PROVIDER_RESOURCE_PRICES =
            FeeComponents.newBuilder()
                    .setMin(DEFAULT_FEE)
                    .setMax(DEFAULT_FEE)
                    .setConstant(0)
                    .setBpt(0)
                    .setVpt(0)
                    .setRbh(0)
                    .setSbh(0)
                    .setGas(0)
                    .setTv(0)
                    .setBpr(0)
                    .setSbpr(0)
                    .build();
    public static final Map<SubType, FeeData> DEFAULT_RESOURCE_PRICES =
            Map.of(
                    DEFAULT,
                    FeeData.newBuilder()
                            .setNetworkdata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .setNodedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .setServicedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .build());

    private ExchangeRateSet grpcRates = null;

    public PricesAndFeesImpl(
            AccessorFactory accessorFactory,
            Provider<FeeCalculator> feeCalculator,
            StateView currentView) {
        this.accessorFactory = accessorFactory;
        this.feeCalculator = feeCalculator;
        this.currentView = currentView;
    }

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return pricesGiven(function, at).get(DEFAULT);
    }

    @Override
    public ExchangeRate rate(Timestamp now) {
        return rateAt(now.getSeconds());
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = rate(at);
        var prices = defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    @Override
    public FeeObject getRedirectQueryFee(FeeData usagePrices, Timestamp at) {
        return null;
    }

    @Override
    public long gasFeeInTinybars(Instant consensusTime, Precompile precompile) {
        final var signedTxn =
                SignedTransaction.newBuilder().setSigMap(SignatureMap.getDefaultInstance()).build();
        final var txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(signedTxn.toByteString())
                        .build();

        final var accessor = accessorFactory.uncheckedSpecializedAccessor(txn);
        precompile.addImplicitCostsIn(accessor);
        final var fees =
                feeCalculator.get().computeFee(accessor, EMPTY_KEY, currentView, consensusTime);
        return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
    }

    public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
        try {
            Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices =
                    applicableUsagePrices(at);
            Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
            Objects.requireNonNull(usagePrices);
            return usagePrices;
        } catch (Exception e) {
            log.debug(
                    "Default usage price will be used, no specific usage prices available for"
                            + " function {} @ {}!",
                    function,
                    Instant.ofEpochSecond(at.getSeconds(), at.getNanos()));
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(
            final Timestamp at) {
        if (onlyNextScheduleApplies(at)) {
            return nextFunctionUsagePrices;
        } else {
            return currFunctionUsagePrices;
        }
    }

    private boolean onlyNextScheduleApplies(final Timestamp at) {
        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds()
                && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }

    private ExchangeRate rateAt(final long now) {
        final var currentRate = grpcRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : grpcRates.getNextRate();
    }

    private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    private Map<SubType, FeeData> uncheckedPricesGiven(TxnAccessor accessor, Timestamp at) {
        try {
            return pricesGiven(accessor.getFunction(), at);
        } catch (Exception e) {
            log.warn(
                    "Using default usage prices to calculate fees for {}!",
                    accessor.getSignedTxnWrapper(),
                    e);
        }
        return DEFAULT_RESOURCE_PRICES;
    }
}
