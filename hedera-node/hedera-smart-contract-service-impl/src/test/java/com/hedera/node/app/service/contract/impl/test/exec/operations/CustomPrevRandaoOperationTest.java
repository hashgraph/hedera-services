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

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.operations.CustomPrevRandaoOperation;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomPrevRandaoOperationTest {
    @Mock
    private EVM evm;

    @Mock
    private MessageFrame frame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    private CustomPrevRandaoOperation subject;

    @BeforeEach
    void setup() {
        given(gasCalculator.getBaseTierGasCost()).willReturn(2L);
        subject = new CustomPrevRandaoOperation(gasCalculator);
    }

    @Test
    void insufficientRemainingGasGetsOOG() {
        given(frame.getRemainingGas()).willReturn(1L);

        final var result = subject.execute(frame, evm);

        assertEquals(INSUFFICIENT_GAS, result.getHaltReason());
    }

    @Test
    void returnsLessThan32BytesInEntirety() {
        final var littleEntropy = Bytes.of(1, 2, 3);
        given(frame.getRemainingGas()).willReturn(4L);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.entropy()).willReturn(littleEntropy);

        final var result = subject.execute(frame, evm);

        assertEquals(2L, result.getGasCost());
        assertNull(result.getHaltReason());
        verify(frame).pushStackItem(littleEntropy);
    }

    @Test
    void slices32BytesIfMoreEntropyAvailable() {
        final var bigEntropy = Bytes.of(
                1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3);
        given(frame.getRemainingGas()).willReturn(4L);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.entropy()).willReturn(bigEntropy);

        final var result = subject.execute(frame, evm);

        assertEquals(2L, result.getGasCost());
        assertNull(result.getHaltReason());
        verify(frame).pushStackItem(bigEntropy.slice(0, 32));
    }
}
