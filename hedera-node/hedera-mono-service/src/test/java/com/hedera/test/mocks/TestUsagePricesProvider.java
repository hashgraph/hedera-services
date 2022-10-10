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
package com.hedera.test.mocks;

import static com.hedera.services.fees.calculation.BasicFcfsUsagePrices.DEFAULT_RESOURCE_PRICES;

import com.google.common.io.Files;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Triple;

public enum TestUsagePricesProvider implements UsagePricesProvider {
    TEST_USAGE_PRICES;

    public static final String R4_FEE_SCHEDULE_REPR_PATH =
            "src/test/resources/testfiles/r4FeeSchedule.bin";

    CurrentAndNextFeeSchedule feeSchedules;

    Timestamp currFunctionUsagePricesExpiry;
    Timestamp nextFunctionUsagePricesExpiry;

    Map<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    Map<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;

    TestUsagePricesProvider() {
        loadPriceSchedules();
    }

    @Override
    public void loadPriceSchedules() {
        try {
            byte[] bytes = Files.toByteArray(new File(R4_FEE_SCHEDULE_REPR_PATH));
            setFeeSchedules(CurrentAndNextFeeSchedule.parseFrom(bytes));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public Map<SubType, FeeData> activePrices(TxnAccessor accessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
        try {
            Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices =
                    applicableUsagePrices(at);
            Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
            Objects.requireNonNull(usagePrices);
            return usagePrices;
        } catch (Exception ignore) {
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return pricesGiven(function, at).get(SubType.DEFAULT);
    }

    @Override
    public Triple<Map<SubType, FeeData>, Instant, Map<SubType, FeeData>> activePricingSequence(
            HederaFunctionality function) {
        var now = Instant.now();
        var prices = pricesGiven(function, MiscUtils.asTimestamp(now));
        return Triple.of(prices, now, prices);
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(Timestamp at) {
        if (onlyNextScheduleApplies(at)) {
            return nextFunctionUsagePrices;
        } else {
            return currFunctionUsagePrices;
        }
    }

    private boolean onlyNextScheduleApplies(Timestamp at) {
        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds()
                && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }

    public void setFeeSchedules(CurrentAndNextFeeSchedule feeSchedules) {
        this.feeSchedules = feeSchedules;

        currFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getCurrentFeeSchedule());
        currFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getCurrentFeeSchedule().getExpiryTime());

        nextFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getNextFeeSchedule());
        nextFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getNextFeeSchedule().getExpiryTime());
    }

    private Timestamp asTimestamp(TimestampSeconds ts) {
        return Timestamp.newBuilder().setSeconds(ts.getSeconds()).build();
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePricesFrom(
            FeeSchedule feeSchedule) {
        var feeScheduleList = feeSchedule.getTransactionFeeScheduleList();
        Map<HederaFunctionality, Map<SubType, FeeData>> feeDataMap = new HashMap<>();
        for (TransactionFeeSchedule fs : feeScheduleList) {
            Map<SubType, FeeData> subTypeMap = new HashMap<>();
            for (FeeData value : fs.getFeesList()) {
                subTypeMap.put(value.getSubType(), value);
            }
            feeDataMap.put(fs.getHederaFunctionality(), subTypeMap);
        }
        return feeDataMap;
    }
}
