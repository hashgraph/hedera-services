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
package com.hedera.services.evm.contracts.execution;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.hedera.services.evm.contracts.loader.impl.PricesAndFeesLoaderImpl;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PricesAndFeesUtils {
    PricesAndFeesLoaderImpl pricesAndFeesLoader = new PricesAndFeesLoaderImpl();
    static final Logger log = LogManager.getLogger(PricesAndFeesUtils.class);

    private ExchangeRateSet exchangeRates;
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

    public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
        try {
            Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices =
                    pricesAndFeesLoader.applicableUsagePrices(at);
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

    public ExchangeRate rateAt(final long now) {
        final var currentRate = exchangeRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : exchangeRates.getNextRate();
    }

    public static long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    public static long getTinybarsFromTinyCents(ExchangeRate exchangeRate, long tinyCentsFee) {
        return getAFromB(tinyCentsFee, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
    }

    public static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        final var aMultiplier = BigInteger.valueOf(aEquiv);
        final var bDivisor = BigInteger.valueOf(bEquiv);
        return BigInteger.valueOf(bAmount).multiply(aMultiplier).divide(bDivisor).longValueExact();
    }
}
