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

import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertExchangeRateFromProtoToDto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertFeeDataFromProtoToDto;

import com.hedera.node.app.service.evm.fee.FeeResourcesLoader;
import com.hedera.node.app.service.evm.fee.codec.ExchangeRate;
import com.hedera.node.app.service.evm.fee.codec.FeeData;
import com.hedera.node.app.service.evm.fee.codec.SubType;
import com.hedera.node.app.service.evm.utils.codec.HederaFunctionality;
import com.hedera.node.app.service.evm.utils.codec.Timestamp;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.fees.BasicHbarCentExchange;
import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

public class FeeResourcesLoaderImpl implements FeeResourcesLoader {

    private final BasicFcfsUsagePrices basicFcfsUsagePrices;
    private final BasicHbarCentExchange basicHbarCentExchange;
    private final MultiplierSources multiplierSources;
    private final TransactionContext txnCtx;

    @Inject
    public FeeResourcesLoaderImpl(
            final BasicFcfsUsagePrices basicFcfsUsagePrices,
            final BasicHbarCentExchange basicHbarCentExchange,
            final MultiplierSources multiplierSources,
            final TransactionContext txnCtx) {
        this.basicFcfsUsagePrices = basicFcfsUsagePrices;
        this.basicHbarCentExchange = basicHbarCentExchange;
        this.multiplierSources = multiplierSources;
        this.txnCtx = txnCtx;
    }

    @Override
    public ExchangeRate getCurrentRate() {
        return convertExchangeRateFromProtoToDto(basicHbarCentExchange.getCurrentRate());
    }

    @Override
    public ExchangeRate getNextRate() {
        return convertExchangeRateFromProtoToDto(basicHbarCentExchange.getNextRate());
    }

    @Override
    public long getMaxCurrentMultiplier() {
        return multiplierSources.maxCurrentMultiplier(txnCtx.accessor());
    }

    @Override
    public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
        final var prices = basicFcfsUsagePrices.pricesGiven(
                com.hederahashgraph.api.proto.java.HederaFunctionality.valueOf(function.name()),
                com.hederahashgraph.api.proto.java.Timestamp.newBuilder()
                        .setSeconds(at.getSeconds())
                        .setNanos(at.getNanos())
                        .build());

        return convertSubTypeToFeeDataMapFromProtoToDto(prices);
    }

    private Map<SubType, FeeData> convertSubTypeToFeeDataMapFromProtoToDto(
            final Map<com.hederahashgraph.api.proto.java.SubType, com.hederahashgraph.api.proto.java.FeeData> map) {
        final var subTypeMap = new HashMap<SubType, FeeData>();

        for (final var entry : map.entrySet()) {
            subTypeMap.put(SubType.valueOf(entry.getKey().name()), convertFeeDataFromProtoToDto(entry.getValue()));
        }

        return subTypeMap;
    }
}
