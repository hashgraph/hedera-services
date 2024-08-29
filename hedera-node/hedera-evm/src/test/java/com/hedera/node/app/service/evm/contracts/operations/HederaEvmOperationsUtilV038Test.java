/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.hapi.utils.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.OverflowException;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmOperationsUtilV038Test {
    private final String EVM_VERSION_0_38 = "v0.38";

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private LongSupplier gasSupplier;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private Supplier<Operation.OperationResult> executionSupplier;

    private final long expectedHaltGas = 10L;
    private final long expectedSuccessfulGas = 100L;

    @Test
    void throwsUnderflowExceptionWhenGettingAddress() {
        // given:
        given(messageFrame.getStackItem(0)).willThrow(new UnderflowException());
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);

        // when:
        final var result = HederaEvmOperationsUtilV038.addressCheckExecution(
                messageFrame,
                () -> messageFrame.getStackItem(0),
                gasSupplier,
                executionSupplier,
                (a, b) -> true,
                a -> false,
                () -> mock(Operation.OperationResult.class),
                evmProperties);

        // then:
        assertEquals(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS, result.getHaltReason());
        assertEquals(expectedHaltGas, result.getGasCost());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(messageFrame, never()).getWorldUpdater();
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void handlesOverlowExceptionAsExpected() {
        // given:
        doThrow(OverflowException.class).when(messageFrame).pushStackItem(Bytes.EMPTY);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);
        // when:
        final var result = HederaEvmOperationsUtilV038.addressCheckExecution(
                messageFrame,
                () -> Address.ZERO,
                gasSupplier,
                executionSupplier,
                (a, b) -> true,
                a -> true,
                () -> {
                    messageFrame.pushStackItem(Bytes.EMPTY);
                    return mock(Operation.OperationResult.class);
                },
                evmProperties);
        // then:
        assertEquals(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS, result.getHaltReason());
        assertEquals(expectedHaltGas, result.getGasCost());
        // and:
        verify(messageFrame, never()).getWorldUpdater();
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void haltsWithInvalidSolidityAddressWhenAccountCheckExecution() {
        // given:
        given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);
        given(evmProperties.callsToNonExistingEntitiesEnabled(any())).willReturn(false);

        // when:
        final var result = HederaEvmOperationsUtilV038.addressCheckExecution(
                messageFrame,
                () -> messageFrame.getStackItem(0),
                gasSupplier,
                executionSupplier,
                (a, b) -> false,
                a -> false,
                () -> mock(Operation.OperationResult.class),
                evmProperties);

        // then:
        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason());
        assertEquals(expectedHaltGas, result.getGasCost());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void haltsWithInvalidSolidityAddressWhenCallsToNonExistingEntitiesEnabledFalse() {
        // given:
        given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
        given(messageFrame.getContractAddress()).willReturn(Address.ZERO);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);
        given(evmProperties.callsToNonExistingEntitiesEnabled(any())).willReturn(false);

        // when:
        final var result = HederaEvmOperationsUtilV038.addressCheckExecution(
                messageFrame,
                () -> messageFrame.getStackItem(0),
                gasSupplier,
                executionSupplier,
                (a, b) -> false,
                a -> false,
                () -> mock(Operation.OperationResult.class),
                evmProperties);

        // then:
        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason());
        assertEquals(expectedHaltGas, result.getGasCost());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void successfulWhenAddressCheckExecution() {
        // given:
        given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
        given(executionSupplier.get()).willReturn(new Operation.OperationResult(expectedSuccessfulGas, null));
        given(evmProperties.callsToNonExistingEntitiesEnabled(any())).willReturn(false);

        // when:
        final var result = HederaEvmOperationsUtilV038.addressCheckExecution(
                messageFrame,
                () -> messageFrame.getStackItem(0),
                gasSupplier,
                executionSupplier,
                (a, b) -> true,
                a -> false,
                () -> mock(Operation.OperationResult.class),
                evmProperties);

        // when:
        assertNull(result.getHaltReason());
        assertEquals(expectedSuccessfulGas, result.getGasCost());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(gasSupplier, never()).getAsLong();
        verify(executionSupplier).get();
    }
}
