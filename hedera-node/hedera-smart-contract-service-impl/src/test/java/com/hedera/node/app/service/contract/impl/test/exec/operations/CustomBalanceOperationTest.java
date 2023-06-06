package com.hedera.node.app.service.contract.impl.test.exec.operations;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomBalanceOperation;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers.NOT_SYSTEM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers.SYSTEM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers.assertSameResult;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomBalanceOperationTest {
    @Mock
    private GasCalculator gasCalculator;
    @Mock
    private AddressChecks addressChecks;

    @Mock
    private MessageFrame frame;
    @Mock
    private EVM evm;
    @Mock
    private WorldUpdater worldUpdater;

    private CustomBalanceOperation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomBalanceOperation(gasCalculator, addressChecks);
    }

    @Test
    void catchesUnderflowWhenStackIsEmpty() {
        setupWarmGasCost();
        given(frame.getStackItem(0)).willThrow(FixedStack.UnderflowException.class);
        final var expected = new Operation.OperationResult(3L , ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        final var actual = subject.execute(frame, evm);
        assertSameResult(expected, actual);
    }

    @Test
    void systemAccountBalanceHardCodedToZero() {
        setupWarmGasCost();
        given(frame.getStackItem(0)).willReturn(SYSTEM_ADDRESS);
        given(addressChecks.isSystemAccount(SYSTEM_ADDRESS)).willReturn(true);
        final var expected = new Operation.OperationResult(3L, null);
        final var actual = subject.execute(frame, evm);
        assertSameResult(expected, actual);
        verify(frame).popStackItem();
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void rejectsMissingUserAddress() {
        setupWarmGasCost();
        given(frame.getStackItem(0)).willReturn(NOT_SYSTEM_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        final var expected = new Operation.OperationResult(3L, CustomExceptionalHaltReason.MISSING_ADDRESS);
        final var actual = subject.execute(frame, evm);
        assertSameResult(expected, actual);
    }


    @Test
    void delegatesToSuperForPresentUserAddress() {
        setupWarmGasCost();
        given(frame.getStackItem(0)).willReturn(NOT_SYSTEM_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.popStackItem()).willReturn(NOT_SYSTEM_ADDRESS);
        given(frame.warmUpAddress(NOT_SYSTEM_ADDRESS)).willReturn(true);
        given(addressChecks.isPresent(NOT_SYSTEM_ADDRESS, worldUpdater)).willReturn(true);
        final var expected = new Operation.OperationResult(3L, ExceptionalHaltReason.INSUFFICIENT_GAS);
        final var actual = subject.execute(frame, evm);
        assertSameResult(expected, actual);
    }

    private void setupWarmGasCost() {
        given(gasCalculator.getBalanceOperationGasCost()).willReturn(1L);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(2L);
    }

}