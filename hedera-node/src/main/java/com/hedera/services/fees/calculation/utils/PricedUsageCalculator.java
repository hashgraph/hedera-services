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
package com.hedera.services.fees.calculation.utils;

import static com.hedera.services.keys.HederaKeyTraversal.numSimpleKeys;

import com.hedera.services.calc.OverflowCheckingCalc;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.fee.FeeObject;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PricedUsageCalculator {
    private final UsageAccumulator handleScopedAccumulator = new UsageAccumulator();

    private final AccessorBasedUsages accessorBasedUsages;
    private final FeeMultiplierSource feeMultiplierSource;
    private final OverflowCheckingCalc calculator;

    @Inject
    public PricedUsageCalculator(
            AccessorBasedUsages accessorBasedUsages,
            FeeMultiplierSource feeMultiplierSource,
            OverflowCheckingCalc calculator) {
        this.accessorBasedUsages = accessorBasedUsages;
        this.feeMultiplierSource = feeMultiplierSource;
        this.calculator = calculator;
    }

    public boolean supports(HederaFunctionality function) {
        return accessorBasedUsages.supports(function);
    }

    public FeeObject inHandleFees(
            TxnAccessor accessor, FeeData resourcePrices, ExchangeRate rate, JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, handleScopedAccumulator);
    }

    public FeeObject extraHandleFees(
            TxnAccessor accessor, FeeData resourcePrices, ExchangeRate rate, JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, new UsageAccumulator());
    }

    private FeeObject fees(
            TxnAccessor accessor,
            FeeData resourcePrices,
            ExchangeRate rate,
            JKey payerKey,
            UsageAccumulator accumulator) {
        final var sigUsage = accessor.usageGiven(numSimpleKeys(payerKey));

        accessorBasedUsages.assess(sigUsage, accessor, accumulator);

        return calculator.fees(
                accumulator, resourcePrices, rate, feeMultiplierSource.currentMultiplier(accessor));
    }

    UsageAccumulator getHandleScopedAccumulator() {
        return handleScopedAccumulator;
    }
}
