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

package com.hedera.node.app.service.mono.fees.calculation;

import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.utils.codec.HederaFunctionality;
import com.hedera.node.app.service.mono.contracts.execution.LivePricesSource;
import java.time.Instant;
import javax.inject.Inject;

public class PricesAndFeesProviderImpl implements PricesAndFeesProvider {

    private final LivePricesSource livePricesSource;

    @Inject
    public PricesAndFeesProviderImpl(final LivePricesSource livePricesSource) {
        this.livePricesSource = livePricesSource;
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return livePricesSource.currentGasPrice(
                now, com.hederahashgraph.api.proto.java.HederaFunctionality.valueOf(function.name()));
    }

    @Override
    public long currentGasPriceInTinycents(Instant now, HederaFunctionality function) {
        return livePricesSource.currentGasPriceInTinycents(
                now, com.hederahashgraph.api.proto.java.HederaFunctionality.valueOf(function.name()));
    }
}
