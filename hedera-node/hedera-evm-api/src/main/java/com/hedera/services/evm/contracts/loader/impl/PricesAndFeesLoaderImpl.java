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

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.hedera.services.evm.contracts.execution.RequiredPriceTypes;
import com.hedera.services.evm.contracts.loader.PricesAndFeesLoader;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class PricesAndFeesLoaderImpl implements PricesAndFeesLoader {

    private Timestamp currFunctionUsagePricesExpiry;
    private Timestamp nextFunctionUsagePricesExpiry;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;
    CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder().build();

    @Override
    public Timestamp currFunctionUsagePricesExpiry() {
        return asTimestamp(feeSchedules.getCurrentFeeSchedule().getExpiryTime());
    }

    @Override
    public Timestamp nextFunctionUsagePricesExpiry() {
        return asTimestamp(feeSchedules.getNextFeeSchedule().getExpiryTime());
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices() {
        return functionUsagePricesFrom(feeSchedules.getCurrentFeeSchedule());
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices() {
        return functionUsagePricesFrom(feeSchedules.getNextFeeSchedule());
    }

    @Override
    public CurrentAndNextFeeSchedule getFeeSchedules(long now) {
        return feeSchedules;
    }

    public void loadFeeSchedules(final long now) {
        PricesAndFeesLoader pricesAndFeesLoader =
                new PricesAndFeesLoader() {

                    final PricesAndFeesLoaderImpl feesLoader = new PricesAndFeesLoaderImpl();

                    @Override
                    public Timestamp currFunctionUsagePricesExpiry() {
                        return feesLoader.currFunctionUsagePricesExpiry();
                    }

                    @Override
                    public Timestamp nextFunctionUsagePricesExpiry() {
                        return feesLoader.nextFunctionUsagePricesExpiry();
                    }

                    @Override
                    public EnumMap<HederaFunctionality, Map<SubType, FeeData>>
                            currFunctionUsagePrices() {
                        return feesLoader.currFunctionUsagePrices();
                    }

                    @Override
                    public EnumMap<HederaFunctionality, Map<SubType, FeeData>>
                            nextFunctionUsagePrices() {
                        return feesLoader.nextFunctionUsagePrices();
                    }

                    @Override
                    public CurrentAndNextFeeSchedule getFeeSchedules(long now) {
                        return feeSchedules;
                    }
                };

        getFeeSchedules(now);

        currFunctionUsagePricesExpiry = pricesAndFeesLoader.currFunctionUsagePricesExpiry();
        nextFunctionUsagePricesExpiry = pricesAndFeesLoader.nextFunctionUsagePricesExpiry();
        currFunctionUsagePrices = pricesAndFeesLoader.currFunctionUsagePrices();
        nextFunctionUsagePrices = pricesAndFeesLoader.nextFunctionUsagePrices();
    }

    public Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(
            final Timestamp at) {
        if (onlyNextScheduleApplies(at)) {
            return nextFunctionUsagePrices;
        } else {
            return currFunctionUsagePrices;
        }
    }

    private boolean onlyNextScheduleApplies(final Timestamp at) {
        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds()
                && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }

    EnumMap<HederaFunctionality, Map<SubType, FeeData>> functionUsagePricesFrom(
            final FeeSchedule feeSchedule) {
        final EnumMap<HederaFunctionality, Map<SubType, FeeData>> allPrices =
                new EnumMap<>(HederaFunctionality.class);
        for (var pricingData : feeSchedule.getTransactionFeeScheduleList()) {
            final var function = pricingData.getHederaFunctionality();
            Map<SubType, FeeData> pricesMap = allPrices.get(function);
            if (pricesMap == null) {
                pricesMap = new EnumMap<>(SubType.class);
            }
            final Set<SubType> requiredTypes = RequiredPriceTypes.requiredTypesFor(function);
            ensurePricesMapHasRequiredTypes(pricingData, pricesMap, requiredTypes);
            allPrices.put(pricingData.getHederaFunctionality(), pricesMap);
        }
        return allPrices;
    }

    private Timestamp asTimestamp(final TimestampSeconds ts) {
        return Timestamp.newBuilder().setSeconds(ts.getSeconds()).build();
    }

    void ensurePricesMapHasRequiredTypes(
            final TransactionFeeSchedule tfs,
            final Map<SubType, FeeData> pricesMap,
            final Set<SubType> requiredTypes) {
        /* The deprecated prices are the final fallback; if even they are not set, the function will be free */
        final var oldDefaultPrices = tfs.getFeeData();
        FeeData newDefaultPrices = null;
        for (var typedPrices : tfs.getFeesList()) {
            final var type = typedPrices.getSubType();
            if (requiredTypes.contains(type)) {
                pricesMap.put(type, typedPrices);
            }
            if (type == DEFAULT) {
                newDefaultPrices = typedPrices;
            }
        }
        for (var type : requiredTypes) {
            if (!pricesMap.containsKey(type)) {
                if (newDefaultPrices != null) {
                    pricesMap.put(type, newDefaultPrices.toBuilder().setSubType(type).build());
                } else {
                    pricesMap.put(type, oldDefaultPrices.toBuilder().setSubType(type).build());
                }
            }
        }
    }

    public Timestamp getCurrFunctionUsagePricesExpiry() {
        return currFunctionUsagePricesExpiry;
    }

    public Timestamp getNextFunctionUsagePricesExpiry() {
        return nextFunctionUsagePricesExpiry;
    }

    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> getCurrFunctionUsagePrices() {
        return currFunctionUsagePrices;
    }

    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> getNextFunctionUsagePrices() {
        return nextFunctionUsagePrices;
    }
}
