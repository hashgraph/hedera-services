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
package com.hedera.services.evm.contracts.execution;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.hedera.services.evm.contracts.loader.impl.PricesAndFeesLoaderImpl;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;

public class PricesAndFeesImpl implements PricesAndFeesProvider {
    private final PricesAndFeesUtils utils;
    private final PricesAndFeesLoaderImpl pricesAndFeesLoader;

    private final CurrentAndNextFeeSchedule feeSchedules;

    public PricesAndFeesImpl(
            PricesAndFeesUtils utils,
            PricesAndFeesLoaderImpl pricesAndFeesLoader,
            CurrentAndNextFeeSchedule feeSchedules) {
        this.utils = utils;
        this.pricesAndFeesLoader = pricesAndFeesLoader;
        this.feeSchedules = feeSchedules;
    }

    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        pricesAndFeesLoader.loadFeeSchedules(at.getSeconds());
        return utils.pricesGiven(function, at).get(DEFAULT);
    }

    public ExchangeRate rate(Timestamp now) {
        return utils.rateAt(now.getSeconds());
    }

    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = rate(at);
        var prices = defaultPricesGiven(function, at);
        return PricesAndFeesUtils.gasPriceInTinybars(prices, rates);
    }

    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return 0;
    }
}
