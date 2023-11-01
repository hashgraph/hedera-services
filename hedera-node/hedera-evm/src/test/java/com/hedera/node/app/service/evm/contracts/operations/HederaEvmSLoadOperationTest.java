/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.TOO_MANY_STACK_ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmSLoadOperationTest {
    final Address recipientAccount = Address.fromHexString("0x0001");

    HederaEvmSLoadOperation subject;

    @Mock
    GasCalculator gasCalculator;

    @Mock
    MessageFrame messageFrame;

    @Mock
    EVM evm;

    @Mock
    AbstractLedgerEvmWorldUpdater<?, ?> worldUpdater;

    @Mock
    MutableAccount evmAccount;

    final Bytes keyBytesMock = Bytes.of(1, 2, 3, 4);
    final Bytes valueBytesMock = Bytes.of(4, 3, 2, 1);

    @BeforeEach
    void setUp() {
        givenValidContext();
        subject = new HederaEvmSLoadOperation(gasCalculator);
    }

    @Test
    void executesProperlyWithColdSuccess() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(false);

        final var coldResult = subject.execute(messageFrame, evm);

        final var expectedColdResult = new Operation.OperationResult(20L, null);

        assertEquals(expectedColdResult.getGasCost(), coldResult.getGasCost());
        assertEquals(expectedColdResult.getHaltReason(), coldResult.getHaltReason());
        assertEquals(expectedColdResult.getPcIncrement(), coldResult.getPcIncrement());
    }

    @Test
    void executesProperlyWithWarmSuccess() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);

        given(evmAccount.getStorageValue(UInt256.fromBytes(UInt256.fromBytes(keyBytesMock))))
                .willReturn(UInt256.fromBytes(valueBytesMock));
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);

        final var warmResult = subject.execute(messageFrame, evm);

        final var expectedWarmResult = new Operation.OperationResult(30L, null);
        assertEquals(expectedWarmResult.getGasCost(), warmResult.getGasCost());
        assertEquals(expectedWarmResult.getHaltReason(), warmResult.getHaltReason());
        assertEquals(expectedWarmResult.getPcIncrement(), warmResult.getPcIncrement());
    }

    @Test
    void executeHaltsForInsufficientGas() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);
        given(messageFrame.getRemainingGas()).willReturn(0L);

        final var expectedHaltResult = new Operation.OperationResult(30L, ExceptionalHaltReason.INSUFFICIENT_GAS);

        final var haltResult = subject.execute(messageFrame, evm);

        assertEquals(expectedHaltResult.getGasCost(), haltResult.getGasCost());
        assertEquals(expectedHaltResult.getHaltReason(), haltResult.getHaltReason());
        assertEquals(expectedHaltResult.getPcIncrement(), haltResult.getPcIncrement());
    }

    @Test
    void executeWithUnderFlowException() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.popStackItem()).willThrow(new FixedStack.UnderflowException());
        final var result = subject.execute(messageFrame, evm);
        assertEquals(INSUFFICIENT_STACK_ITEMS, result.getHaltReason());
    }

    @Test
    void executeWithOverFlowException() {
        givenAdditionalContext(keyBytesMock, valueBytesMock);
        given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
        given(messageFrame.getRemainingGas()).willReturn(300L);

        doThrow(new FixedStack.OverflowException()).when(messageFrame).pushStackItem(any());

        final var result = subject.execute(messageFrame, evm);
        assertEquals(TOO_MANY_STACK_ITEMS, result.getHaltReason());
    }

    private void givenAdditionalContext(Bytes key, Bytes value) {
        final UInt256 keyBytes = UInt256.fromBytes(key);
        final UInt256 valueBytes = UInt256.fromBytes(value);

        given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
        given(worldUpdater.get(recipientAccount)).willReturn(evmAccount);
        given(evmAccount.getAddress()).willReturn(Address.fromHexString("0x123"));
    }

    private void givenValidContext() {
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);

        given(gasCalculator.getSloadOperationGasCost()).willReturn(10L);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(20L);
        given(gasCalculator.getColdSloadCost()).willReturn(10L);
    }
}
