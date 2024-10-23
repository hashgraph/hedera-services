/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TinybarValuesTest {
    private static final int CENTS_PER_HBAR = 7;
    private static final ExchangeRate RATE_TO_USE =
            ExchangeRate.newBuilder().hbarEquiv(1).centEquiv(CENTS_PER_HBAR).build();
    private static final long RBH_FEE_SCHEDULE_PRICE = 77_000L;
    private static final long TOP_LEVEL_GAS_FEE_SCHEDULE_PRICE = 777_000L;
    private static final long CHILD_TRANSACTION_GAS_FEE_SCHEDULE_PRICE = 777_777L;
    private static final FeeData TOP_LEVEL_PRICES_TO_USE = FeeData.newBuilder()
            .servicedata(FeeComponents.newBuilder().rbh(RBH_FEE_SCHEDULE_PRICE).gas(TOP_LEVEL_GAS_FEE_SCHEDULE_PRICE))
            .build();
    private static final FeeData CHILD_TRANSACTION_PRICES_TO_USE = FeeData.newBuilder()
            .servicedata(FeeComponents.newBuilder().gas(CHILD_TRANSACTION_GAS_FEE_SCHEDULE_PRICE))
            .build();

    private final FunctionalityResourcePrices resourcePrices =
            new FunctionalityResourcePrices(TOP_LEVEL_PRICES_TO_USE, 1);
    private final FunctionalityResourcePrices childResourcePrices =
            new FunctionalityResourcePrices(CHILD_TRANSACTION_PRICES_TO_USE, 2);

    private TinybarValues subject;
    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();
    private final ContractsConfig contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);

    @BeforeEach
    void setUp() {
        subject = TinybarValues.forTransactionWith(RATE_TO_USE, contractsConfig, resourcePrices, childResourcePrices);
    }

    @Test
    void computesExchangeRateAsExpected() {
        final var tinycents = 77L;
        withTransactionSubject();
        assertEquals(tinycents / CENTS_PER_HBAR, subject.asTinybars(tinycents));
    }

    @Test
    void computesExpectedRbhServicePrice() {
        withTransactionSubject();
        assertEquals(RBH_FEE_SCHEDULE_PRICE, subject.topLevelTinycentRbhPrice());
    }

    @Test
    void computesExpectedGasServicePrice() {
        withTransactionSubject();
        final var expectedGasPrice = TOP_LEVEL_GAS_FEE_SCHEDULE_PRICE / (CENTS_PER_HBAR * 1000);
        assertEquals(expectedGasPrice, subject.topLevelTinybarGasPrice());
    }

    @Test
    void computesExpectedChildGasServicePrice() {
        withTransactionSubject();
        final var expectedGasPrice = 2 * CHILD_TRANSACTION_GAS_FEE_SCHEDULE_PRICE / (CENTS_PER_HBAR * 1000);
        assertEquals(expectedGasPrice, subject.childTransactionTinybarGasPrice());
    }

    @Test
    void querySubjectRefusesToComputeChildGasServicePrice() {
        withQuerySubject();
        assertThrows(IllegalStateException.class, subject::childTransactionTinybarGasPrice);
    }

    private void withTransactionSubject() {
        subject = TinybarValues.forTransactionWith(RATE_TO_USE, contractsConfig, resourcePrices, childResourcePrices);
    }

    private void withQuerySubject() {
        subject = TinybarValues.forQueryWith(RATE_TO_USE, contractsConfig);
    }
}
