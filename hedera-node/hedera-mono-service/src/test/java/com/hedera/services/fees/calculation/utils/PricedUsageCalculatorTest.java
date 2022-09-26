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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.calc.OverflowCheckingCalc;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricedUsageCalculatorTest {
    private final int sigMapSize = 123;
    private final int numSigPairs = 3;
    private final long multiplier = 2;
    private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
    private final ExchangeRate mockRate =
            ExchangeRate.newBuilder().setCentEquiv(22).setHbarEquiv(1).build();
    private final FeeComponents mockComps =
            FeeComponents.newBuilder()
                    .setMax(1L)
                    .setGas(5L)
                    .setBpr(1L)
                    .setBpt(2L)
                    .setRbh(3L)
                    .setSbh(4L)
                    .build();
    private FeeData mockPrices =
            FeeData.newBuilder()
                    .setNetworkdata(mockComps)
                    .setNodedata(mockComps)
                    .setServicedata(mockComps)
                    .build();
    private FeeObject mockFees = new FeeObject(1L, 2L, 3L);

    @Mock private TxnAccessor accessor;
    @Mock private AccessorBasedUsages accessorBasedUsages;
    @Mock private FeeMultiplierSource feeMultiplierSource;
    @Mock private OverflowCheckingCalc calculator;

    private PricedUsageCalculator subject;

    @BeforeEach
    void setUp() {
        subject = new PricedUsageCalculator(accessorBasedUsages, feeMultiplierSource, calculator);
    }

    @Test
    void delegatesSupports() {
        given(accessorBasedUsages.supports(HederaFunctionality.CryptoTransfer)).willReturn(true);

        // then:
        Assertions.assertTrue(subject.supports(HederaFunctionality.CryptoTransfer));
    }

    @Test
    void computesInHandleAsExpected() {
        // setup:
        final var inHandleAccum = subject.getHandleScopedAccumulator();
        final var su = new SigUsage(numSigPairs, sigMapSize, numSimpleKeys(payerKey));

        given(accessor.usageGiven(su.numPayerKeys()))
                .willReturn(new SigUsage(numSigPairs, sigMapSize, 1));
        given(feeMultiplierSource.currentMultiplier(accessor)).willReturn(multiplier);
        given(calculator.fees(inHandleAccum, mockPrices, mockRate, multiplier))
                .willReturn(mockFees);

        // when:
        final var actual = subject.inHandleFees(accessor, mockPrices, mockRate, payerKey);

        // then:
        verify(accessorBasedUsages).assess(su, accessor, inHandleAccum);
        assertEquals(mockFees, actual);
    }

    @Test
    void computesExtraHandleAsExpected() {
        // setup:
        ArgumentCaptor<UsageAccumulator> feesCaptor =
                ArgumentCaptor.forClass(UsageAccumulator.class);
        ArgumentCaptor<UsageAccumulator> assessCaptor =
                ArgumentCaptor.forClass(UsageAccumulator.class);

        final var inHandleAccum = subject.getHandleScopedAccumulator();
        final var su = new SigUsage(numSigPairs, sigMapSize, numSimpleKeys(payerKey));
        given(accessor.usageGiven(su.numPayerKeys()))
                .willReturn(new SigUsage(numSigPairs, sigMapSize, 1));
        given(feeMultiplierSource.currentMultiplier(accessor)).willReturn(multiplier);
        given(
                        calculator.fees(
                                feesCaptor.capture(),
                                eq(mockPrices),
                                eq(mockRate),
                                longThat(l -> l == multiplier)))
                .willReturn(mockFees);

        // when:
        final var actual = subject.extraHandleFees(accessor, mockPrices, mockRate, payerKey);

        // then:
        verify(accessorBasedUsages).assess(eq(su), eq(accessor), assessCaptor.capture());
        assertEquals(mockFees, actual);
        assertSame(feesCaptor.getValue(), assessCaptor.getValue());
        assertNotSame(inHandleAccum, feesCaptor.getValue());
    }
}
