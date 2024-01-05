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

package com.hedera.node.app.service.contract.impl.test.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.operations.CustomChainIdOperation;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.Deque;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomChainIdOperationTest {
    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private EVM evm;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private MessageFrame messageFrame;

    private CustomChainIdOperation subject;

    @BeforeEach
    void setup() {
        given(gasCalculator.getBaseTierGasCost()).willReturn(123L);
        subject = new CustomChainIdOperation(gasCalculator);
    }

    @Test
    void usesContractsConfigFromContext() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("hedera.allowances.maxAccountLimit", 2)
                .getOrCreateConfig();
        given(messageFrame.getMessageFrameStack()).willReturn(stack);
        given(stack.isEmpty()).willReturn(true);
        given(messageFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        given(messageFrame.getRemainingGas()).willReturn(Long.MAX_VALUE);
        final var contractsConfig = config.getConfigData(ContractsConfig.class);
        final var expectedStackItem = Bytes32.fromHexStringLenient(Integer.toString(contractsConfig.chainId(), 16));
        final var expectedResult = new Operation.OperationResult(123L, null);
        final var actualResult = subject.execute(messageFrame, evm);
        TestHelpers.assertSameResult(actualResult, expectedResult);
        verify(messageFrame).pushStackItem(expectedStackItem);
    }

    @Test
    void checksForOOG() {
        final var expectedResult = new Operation.OperationResult(123L, ExceptionalHaltReason.INSUFFICIENT_GAS);
        final var actualResult = subject.execute(messageFrame, evm);
        TestHelpers.assertSameResult(actualResult, expectedResult);
    }
}
