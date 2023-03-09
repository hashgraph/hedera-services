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

import static com.hedera.node.app.service.evm.contracts.operations.AbstractEvmRecordingCreateOperation.haltWith;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractEvmRecordingCreateOperationTest {
    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private EVM evm;

    @Mock
    private MessageFrame frame;

    @Mock
    private EvmAccount recipientAccount;

    @Mock
    private MutableAccount mutableAccount;

    @Mock
    private HederaEvmWorldUpdater updater;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private CreateOperationExternalizer externalizer;

    private static final long value = 123_456L;
    private static final Address recipient = Address.BLAKE2B_F_COMPRESSION;
    private static final Operation.OperationResult EMPTY_HALT_RESULT =
            new Operation.OperationResult(Subject.PRETEND_COST, null);
    private Subject subject;

    @BeforeEach
    void setUp() {
        subject = new Subject(0xF0, "ħCREATE", 3, 1, 1, gasCalculator, evmProperties, externalizer);
    }

    @Test
    void returnsUnderflowWhenStackSizeTooSmall() {
        given(frame.stackSize()).willReturn(2);

        assertSame(Subject.UNDERFLOW_RESPONSE, subject.execute(frame, evm));
    }

    @Test
    void returnsInvalidWhenDisabled() {
        subject.isEnabled = false;

        assertSame(Subject.INVALID_RESPONSE, subject.execute(frame, evm));
    }

    @Test
    void haltsOnStaticFrame() {
        given(frame.stackSize()).willReturn(3);
        given(frame.isStatic()).willReturn(true);

        final var expected = haltWith(Subject.PRETEND_COST, ILLEGAL_STATE_CHANGE);

        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void haltsOnInsufficientGas() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST - 1);

        final var expected = haltWith(Subject.PRETEND_COST, INSUFFICIENT_GAS);

        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void failsWithInsufficientRecipientBalanceForValue() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(value));
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.getAccount(recipient)).willReturn(recipientAccount);
        given(recipientAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getBalance()).willReturn(Wei.ONE);

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void failsWithExcessStackDepth() {
        givenSpawnPrereqs();
        given(frame.getMessageStackDepth()).willReturn(1024);

        assertSameResult(EMPTY_HALT_RESULT, subject.execute(frame, evm));
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void failsWhenMatchingHollowAccountExistsAndLazyCreationDisabled() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(value));
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.getAccount(recipient)).willReturn(recipientAccount);
        given(recipientAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getBalance()).willReturn(Wei.of(value));
        given(frame.getMessageStackDepth()).willReturn(1023);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        given(externalizer.shouldFailBasedOnLazyCreation(eq(frame), any())).willReturn(true);

        subject.execute(frame, evm);

        verify(frame).readMutableMemory(1L, 1L);
        verify(frame).popStackItems(3);
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    private void givenSpawnPrereqs() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        given(frame.getRemainingGas()).willReturn(Subject.PRETEND_GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(value));
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.getAccount(recipient)).willReturn(recipientAccount);
        given(recipientAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getBalance()).willReturn(Wei.of(value));
        given(frame.getMessageStackDepth()).willReturn(1023);
    }

    private void assertSameResult(final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getGasCost(), actual.getGasCost());
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
    }

    static class Subject extends AbstractEvmRecordingCreateOperation {
        static final long PRETEND_GAS_COST = 123L;
        static final Address PRETEND_CONTRACT_ADDRESS = Address.ALTBN128_ADD;
        static final long PRETEND_COST = PRETEND_GAS_COST;

        boolean isEnabled = true;

        protected Subject(
                final int opcode,
                final String name,
                final int stackItemsConsumed,
                final int stackItemsProduced,
                final int opSize,
                final GasCalculator gasCalculator,
                final EvmProperties dynamicProperties,
                final CreateOperationExternalizer externalizer) {
            super(
                    opcode,
                    name,
                    stackItemsConsumed,
                    stackItemsProduced,
                    opSize,
                    gasCalculator,
                    dynamicProperties,
                    externalizer);
        }

        @Override
        protected boolean isEnabled() {
            return isEnabled;
        }

        @Override
        protected long cost(final MessageFrame frame) {
            return PRETEND_GAS_COST;
        }

        @Override
        protected Address targetContractAddress(final MessageFrame frame) {
            return PRETEND_CONTRACT_ADDRESS;
        }
    }
}
