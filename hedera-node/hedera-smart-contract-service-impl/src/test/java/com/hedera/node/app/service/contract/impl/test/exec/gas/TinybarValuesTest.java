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

package com.hedera.node.app.service.contract.impl.test.exec.gas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TinybarValuesTest {
    private static final ExchangeRate RATE_TO_USE =
            ExchangeRate.newBuilder().hbarEquiv(2).centEquiv(14).build();
    private static final long RBH_FEE_SCHEDULE_RICE = 77_000L;
    private static final long GAS_FEE_SCHEDULE_PRICE = 777_000L;
    private static final FeeData PRICES_TO_USE = FeeData.newBuilder()
            .servicedata(FeeComponents.newBuilder().rbh(RBH_FEE_SCHEDULE_RICE).gas(GAS_FEE_SCHEDULE_PRICE))
            .build();

    @Mock
    private FunctionalityResourcePrices resourcePrices;

    private TinybarValues subject;

    @BeforeEach
    void setUp() {
        subject = new TinybarValues(RATE_TO_USE, resourcePrices);
    }

    @Test
    void computesExchangeRateAsExpected() {
        final var tinycents = 77l;
        assertEquals(11L, subject.asTinybars(tinycents));
    }

    @Test
    void computesExpectedRbhServicePrice() {
        given(resourcePrices.basePrices()).willReturn(PRICES_TO_USE);
        final var expectedRbhPrice = RBH_FEE_SCHEDULE_RICE / (7 * 1000);
        assertEquals(expectedRbhPrice, subject.serviceRbhPrice());
    }

    @Test
    void computesExpectedGasServicePrice() {
        given(resourcePrices.basePrices()).willReturn(PRICES_TO_USE);
        final var expectedGasPrice = GAS_FEE_SCHEDULE_PRICE / (7 * 1000);
        assertEquals(expectedGasPrice, subject.serviceGasPrice());
    }
}
