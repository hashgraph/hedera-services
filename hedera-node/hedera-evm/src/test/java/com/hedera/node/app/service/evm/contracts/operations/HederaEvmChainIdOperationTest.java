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

package com.hedera.node.app.service.evm.contracts.operations;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmChainIdOperationTest {
    @Mock
    private MessageFrame frame;

    @Mock
    private EVM evm;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private EvmProperties evmProperties;

    private HederaEvmChainIdOperation subject;

    static final long PRETEND_GAS_COST = 123L;

    @BeforeEach
    void setUp() {
        given(gasCalculator.getBaseTierGasCost()).willReturn(PRETEND_GAS_COST);
        subject = new HederaEvmChainIdOperation(gasCalculator, evmProperties);
    }

    @Test
    void haltsOnInsufficientGasR() {
        given(frame.getRemainingGas()).willReturn(PRETEND_GAS_COST - 1);

        final var actual = subject.execute(frame, evm);

        assertEquals(PRETEND_GAS_COST, actual.getGasCost());
        assertEquals(INSUFFICIENT_GAS, actual.getHaltReason());
    }

    @Test
    void succeedsOnSufficientGas() {
        given(frame.getRemainingGas()).willReturn(PRETEND_GAS_COST);

        final var result = subject.execute(frame, evm);

        assertEquals(PRETEND_GAS_COST, result.getGasCost());
        assertNull(result.getHaltReason());
    }

    @Test
    void addsChainIdToStack() {
        Bytes32 chainIdBytes = Bytes32.fromHexStringLenient("0x12345678");

        given(frame.getRemainingGas()).willReturn(PRETEND_GAS_COST);
        given(evmProperties.chainIdBytes32()).willReturn(chainIdBytes);

        subject.execute(frame, evm);

        verify(frame).pushStackItem(chainIdBytes);
    }
}
