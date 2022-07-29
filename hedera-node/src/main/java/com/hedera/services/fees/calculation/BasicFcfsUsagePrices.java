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
package com.hedera.services.fees.calculation;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.files.HederaFs;
import com.hedera.services.pricing.RequiredPriceTypes;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements a {@link UsagePricesProvider} by loading the required fee schedules from the Hedera
 * "file system".
 */
@Singleton
public class BasicFcfsUsagePrices implements UsagePricesProvider {
    private static final Logger log = LogManager.getLogger(BasicFcfsUsagePrices.class);

    private static final long DEFAULT_FEE = 100_000L;
    private static final FeeComponents DEFAULT_PROVIDER_RESOURCE_PRICES =
            FeeComponents.newBuilder()
                    .setMin(DEFAULT_FEE)
                    .setMax(DEFAULT_FEE)
                    .setConstant(0)
                    .setBpt(0)
                    .setVpt(0)
                    .setRbh(0)
                    .setSbh(0)
                    .setGas(0)
                    .setTv(0)
                    .setBpr(0)
                    .setSbpr(0)
                    .build();
    public static final Map<SubType, FeeData> DEFAULT_RESOURCE_PRICES =
            Map.of(
                    DEFAULT,
                    FeeData.newBuilder()
                            .setNetworkdata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .setNodedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .setServicedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .build());

    private final HederaFs hfs;
    private final FileNumbers fileNumbers;

    CurrentAndNextFeeSchedule feeSchedules;

    private Timestamp currFunctionUsagePricesExpiry;
    private Timestamp nextFunctionUsagePricesExpiry;

    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;

    @Inject
    public BasicFcfsUsagePrices(HederaFs hfs, FileNumbers fileNumbers) {
        this.hfs = hfs;
        this.fileNumbers = fileNumbers;
    }

    @Override
    public void loadPriceSchedules() {
        var feeSchedulesId = fileNumbers.toFid(fileNumbers.feeSchedules());
        if (!hfs.exists(feeSchedulesId)) {
            throw new IllegalStateException(
                    String.format(
                            "No fee schedule available at %s!", readableId(this.feeSchedules)));
        }
        try {
            var schedules = CurrentAndNextFeeSchedule.parseFrom(hfs.cat(feeSchedulesId));
            setFeeSchedules(schedules);
        } catch (InvalidProtocolBufferException e) {
            log.warn(
                    "Corrupt fee schedules file at {}, may require remediation!",
                    readableId(this.feeSchedules),
                    e);
            throw new IllegalStateException(
                    String.format("Fee schedule %s is corrupt!", readableId(this.feeSchedules)));
        }
    }

    @Override
    public Map<SubType, FeeData> activePrices(TxnAccessor accessor) {
        try {
            return pricesGiven(
                    accessor.getFunction(), accessor.getTxnId().getTransactionValidStart());
        } catch (Exception e) {
            log.warn(
                    "Using default usage prices to calculate fees for {}!",
                    accessor.getSignedTxnWrapper(),
                    e);
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    @Override
    public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
        try {
            Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices =
                    applicableUsagePrices(at);
            Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
            Objects.requireNonNull(usagePrices);
            return usagePrices;
        } catch (Exception e) {
            log.debug(
                    "Default usage price will be used, no specific usage prices available for"
                            + " function {} @ {}!",
                    function,
                    Instant.ofEpochSecond(at.getSeconds(), at.getNanos()));
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    @Override
    public FeeData defaultPricesGiven(final HederaFunctionality function, final Timestamp at) {
        return pricesGiven(function, at).get(DEFAULT);
    }

    @Override
    public Triple<Map<SubType, FeeData>, Instant, Map<SubType, FeeData>> activePricingSequence(
            final HederaFunctionality function) {
        return Triple.of(
                currFunctionUsagePrices.get(function),
                Instant.ofEpochSecond(
                        currFunctionUsagePricesExpiry.getSeconds(),
                        currFunctionUsagePricesExpiry.getNanos()),
                nextFunctionUsagePrices.get(function));
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(
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

    public void setFeeSchedules(final CurrentAndNextFeeSchedule feeSchedules) {
        this.feeSchedules = feeSchedules;

        currFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getCurrentFeeSchedule());
        currFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getCurrentFeeSchedule().getExpiryTime());

        nextFunctionUsagePrices = functionUsagePricesFrom(feeSchedules.getNextFeeSchedule());
        nextFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getNextFeeSchedule().getExpiryTime());
    }

    private Timestamp asTimestamp(final TimestampSeconds ts) {
        return Timestamp.newBuilder().setSeconds(ts.getSeconds()).build();
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
}
