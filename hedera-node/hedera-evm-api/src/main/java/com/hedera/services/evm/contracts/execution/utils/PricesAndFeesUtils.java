/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.evm.contracts.execution.utils;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.hedera.services.evm.contracts.execution.RequiredPriceTypes;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.math.BigInteger;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PricesAndFeesUtils {
    static final Logger log = LogManager.getLogger(PricesAndFeesUtils.class);
    private static ExchangeRateSet grpcRates = null;
    CurrentAndNextFeeSchedule feeSchedules;
    private static Timestamp currFunctionUsagePricesExpiry;
    private static Timestamp nextFunctionUsagePricesExpiry;
    private static EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    private static EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;
    private static final long DEFAULT_FEE = 100_000L;
    private static final int FEE_DIVISOR_FACTOR = 1000;
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

    public void setFeeSchedules(final CurrentAndNextFeeSchedule feeSchedules) {
        this.feeSchedules = feeSchedules;

        currFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getCurrentFeeSchedule());
        currFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getCurrentFeeSchedule().getExpiryTime());

        nextFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getNextFeeSchedule());
        nextFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getNextFeeSchedule().getExpiryTime());
    }

    public static Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
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

    private static Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(
            final Timestamp at) {
        if (onlyNextScheduleApplies(at)) {
            return nextFunctionUsagePrices;
        } else {
            return currFunctionUsagePrices;
        }
    }

    private static boolean onlyNextScheduleApplies(final Timestamp at) {
        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds()
                && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }

    public static ExchangeRate rateAt(final long now) {
        final var currentRate = grpcRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : grpcRates.getNextRate();
    }

    private static long getTinybarsFromTinyCents(ExchangeRate exchangeRate, long tinyCentsFee) {
        return getAFromB(tinyCentsFee, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
    }

    private static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        final var aMultiplier = BigInteger.valueOf(aEquiv);
        final var bDivisor = BigInteger.valueOf(bEquiv);
        return BigInteger.valueOf(bAmount).multiply(aMultiplier).divide(bDivisor).longValueExact();
    }

    public static long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    EnumMap<HederaFunctionality, Map<SubType, FeeData>> functionUsagePricesFrom(
            final FeeSchedule feeSchedule) {
        final EnumMap<HederaFunctionality, Map<SubType, FeeData>> allPrices =
                new EnumMap<>(HederaFunctionality.class);
        for (var pricingData : feeSchedule.getTransactionFeeScheduleList()) {
            final var function = pricingData.getHederaFunctionality();
            Map<SubType, FeeData> pricesMap = allPrices.get(function);
            if (pricesMap == null) {
                pricesMap = new EnumMap<>(SubType.class);
            }
            final Set<SubType> requiredTypes = RequiredPriceTypes.requiredTypesFor(function);
            ensurePricesMapHasRequiredTypes(pricingData, pricesMap, requiredTypes);
            allPrices.put(pricingData.getHederaFunctionality(), pricesMap);
        }
        return allPrices;
    }

    private Timestamp asTimestamp(final TimestampSeconds ts) {
        return Timestamp.newBuilder().setSeconds(ts.getSeconds()).build();
    }

    void ensurePricesMapHasRequiredTypes(
            final TransactionFeeSchedule tfs,
            final Map<SubType, FeeData> pricesMap,
            final Set<SubType> requiredTypes) {
        /* The deprecated prices are the final fallback; if even they are not set, the function will be free */
        final var oldDefaultPrices = tfs.getFeeData();
        FeeData newDefaultPrices = null;
        for (var typedPrices : tfs.getFeesList()) {
            final var type = typedPrices.getSubType();
            if (requiredTypes.contains(type)) {
                pricesMap.put(type, typedPrices);
            }
            if (type == DEFAULT) {
                newDefaultPrices = typedPrices;
            }
        }
        for (var type : requiredTypes) {
            if (!pricesMap.containsKey(type)) {
                if (newDefaultPrices != null) {
                    pricesMap.put(type, newDefaultPrices.toBuilder().setSubType(type).build());
                } else {
                    pricesMap.put(type, oldDefaultPrices.toBuilder().setSubType(type).build());
                }
            }
        }
    }
}
