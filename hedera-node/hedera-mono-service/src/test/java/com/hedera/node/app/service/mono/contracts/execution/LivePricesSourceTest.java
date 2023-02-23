/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.execution;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getTinybarsFromTinyCents;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertExchangeRateFromProtoToDto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertFeeDataFromProtoToDto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertHederaFunctionalityFromProtoToDto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertTimestampFromProtoToDto;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProviderImpl;
import com.hedera.node.app.service.evm.utils.codec.HederaFunctionality;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LivePricesSourceTest {
    private static final Instant now = Instant.ofEpochSecond(1_234_567L);
    private static final Timestamp timeNow = MiscUtils.asTimestamp(now);
    private static final long gasPriceTinybars = 123;
    private static final long sbhPriceTinybars = 456;
    private static final FeeComponents servicePrices = FeeComponents.newBuilder()
            .setGas(gasPriceTinybars * 1000)
            .setSbh(sbhPriceTinybars * 1000)
            .build();
    private static final FeeData providerPrices =
            FeeData.newBuilder().setServicedata(servicePrices).build();
    private static final ExchangeRate activeRate =
            ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(12).build();
    private static final long reasonableMultiplier = 7;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private UsagePricesProvider usagePrices;

    @Mock
    private MultiplierSources multiplierSources;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private PricesAndFeesProviderImpl pricesAndFeesProvider;

    @Mock
    private TxnAccessor accessor;

    @Mock
    private FeeResourcesLoaderImpl feeResourcesLoader;

    private LivePricesSource subject;

    @BeforeEach
    void setUp() {
        subject = new LivePricesSource(exchange, usagePrices, multiplierSources, txnCtx, feeResourcesLoader);
    }

    @Test
    void getsExpectedGasPriceWithReasonableMultiplier() {
        givenCollabsWithMultiplier(reasonableMultiplier);

        final var expected = getTinybarsFromTinyCents(activeRate, gasPriceTinybars) * reasonableMultiplier;

        assertEquals(
                expected,
                pricesAndFeesProvider.currentGasPrice(now, convertHederaFunctionalityFromProtoToDto(ContractCall)));
    }

    @Test
    void getsCurrentGasPriceInTinyCents() {
        given(pricesAndFeesProvider.defaultPricesGiven(
                        convertHederaFunctionalityFromProtoToDto(ContractCall),
                        convertTimestampFromProtoToDto(timeNow)))
                .willReturn(convertFeeDataFromProtoToDto(providerPrices));

        ToLongFunction<FeeComponents> resourcePriceFn = FeeComponents::getGas;
        final var expected = resourcePriceFn.applyAsLong(providerPrices.getServicedata()) / 1000;

        assertEquals(
                expected,
                pricesAndFeesProvider.currentGasPriceInTinycents(
                        now, convertHederaFunctionalityFromProtoToDto(ContractCall)));
    }

    private void givenCollabsWithMultiplier(final long multiplier) {
        given(pricesAndFeesProvider.rate(convertTimestampFromProtoToDto(timeNow)))
                .willReturn(convertExchangeRateFromProtoToDto(activeRate));
        given(pricesAndFeesProvider.defaultPricesGiven(
                        HederaFunctionality.ContractCall, convertTimestampFromProtoToDto(timeNow)))
                .willReturn(convertFeeDataFromProtoToDto(providerPrices));
        given(multiplierSources.maxCurrentMultiplier(accessor)).willReturn(multiplier);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
