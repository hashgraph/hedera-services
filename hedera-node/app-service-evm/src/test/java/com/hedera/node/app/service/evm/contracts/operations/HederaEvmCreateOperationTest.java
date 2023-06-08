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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmCreateOperationTest {
    private static final long baseGas = 100L;

    private final Address recipientAddr = Address.fromHexString("0x0102030405060708090a0b0c0d0e0f1011121314");

    @Mock
    private MessageFrame frame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private HederaEvmWorldUpdater hederaWorldUpdater;

    private HederaEvmCreateOperation subject;

    @Mock
    private CreateOperationExternalizer externalizer;

    @BeforeEach
    void setup() {
        subject = new HederaEvmCreateOperation(gasCalculator, externalizer);
    }

    @Test
    void isAlwaysEnabled() {
        Assertions.assertTrue(subject.isEnabled());
    }

    @Test
    void computesExpectedCost() {
        given(gasCalculator.createOperationGasCost(frame)).willReturn(baseGas);

        var actualGas = subject.cost(frame);

        assertEquals(baseGas, actualGas);
    }

    @Test
    void computesExpectedTargetAddress() {
        given(frame.getWorldUpdater()).willReturn(hederaWorldUpdater);
        given(frame.getRecipientAddress()).willReturn(recipientAddr);
        given(hederaWorldUpdater.newContractAddress(recipientAddr)).willReturn(Address.ZERO);
        var targetAddr = subject.targetContractAddress(frame);
        assertEquals(Address.ZERO, targetAddr);
    }
}
