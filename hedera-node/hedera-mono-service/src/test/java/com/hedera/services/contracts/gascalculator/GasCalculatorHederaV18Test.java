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
package com.hedera.services.contracts.gascalculator;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.HederaBlockValues;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GasCalculatorHederaV18Test {

    @Mock private GlobalDynamicProperties properties;
    @Mock private HbarCentExchange hbarCentExchange;
    @Mock private UsagePricesProvider usagePricesProvider;
    @Mock private MerkleNetworkContext merkleNetworkContext;

    private GasCalculatorHederaV18 gasCalculatorHedera;

    @BeforeEach
    void setup() {
        gasCalculatorHedera =
                new GasCalculatorHederaV18(properties, usagePricesProvider, hbarCentExchange);
    }

    @Test
    void assertAndVerifyLogOperationGasCost() {
        final var messageFrame = mock(MessageFrame.class);
        final var consensusTime = 123L;
        final var functionality = HederaFunctionality.ContractCreate;
        final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
        final var returningDeque = new ArrayDeque<MessageFrame>() {};
        returningDeque.add(messageFrame);

        final var rbh = 20000L;
        final var feeComponents = FeeComponents.newBuilder().setRbh(rbh);
        final var feeData = FeeData.newBuilder().setServicedata(feeComponents).build();
        final var blockConsTime = Instant.ofEpochSecond(consensusTime);
        final var blockNo = 123L;

        given(messageFrame.getGasPrice()).willReturn(Wei.of(2000L));
        given(messageFrame.getBlockValues())
                .willReturn(new HederaBlockValues(10L, blockNo, blockConsTime));
        given(messageFrame.getContextVariable("HederaFunctionality")).willReturn(functionality);
        given(messageFrame.getMessageFrameStack()).willReturn(returningDeque);

        given(usagePricesProvider.defaultPricesGiven(functionality, timestamp)).willReturn(feeData);
        given(hbarCentExchange.rate(timestamp))
                .willReturn(ExchangeRate.newBuilder().setHbarEquiv(2000).setCentEquiv(200).build());
        given(properties.cacheRecordsTtl()).willReturn(1000000);
        assertEquals(28L, gasCalculatorHedera.logOperationGasCost(messageFrame, 1L, 2L, 3));
        verify(messageFrame).getGasPrice();
        verify(messageFrame).getBlockValues();
        verify(messageFrame).getContextVariable("HederaFunctionality");
        verify(messageFrame).getMessageFrameStack();
        verify(usagePricesProvider).defaultPricesGiven(functionality, timestamp);
        verify(hbarCentExchange).rate(timestamp);
    }

    @Test
    void assertCodeDepositGasCostIsZero() {
        assertEquals(0L, gasCalculatorHedera.codeDepositGasCost(10));
    }

    @Test
    void assertTransactionIntrinsicGasCost() {
        assertEquals(0L, gasCalculatorHedera.transactionIntrinsicGasCost(Bytes.EMPTY, true));
    }

    @Test
    void assertGetBalanceOperationGasCost() {
        assertEquals(20L, gasCalculatorHedera.getBalanceOperationGasCost());
    }

    @Test
    void assertExpOperationByteGasCost() {
        assertEquals(10L, gasCalculatorHedera.expOperationByteGasCost());
    }

    @Test
    void assertExtCodeBaseGasCost() {
        assertEquals(20L, gasCalculatorHedera.extCodeBaseGasCost());
    }

    @Test
    void assertCallOperationBaseGasCost() {
        assertEquals(40L, gasCalculatorHedera.callOperationBaseGasCost());
    }

    @Test
    void assertGetExtCodeSizeOperationGasCost() {
        assertEquals(20L, gasCalculatorHedera.getExtCodeSizeOperationGasCost());
    }

    @Test
    void assertExtCodeHashOperationGasCost() {
        assertEquals(400L, gasCalculatorHedera.extCodeHashOperationGasCost());
    }

    @Test
    void assertSelfDestructOperationGasCost() {
        Account recipient = mock(Account.class);
        assertEquals(0L, gasCalculatorHedera.selfDestructOperationGasCost(recipient, Wei.of(10L)));
    }

    @Test
    void assertGetSloadOperationGasCost() {
        assertEquals(50L, gasCalculatorHedera.getSloadOperationGasCost());
    }
}
