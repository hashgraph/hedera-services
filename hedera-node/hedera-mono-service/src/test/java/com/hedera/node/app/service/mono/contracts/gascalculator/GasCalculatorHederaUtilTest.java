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

package com.hedera.node.app.service.mono.contracts.gascalculator;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertExchangeRateFromDtoToProto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertFeeDataFromDtoToProto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertHederaFunctionalityFromProtoToDto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertTimestampFromProtoToDto;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.exchangeRate;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.feeData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProviderImpl;
import com.hedera.node.app.service.evm.fee.codec.SubType;
import com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GasCalculatorHederaUtilTest {

    @Mock
    private PricesAndFeesProviderImpl pricesAndFeesProvider;

    private final Map<SubType, com.hedera.node.app.service.evm.fee.codec.FeeData> feeMap = new HashMap<>();

    private final int hbarEquiv = 1000;
    private final int centEquiv = 100;
    private MockedStatic<FeeConverter> feeConverter;

    @BeforeEach
    void setUp() {
        feeMap.put(SubType.DEFAULT, feeData);
        feeConverter = Mockito.mockStatic(FeeConverter.class);
    }

    @AfterEach
    void closeMocks() {
        if (!feeConverter.isClosed()) {
            feeConverter.close();
        }
    }

    @Test
    void assertRamByteHoursTinyBarsGiven() {
        var expectedRamResult = hbarEquiv / centEquiv;
        var consensusTime = Instant.now().getEpochSecond();
        final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
        var feeDataProto = mock(FeeData.class);
        var exchangeRateProto = mock(ExchangeRate.class);
        given(feeDataProto.getServicedata()).willReturn(mock(FeeComponents.class));
        given(feeDataProto.getServicedata().getRbh()).willReturn(1000L);

        feeConverter
                .when(() -> convertHederaFunctionalityFromProtoToDto(HederaFunctionality.ContractCall))
                .thenReturn(com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.ContractCall);
        feeConverter
                .when(() -> convertTimestampFromProtoToDto(timestamp))
                .thenReturn(new com.hedera.node.app.service.evm.utils.codec.Timestamp(
                        timestamp.getSeconds(), timestamp.getNanos()));
        feeConverter.when(() -> convertFeeDataFromDtoToProto(feeData)).thenReturn(feeDataProto);
        feeConverter.when(() -> convertExchangeRateFromDtoToProto(exchangeRate)).thenReturn(exchangeRateProto);

        given(pricesAndFeesProvider.defaultPricesGiven(
                        com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.ContractCall,
                        new com.hedera.node.app.service.evm.utils.codec.Timestamp(
                                timestamp.getSeconds(), timestamp.getNanos())))
                .willReturn(feeData);
        given(pricesAndFeesProvider.rate(new com.hedera.node.app.service.evm.utils.codec.Timestamp(
                        timestamp.getSeconds(), timestamp.getNanos())))
                .willReturn(exchangeRate);
        given(exchangeRateProto.getHbarEquiv()).willReturn(hbarEquiv);
        given(exchangeRateProto.getCentEquiv()).willReturn(centEquiv);

        assertEquals(
                expectedRamResult,
                GasCalculatorHederaUtil.ramByteHoursTinyBarsGiven(
                        pricesAndFeesProvider, consensusTime, HederaFunctionality.ContractCall));
        verify(pricesAndFeesProvider).rate(convertTimestampFromProtoToDto(timestamp));
        verify(pricesAndFeesProvider)
                .defaultPricesGiven(
                        convertHederaFunctionalityFromProtoToDto(HederaFunctionality.ContractCall),
                        convertTimestampFromProtoToDto(timestamp));
    }

    @Test
    void assertCalculateLogSize() {
        var numberOfTopics = 3;
        var dataSize = 10L;
        assertEquals(386, GasCalculatorHederaUtil.calculateLogSize(numberOfTopics, dataSize));
    }

    @Test
    void assertCalculateStorageGasNeeded() {
        var durationInSeconds = 10L;
        var byteHourCostIntinybars = 1000L;
        var gasPrice = 1234L;
        var storageCostTinyBars = (durationInSeconds * byteHourCostIntinybars) / 3600;
        var expectedResult = Math.round((double) storageCostTinyBars / (double) gasPrice);
        assertEquals(
                expectedResult,
                GasCalculatorHederaUtil.calculateStorageGasNeeded(
                        0, durationInSeconds, byteHourCostIntinybars, gasPrice));
    }

    @Test
    void assertAndVerifyLogOperationGasCost() {
        final var messageFrame = mock(MessageFrame.class);
        final var consensusTime = 123L;
        final var functionality = HederaFunctionality.ContractCreate;
        final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
        final var returningDeque = new ArrayDeque<MessageFrame>() {};
        returningDeque.add(messageFrame);

        final var rbh = 2000000L;
        final var feeComponentsDto =
                new com.hedera.node.app.service.evm.fee.codec.FeeComponents(0, 0, 0, 0, 0, rbh, 0, 0, 0, 0, 0);
        final var blockConsTime = Instant.ofEpochSecond(consensusTime);
        final var blockNo = 123L;
        var feeDataProto = mock(FeeData.class);
        var exchangeRateProto = mock(ExchangeRate.class);
        given(exchangeRateProto.getHbarEquiv()).willReturn(hbarEquiv);
        given(exchangeRateProto.getCentEquiv()).willReturn(centEquiv);

        given(feeDataProto.getServicedata()).willReturn(mock(FeeComponents.class));
        given(feeDataProto.getServicedata().getRbh()).willReturn(20000L);

        given(messageFrame.getGasPrice()).willReturn(Wei.of(2000L));
        given(messageFrame.getBlockValues()).willReturn(new HederaBlockValues(10L, blockNo, blockConsTime));
        given(messageFrame.getContextVariable("HederaFunctionality")).willReturn(functionality);
        given(messageFrame.getMessageFrameStack()).willReturn(returningDeque);

        final com.hedera.node.app.service.evm.fee.codec.FeeComponents feeComponents =
                new com.hedera.node.app.service.evm.fee.codec.FeeComponents(
                        100, 1000, 0, 2000, 0, 20000000, 0, 560000, 0, 0, 0);
        final com.hedera.node.app.service.evm.fee.codec.FeeData feeData =
                new com.hedera.node.app.service.evm.fee.codec.FeeData(
                        feeComponents, feeComponents, feeComponents, SubType.DEFAULT);

        feeConverter
                .when(() -> convertTimestampFromProtoToDto(timestamp))
                .thenReturn(new com.hedera.node.app.service.evm.utils.codec.Timestamp(
                        timestamp.getSeconds(), timestamp.getNanos()));
        feeConverter
                .when(() -> convertHederaFunctionalityFromProtoToDto(HederaFunctionality.ContractCreate))
                .thenReturn(com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.ContractCreate);
        feeConverter.when(() -> convertFeeDataFromDtoToProto(feeData)).thenReturn(feeDataProto);
        feeConverter.when(() -> convertExchangeRateFromDtoToProto(exchangeRate)).thenReturn(exchangeRateProto);

        given(pricesAndFeesProvider.defaultPricesGiven(
                        com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.ContractCreate,
                        new com.hedera.node.app.service.evm.utils.codec.Timestamp(
                                timestamp.getSeconds(), timestamp.getNanos())))
                .willReturn(feeData);
        given(pricesAndFeesProvider.rate(new com.hedera.node.app.service.evm.utils.codec.Timestamp(
                        timestamp.getSeconds(), timestamp.getNanos())))
                .willReturn(exchangeRate);

        assertEquals(
                28L,
                GasCalculatorHederaUtil.logOperationGasCost(pricesAndFeesProvider, messageFrame, 1000000, 1L, 2L, 3));
        verify(messageFrame).getGasPrice();
        verify(messageFrame).getBlockValues();
        verify(messageFrame).getContextVariable("HederaFunctionality");
        verify(messageFrame).getMessageFrameStack();
        verify(pricesAndFeesProvider)
                .defaultPricesGiven(
                        convertHederaFunctionalityFromProtoToDto(functionality),
                        convertTimestampFromProtoToDto(timestamp));
        verify(pricesAndFeesProvider).rate(convertTimestampFromProtoToDto(timestamp));
    }
}
