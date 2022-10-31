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
package com.hedera.services.evm.contracts.loader.impl;

import com.hedera.services.evm.contracts.loader.PricesAndFeesLoader;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.EnumMap;
import java.util.Map;

public class PricesAndFeesLoaderImpl implements PricesAndFeesLoader {
    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> getCurrFunctionUsagePrices() {
        return null;
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> getNextFunctionUsagePrices() {
        return null;
    }

    @Override
    public Timestamp currFunctionUsagePricesExpiry() {
        return null;
    }

    @Override
    public Timestamp nextFunctionUsagePricesExpiry() {
        return null;
    }

    @Override
    public ExchangeRateSet getExchangeRates() {
        return null;
    }

    @Override
    public CurrentAndNextFeeSchedule feeSchedules() {
        return null;
    }
}
