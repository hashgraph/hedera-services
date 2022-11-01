package com.hedera.services.evm.contracts.operation;

import com.hedera.services.evm.contracts.operations.HederaExceptionalHaltReason;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class HederaEvmOperationsUtilTest {
    @Mock private MessageFrame messageFrame;
    @Mock private LongSupplier gasSupplier;
    @Mock private Supplier<Operation.OperationResult> executionSupplier;

    private final long expectedHaltGas = 10L;
    private final long expectedSuccessfulGas = 100L;

    @Test
    void throwsUnderflowExceptionWhenGettingAddress() {
        // given:
        given(messageFrame.getStackItem(0)).willThrow(new FixedStack.UnderflowException());
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);

        // when:
        final var result =
                com.hedera.services.evm.contracts.operations.HederaEvmOperationsUtil
                        .addressCheckExecution(
                                messageFrame,
                                () -> messageFrame.getStackItem(0),
                                gasSupplier,
                                executionSupplier,
                                (a, b) -> true);

        // then:
        assertEquals(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS, result.getHaltReason().get());
        assertEquals(expectedHaltGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(messageFrame, never()).getWorldUpdater();
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void haltsWithInvalidSolidityAddressWhenAccountCheckExecution() {
        // given:
        given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);

        // when:
        final var result =
                com.hedera.services.evm.contracts.operations.HederaEvmOperationsUtil
                        .addressCheckExecution(
                                messageFrame,
                                () -> messageFrame.getStackItem(0),
                                gasSupplier,
                                executionSupplier,
                                (a, b) -> false);

        // then:
        assertEquals(
                HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason().get());
        assertEquals(expectedHaltGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void successfulWhenAddressCheckExecution() {
        // given:
        given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
        given(executionSupplier.get())
                .willReturn(
                        new Operation.OperationResult(
                                OptionalLong.of(expectedSuccessfulGas), Optional.empty()));

        // when:
        final var result =
                com.hedera.services.evm.contracts.operations.HederaEvmOperationsUtil
                        .addressCheckExecution(
                                messageFrame,
                                () -> messageFrame.getStackItem(0),
                                gasSupplier,
                                executionSupplier,
                                (a, b) -> true);

        // when:
        assertTrue(result.getHaltReason().isEmpty());
        assertEquals(expectedSuccessfulGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(gasSupplier, never()).getAsLong();
        verify(executionSupplier).get();
    }
}
