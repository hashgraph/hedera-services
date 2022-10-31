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

import static com.hedera.services.evm.contracts.execution.utils.PricesAndFeesUtils.gasPriceInTinybars;
import static com.hedera.services.evm.contracts.execution.utils.PricesAndFeesUtils.pricesGiven;
import static com.hedera.services.evm.contracts.execution.utils.PricesAndFeesUtils.rateAt;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;

public interface PricesAndFeesProvider {
    static FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return pricesGiven(function, at).get(DEFAULT);
    }

    static ExchangeRate rate(final Timestamp now) {
        return rateAt(now.getSeconds());
    }

    static long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        var rates = rate(at);
        var prices = defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    default long currentGasPrice(Instant now, HederaFunctionality function) {
        return 0;
    }
}
