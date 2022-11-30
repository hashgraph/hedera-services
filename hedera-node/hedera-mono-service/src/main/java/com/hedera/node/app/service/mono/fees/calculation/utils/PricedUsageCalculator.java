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
package com.hedera.node.app.service.mono.fees.calculation.utils;

import static com.hedera.node.app.service.mono.keys.HederaKeyTraversal.numSimpleKeys;

import com.hedera.node.app.hapi.fees.calc.OverflowCheckingCalc;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.fees.annotations.GenericPriceMultiplier;
import com.hedera.node.app.service.mono.fees.congestion.FeeMultiplierSource;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
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
            final AccessorBasedUsages accessorBasedUsages,
            @GenericPriceMultiplier final FeeMultiplierSource feeMultiplierSource,
            final OverflowCheckingCalc calculator) {
        this.accessorBasedUsages = accessorBasedUsages;
        this.feeMultiplierSource = feeMultiplierSource;
        this.calculator = calculator;
    }

    public boolean supports(final HederaFunctionality function) {
        return accessorBasedUsages.supports(function);
    }

    public FeeObject inHandleFees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, handleScopedAccumulator);
    }

    public FeeObject extraHandleFees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, new UsageAccumulator());
    }

    private FeeObject fees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey,
            final UsageAccumulator accumulator) {
        final var sigUsage = accessor.usageGiven(numSimpleKeys(payerKey));

        accessorBasedUsages.assess(sigUsage, accessor, accumulator);

        return calculator.fees(
                accumulator, resourcePrices, rate, feeMultiplierSource.currentMultiplier(accessor));
    }

    UsageAccumulator getHandleScopedAccumulator() {
        return handleScopedAccumulator;
    }
}
