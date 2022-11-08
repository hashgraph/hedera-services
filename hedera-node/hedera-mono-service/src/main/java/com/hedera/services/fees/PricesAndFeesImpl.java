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

import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.evm.contracts.execution.PricesAndFeesUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PricesAndFeesImpl implements PricesAndFeesProvider {

    private final PricesAndFeesUtils utils = new PricesAndFeesUtils();

    @Inject
    public PricesAndFeesImpl() {}

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return utils.pricesGiven(function, at).get(SubType.DEFAULT);
    }

    @Override
    public ExchangeRate rate(Timestamp now) {
        return utils.rateAt(now.getSeconds());
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return 0;
    }
}
