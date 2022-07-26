/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.bdd.spec.fees;

import static com.hederahashgraph.fee.FeeBuilder.getFeeObject;
import static com.hederahashgraph.fee.FeeBuilder.getSignatureCount;
import static com.hederahashgraph.fee.FeeBuilder.getSignatureSize;
import static com.hederahashgraph.fee.FeeBuilder.getTotalFeeforRequest;

import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import com.hederahashgraph.fee.SigValueObj;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FeeCalculator {
    private static final Logger log = LogManager.getLogger(FeeCalculator.class);

    private final HapiSpecSetup setup;
    private final Map<HederaFunctionality, Map<SubType, FeeData>> opFeeData = new HashMap<>();
    private final FeesAndRatesProvider provider;

    private long fixedFee = Long.MIN_VALUE;
    private boolean usingFixedFee = false;

    private int tokenTransferUsageMultiplier = 1;

    public FeeCalculator(HapiSpecSetup setup, FeesAndRatesProvider provider) {
        this.setup = setup;
        this.provider = provider;
    }

    public Map<HederaFunctionality, Map<SubType, FeeData>> getCurrentOpFeeData() {
        return opFeeData;
    }

    public void init() {
        if (setup.useFixedFee()) {
            usingFixedFee = true;
            fixedFee = setup.fixedFee();
            return;
        }
        FeeSchedule feeSchedule = provider.currentSchedule();
        feeSchedule
                .getTransactionFeeScheduleList()
                .forEach(
                        f -> {
                            opFeeData.put(
                                    f.getHederaFunctionality(), feesListToMap(f.getFeesList()));
                        });
        tokenTransferUsageMultiplier = setup.feesTokenTransferUsageMultiplier();
    }

    public static Map<SubType, FeeData> feesListToMap(List<FeeData> feesList) {
        Map<SubType, FeeData> feeDataMap = new HashMap<>();
        for (FeeData feeData : feesList) {
            feeDataMap.put(feeData.getSubType(), feeData);
        }
        return feeDataMap;
    }

    private long maxFeeTinyBars(SubType subType) {
        return usingFixedFee
                ? fixedFee
                : Arrays.stream(HederaFunctionality.values())
                        .mapToLong(
                                op ->
                                        Optional.ofNullable(opFeeData.get(op))
                                                .map(
                                                        fd -> {
                                                            final var pricesForSubtype =
                                                                    fd.get(subType);
                                                            if (pricesForSubtype == null) {
                                                                return 0L;
                                                            } else {
                                                                return pricesForSubtype
                                                                                .getServicedata()
                                                                                .getMax()
                                                                        + pricesForSubtype
                                                                                .getNodedata()
                                                                                .getMax()
                                                                        + pricesForSubtype
                                                                                .getNetworkdata()
                                                                                .getMax();
                                                            }
                                                        })
                                                .orElse(0L))
                        .max()
                        .orElse(0L);
    }

    public long maxFeeTinyBars() {
        return maxFeeTinyBars(SubType.DEFAULT);
    }

    public long forOp(HederaFunctionality op, FeeData knownActivity) {
        return forOp(op, SubType.DEFAULT, knownActivity);
    }

    @FunctionalInterface
    public interface ActivityMetrics {
        FeeData compute(TransactionBody body, SigValueObj sigUsage) throws Throwable;
    }

    public long forActivityBasedOp(
            HederaFunctionality op,
            ActivityMetrics metricsCalculator,
            Transaction txn,
            int numPayerSigs)
            throws Throwable {
        FeeData activityMetrics = metricsFor(txn, numPayerSigs, metricsCalculator);
        final var subType = activityMetrics.getSubType();
        return forOp(op, subType, activityMetrics);
    }

    public long forActivityBasedOpWithDetails(
            HederaFunctionality op,
            ActivityMetrics metricsCalculator,
            Transaction txn,
            int numPayerSigs,
            AtomicReference<FeeObject> obs)
            throws Throwable {
        FeeData activityMetrics = metricsFor(txn, numPayerSigs, metricsCalculator);
        return forOpWithDetails(op, SubType.DEFAULT, activityMetrics, obs);
    }

    public long forActivityBasedOp(
            HederaFunctionality op,
            SubType subType,
            ActivityMetrics metricsCalculator,
            Transaction txn,
            int numPayerSigs)
            throws Throwable {
        FeeData activityMetrics = metricsFor(txn, numPayerSigs, metricsCalculator);
        return forOp(op, subType, activityMetrics);
    }

    private FeeData metricsFor(Transaction txn, int numPayerSigs, ActivityMetrics metricsCalculator)
            throws Throwable {
        SigValueObj sigUsage = sigUsageGiven(txn, numPayerSigs);
        TransactionBody body = CommonUtils.extractTransactionBody(txn);
        return metricsCalculator.compute(body, sigUsage);
    }

    private long forOp(HederaFunctionality op, SubType subType, FeeData knownActivity) {
        if (usingFixedFee) {
            return fixedFee;
        }
        try {
            Map<SubType, FeeData> activityPrices = opFeeData.get(op);
            return getTotalFeeforRequest(
                    activityPrices.get(subType), knownActivity, provider.rates());
        } catch (Throwable t) {
            log.warn("Unable to calculate fee for op {}, using max fee!", op, t);
        }
        return maxFeeTinyBars(subType);
    }

    public long forOpWithDetails(
            HederaFunctionality op,
            SubType subType,
            FeeData knownActivity,
            AtomicReference<FeeObject> obs) {
        try {
            final var activityPrices = opFeeData.get(op).get(subType);
            final var fees = getFeeObject(activityPrices, knownActivity, provider.rates());
            obs.set(fees);
            return getTotalFeeforRequest(activityPrices, knownActivity, provider.rates());
        } catch (Throwable t) {
            throw new IllegalArgumentException("Calculation not observable!", t);
        }
    }

    private SigValueObj sigUsageGiven(Transaction txn, int numPayerSigs) {
        int size = getSignatureSize(txn);
        int totalNumSigs = getSignatureCount(txn);
        return new SigValueObj(totalNumSigs, numPayerSigs, size);
    }

    public int tokenTransferUsageMultiplier() {
        return tokenTransferUsageMultiplier;
    }
}
