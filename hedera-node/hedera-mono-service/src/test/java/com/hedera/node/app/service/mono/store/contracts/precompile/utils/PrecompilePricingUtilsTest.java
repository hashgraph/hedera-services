/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts.precompile.utils;

import static com.hedera.node.app.hapi.fees.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.store.contracts.precompile.Precompile;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import javax.inject.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrecompilePricingUtilsTest {

    private static final long COST = 36;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private Provider<FeeCalculator> feeCalculatorProvider;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private StateView stateView;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private Precompile precompile;

    @Mock
    private TransactionBody.Builder transactionBody;

    private final long minimumGasCost = 100;
    private final long gasPrice = 77;

    @Test
    void failsToLoadCanonicalPrices() throws IOException {
        given(assetLoader.loadCanonicalPrices()).willThrow(IOException.class);
        assertThrows(
                PrecompilePricingUtils.CanonicalOperationsUnloadableException.class,
                () -> new PrecompilePricingUtils(
                        assetLoader, exchange, feeCalculatorProvider, resourceCosts, stateView, accessorFactory));
    }

    @Test
    void calculatesMinimumPrice() throws IOException {
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        given(exchange.rate(timestamp)).willReturn(exchangeRate);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);

        final PrecompilePricingUtils subject = new PrecompilePricingUtils(
                assetLoader, exchange, feeCalculatorProvider, resourceCosts, stateView, accessorFactory);

        final long price = subject.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.ASSOCIATE, timestamp);

        assertEquals(
                USD_TO_TINYCENTS
                        .multiply(BigDecimal.valueOf(COST * HBAR_RATE / CENTS_RATE))
                        .longValue(),
                price);
    }

    @Test
    void computeViewFunctionGasMinimumTest() throws IOException {
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        FeeObject feeObject = new FeeObject(1, 2, 3);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculatorProvider.get()).willReturn(feeCalculator);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(feeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(gasPrice);

        final PrecompilePricingUtils subject = new PrecompilePricingUtils(
                assetLoader, exchange, feeCalculatorProvider, resourceCosts, stateView, accessorFactory);

        // minimum gas cost should apply
        final long price = subject.computeViewFunctionGas(timestamp, minimumGasCost);

        assertEquals(minimumGasCost, price);
    }

    @Test
    void computeViewFunctionGasTest() throws IOException {
        final long nodeFee = 10000;
        final long networkFee = 20000;
        final long serviceFee = 30000;
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        FeeObject feeObject = new FeeObject(nodeFee, networkFee, serviceFee);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculatorProvider.get()).willReturn(feeCalculator);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(feeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(gasPrice);

        final PrecompilePricingUtils subject = new PrecompilePricingUtils(
                assetLoader, exchange, feeCalculatorProvider, resourceCosts, stateView, accessorFactory);

        final long price = subject.computeViewFunctionGas(timestamp, minimumGasCost);
        final long expectedPrice = (nodeFee + networkFee + serviceFee + gasPrice - 1L) / gasPrice;

        // The minimum gas cost does not apply.  The cost is the expected price plus 20%.
        assertEquals(expectedPrice + expectedPrice / 5, price);
    }

    @Test
    void computeGasRequirementMinimumTest() throws IOException {
        final long nodeFee = 10000;
        final long networkFee = 20000;
        final long serviceFee = 30000;
        final long gasInTinybars = 10000;
        final long minimumFeeInTinybars = 20000;
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        FeeObject feeObject = new FeeObject(nodeFee, networkFee, serviceFee);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculatorProvider.get()).willReturn(feeCalculator);
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(gasPrice);
        given(precompile.getMinimumFeeInTinybars(timestamp)).willReturn(minimumFeeInTinybars);

        final PrecompilePricingUtils subject = new PrecompilePricingUtils(
                assetLoader, exchange, feeCalculatorProvider, resourceCosts, stateView, accessorFactory);
        final var subjectSpy = spy(subject);

        doReturn(gasInTinybars).when(subjectSpy).gasFeeInTinybars(any(), any(), any());

        final long price = subjectSpy.computeGasRequirement(timestamp.getSeconds(), precompile, transactionBody);
        final long expectedPrice = (minimumFeeInTinybars + gasPrice - 1L) / gasPrice;

        // The minimum gas should apply
        assertEquals(expectedPrice + expectedPrice / 5, price);
    }

    @Test
    void computeGasRequirementTest() throws IOException {
        final long nodeFee = 10000;
        final long networkFee = 20000;
        final long serviceFee = 30000;
        final long gasInTinybars = 10000;
        final long minimumFeeInTinybars = 500;
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        FeeObject feeObject = new FeeObject(nodeFee, networkFee, serviceFee);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculatorProvider.get()).willReturn(feeCalculator);
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(gasPrice);
        given(precompile.getMinimumFeeInTinybars(timestamp)).willReturn(minimumFeeInTinybars);

        final PrecompilePricingUtils subject = new PrecompilePricingUtils(
                assetLoader, exchange, feeCalculatorProvider, resourceCosts, stateView, accessorFactory);
        final var subjectSpy = spy(subject);

        doReturn(gasInTinybars).when(subjectSpy).gasFeeInTinybars(any(), any(), any());

        final long price = subjectSpy.computeGasRequirement(timestamp.getSeconds(), precompile, transactionBody);
        final long expectedPrice = (gasInTinybars + gasPrice - 1L) / gasPrice;

        // The minimum gas cost does not apply.  The cost is the expected price plus 20%.
        assertEquals(expectedPrice + expectedPrice / 5, price);
    }
}
