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
package com.hedera.services.fees;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricesAndFeesTest {
    private static final Instant now = Instant.ofEpochSecond(1_234_567L);
    private static final Timestamp timeNow = MiscUtils.asTimestamp(now);
    private static final long gasPriceTinybars = 123;
    private static final long sbhPriceTinybars = 456;
    private static final FeeComponents servicePrices =
            FeeComponents.newBuilder()
                    .setGas(gasPriceTinybars * 1000)
                    .setSbh(sbhPriceTinybars * 1000)
                    .build();
    private static final FeeData providerPrices =
            FeeData.newBuilder().setServicedata(servicePrices).build();
    private final FeeComponents mockFees =
            FeeComponents.newBuilder()
                    .setMax(1_234_567L)
                    .setGas(5_000_000L)
                    .setBpr(1_000_000L)
                    .setBpt(2_000_000L)
                    .setRbh(3_000_000L)
                    .setSbh(4_000_000L)
                    .build();
    private final FeeData mockFeeData =
            FeeData.newBuilder()
                    .setNetworkdata(mockFees)
                    .setNodedata(mockFees)
                    .setServicedata(mockFees)
                    .setSubType(SubType.DEFAULT)
                    .build();
    private final FeeData defaultCurrentPrices = mockFeeData;
    private final ExchangeRate currentRate =
            ExchangeRate.newBuilder().setCentEquiv(22).setHbarEquiv(1).build();

    @Mock private HbarCentExchange exchange;
    @Mock private UsagePricesProvider usagePrices;
    @Mock private LivePricesSource livePricesSource;
    private PricesAndFeesImpl subject;

    @BeforeEach
    void setUp() {
        usagePrices = mock(UsagePricesProvider.class);
        exchange = mock(HbarCentExchange.class);
        subject = new PricesAndFeesImpl(exchange, usagePrices, livePricesSource);
    }

    @Test
    void getsCurrentGasPriceInTinyCents() {
        given(subject.currentGasPrice(now, CryptoCreate)).willReturn(123L);

        ToLongFunction<FeeComponents> resourcePriceFn = FeeComponents::getGas;
        final var expected = resourcePriceFn.applyAsLong(providerPrices.getServicedata()) / 1000;
        assertEquals(expected, subject.currentGasPrice(now, CryptoCreate));
    }

    @Test
    void estimatesFutureGasPriceInTinybars() {
        given(exchange.rate(timeNow)).willReturn(currentRate);
        given(usagePrices.defaultPricesGiven(CryptoCreate, timeNow))
                .willReturn(defaultCurrentPrices);

        // and:
        long expected =
                getTinybarsFromTinyCents(currentRate, mockFees.getGas() / FEE_DIVISOR_FACTOR);

        // when:
        long actual = subject.estimatedGasPriceInTinybars(CryptoCreate, timeNow);

        // then:
        assertEquals(expected, actual);
    }

    @Test
    void getsCurrentFutureGasPriceInTinyCents() {
        given(subject.defaultPricesGiven(ContractCall, timeNow)).willReturn(providerPrices);

        ToLongFunction<FeeComponents> resourcePriceFn = FeeComponents::getGas;
        final var expected = resourcePriceFn.applyAsLong(providerPrices.getServicedata());
        assertEquals(
                expected,
                subject.defaultPricesGiven(ContractCall, timeNow).getServicedata().getGas());
    }

    @Test
    void estimatesCentEquiv() {
        given(subject.rate(timeNow)).willReturn(currentRate);

        // and:
        long expected = currentRate.getCentEquiv();

        // when:
        long actual = subject.rate(timeNow).getCentEquiv();

        // then:
        assertEquals(expected, actual);
    }
}
