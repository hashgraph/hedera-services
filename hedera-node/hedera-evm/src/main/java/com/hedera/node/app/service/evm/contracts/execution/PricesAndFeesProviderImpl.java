/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.contracts.execution;

import com.hedera.node.app.service.evm.fee.FeeResourcesLoader;
import com.hedera.node.app.service.evm.fee.codec.ExchangeRate;
import com.hedera.node.app.service.evm.fee.codec.FeeComponents;
import com.hedera.node.app.service.evm.fee.codec.FeeData;
import com.hedera.node.app.service.evm.fee.codec.SubType;
import com.hedera.node.app.service.evm.utils.codec.HederaFunctionality;
import com.hedera.node.app.service.evm.utils.codec.Timestamp;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.ToLongFunction;

public class PricesAndFeesProviderImpl implements PricesAndFeesProvider {

    public static final long DEFAULT_FEE = 100_000L;
    private final FeeResourcesLoader feeResourcesLoader;

    public PricesAndFeesProviderImpl(final FeeResourcesLoader feeResourcesLoader) {
        this.feeResourcesLoader = feeResourcesLoader;
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return currentPrice(now, function, FeeComponents::getGas);
    }

    @Override
    public long currentGasPriceInTinycents(Instant now, HederaFunctionality function) {
        return currentFeeInTinycents(now, function, com.hedera.node.app.service.evm.fee.codec.FeeComponents::getGas);
    }

    @Override
    public ExchangeRate rateAt(final long now) {
        final var currentRate = feeResourcesLoader.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime();
        return (now < currentExpiry) ? currentRate : feeResourcesLoader.getNextRate();
    }

    @Override
    public ExchangeRate activeRate(Instant now) {
        return rateAt(now.getEpochSecond());
    }

    @Override
    public ExchangeRate rate(Timestamp at) {
        return rateAt(at.getSeconds());
    }

    @Override
    public FeeData defaultPricesGiven(final HederaFunctionality function, final Timestamp at) {
        return feeResourcesLoader.pricesGiven(function, at).get(SubType.DEFAULT);
    }

    private long currentPrice(
            final Instant now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
        final var exchangeRate = rateAt(now.getEpochSecond());
        long feeInTinyBars =
                getTinybarsFromTinyCents(feeInTinyCents, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
        final var unscaledPrice = Math.max(1L, feeInTinyBars);

        final var maxMultiplier = Long.MAX_VALUE / feeInTinyBars;
        final var curMultiplier = feeResourcesLoader.getMaxCurrentMultiplier();
        if (curMultiplier > maxMultiplier) {
            return Long.MAX_VALUE;
        } else {
            return unscaledPrice * curMultiplier;
        }
    }

    private long getTinybarsFromTinyCents(
            final long tinyCentsAmount, final int hbarDenominator, final int centNominator) {
        final var aMultiplier = BigInteger.valueOf(hbarDenominator);
        final var bDivisor = BigInteger.valueOf(centNominator);
        return BigInteger.valueOf(tinyCentsAmount)
                .multiply(aMultiplier)
                .divide(bDivisor)
                .longValueExact();
    }

    private long currentFeeInTinycents(
            final Instant now,
            final HederaFunctionality function,
            final ToLongFunction<com.hedera.node.app.service.evm.fee.codec.FeeComponents> resourcePriceFn) {
        final var timestamp = new Timestamp(now.getEpochSecond(), 0);
        final var prices = defaultPricesGiven(function, timestamp);

        /* Fee schedule prices are set in thousandths of a tinycent */
        return resourcePriceFn.applyAsLong(prices.getServiceData()) / 1000;
    }
}
