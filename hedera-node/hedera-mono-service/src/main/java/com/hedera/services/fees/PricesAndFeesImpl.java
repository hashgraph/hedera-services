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
package com.hedera.services.fees;

import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PricesAndFeesImpl implements PricesAndFeesProvider {

    private final HbarCentExchange exchange;
    private final UsagePricesProvider usagePrices;
    private final LivePricesSource livePricesSource;

    @Inject
    public PricesAndFeesImpl(
            final HbarCentExchange exchange,
            final UsagePricesProvider usagePrices,
            final LivePricesSource livePricesSource) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.livePricesSource = livePricesSource;
    }

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return usagePrices.defaultPricesGiven(function, at);
    }

    @Override
    public ExchangeRate rate(Timestamp at) {
        return exchange.rate(at);
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = exchange.rate(at);
        var prices = usagePrices.defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return livePricesSource.currentPrice(now, function, FeeComponents::getGas);
    }
}
