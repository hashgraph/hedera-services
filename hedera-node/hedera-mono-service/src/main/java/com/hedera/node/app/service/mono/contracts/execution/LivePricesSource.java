/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.execution;

import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProviderImpl;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LivePricesSource {
    private final HbarCentExchange exchange;
    private final UsagePricesProvider usagePrices;
    private final MultiplierSources multiplierSources;
    private final PricesAndFeesProvider pricesAndFeesProvider;
    private final TransactionContext txnCtx;

    @Inject
    public LivePricesSource(
            final HbarCentExchange exchange,
            final UsagePricesProvider usagePrices,
            final MultiplierSources multiplierSources,
            final TransactionContext txnCtx,
            final FeeResourcesLoaderImpl feeResourcesLoader) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.multiplierSources = multiplierSources;
        this.pricesAndFeesProvider = new PricesAndFeesProviderImpl(feeResourcesLoader);
        this.txnCtx = txnCtx;
    }

    //    public long currentGasPrice(final Instant now, final HederaFunctionality function) {
    //        return pricesAndFeesProvider.currentGasPrice(now,
    //            com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.valueOf(function.name()));
    //    }

    //    public long currentGasPriceInTinycents(final Instant now, final HederaFunctionality function) {
    ////        return currentFeeInTinycents(now, function, FeeComponents::getGas);
    //        return pricesAndFeesProvider.currentGasPriceInTinycents(now,
    // com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.valueOf(function.name()));
    //    }

    //    private long currentPrice(
    //            final Instant now,
    //            final HederaFunctionality function,
    //            final ToLongFunction<FeeComponents> resourcePriceFn) {
    //        final var timestamp =
    //                Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
    //        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
    //        long feeInTinyBars = getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
    //        final var unscaledPrice = Math.max(1L, feeInTinyBars);
    //
    //        final var maxMultiplier = Long.MAX_VALUE / feeInTinyBars;
    //        final var curMultiplier = multiplierSources.maxCurrentMultiplier(txnCtx.accessor());
    //        if (curMultiplier > maxMultiplier) {
    //            return Long.MAX_VALUE;
    //        } else {
    //            return unscaledPrice * curMultiplier;
    //        }
    //    }

    //    private long currentFeeInTinycents(
    //            final Instant now,
    //            final HederaFunctionality function,
    //            final ToLongFunction<FeeComponents> resourcePriceFn) {
    //        final var timestamp =
    //                Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
    //        final var prices = usagePrices.defaultPricesGiven(function, timestamp);
    //
    //        /* Fee schedule prices are set in thousandths of a tinycent */
    //        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
    //    }
}
