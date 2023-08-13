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

package com.hedera.node.app.service.contract.impl.test.exec.operations;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertSameResult;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.operations.CustomCreateOperation;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomCreateOperationTest extends CreateOperationTestBase {
    private static final Address EXPECTED_CREATE1_ADDRESS = Address.contractAddress(RECIEVER_ADDRESS, NONCE - 1);

    private CustomCreateOperation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomCreateOperation(gasCalculator);
    }

    @Test
    void returnsUnderflowWhenStackSizeTooSmall() {
        given(frame.stackSize()).willReturn(2);

        final var expected = new Operation.OperationResult(0, INSUFFICIENT_STACK_ITEMS);

        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void haltsOnStaticFrame() {
        given(frame.stackSize()).willReturn(3);
        given(frame.isStatic()).willReturn(true);
        given(gasCalculator.createOperationGasCost(frame)).willReturn(GAS_COST);

        final var expected = new Operation.OperationResult(GAS_COST, ILLEGAL_STATE_CHANGE);

        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void haltsOnInsufficientGas() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getRemainingGas()).willReturn(GAS_COST - 1);
        given(gasCalculator.createOperationGasCost(frame)).willReturn(GAS_COST);

        final var expected = new Operation.OperationResult(GAS_COST, INSUFFICIENT_GAS);

        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void failsWithInsufficientRecipientBalanceForValue() {
        given(frame.stackSize()).willReturn(3);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));
        given(frame.getRemainingGas()).willReturn(GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(VALUE));
        given(frame.getRecipientAddress()).willReturn(RECIEVER_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.getAccount(RECIEVER_ADDRESS)).willReturn(receiver);
        given(receiver.getMutable()).willReturn(mutableReceiver);
        given(mutableReceiver.getBalance()).willReturn(Wei.ONE);
        given(gasCalculator.createOperationGasCost(frame)).willReturn(GAS_COST);

        final var expected = new Operation.OperationResult(GAS_COST, null);

        assertSameResult(expected, subject.execute(frame, evm));
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void failsWithExcessStackDepth() {
        givenSpawnPrereqs();
        given(frame.getMessageStackDepth()).willReturn(1024);
        given(gasCalculator.createOperationGasCost(frame)).willReturn(GAS_COST);

        final var expected = new Operation.OperationResult(GAS_COST, null);
        assertSameResult(expected, subject.execute(frame, evm));
        verify(frame).pushStackItem(UInt256.ZERO);
    }

    @Test
    void hasExpectedChildCompletionOnSuccess() {
        final var frameCaptor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs();
        givenBuilderPrereqs();
        given(receiver.getNonce()).willReturn(NONCE);
        given(gasCalculator.createOperationGasCost(frame)).willReturn(GAS_COST);
        given(frame.readMemory(anyLong(), anyLong())).willReturn(INITCODE);
        final var expected = new Operation.OperationResult(GAS_COST, null);
        assertSameResult(expected, subject.execute(frame, evm));

        verify(stack).addFirst(frameCaptor.capture());
        final var childFrame = frameCaptor.getValue();
        childFrame.setState(MessageFrame.State.COMPLETED_SUCCESS);
        childFrame.notifyCompletion();
        verify(worldUpdater).setupInternalAliasedCreate(RECIEVER_ADDRESS, EXPECTED_CREATE1_ADDRESS);
        verify(frame).pushStackItem(Words.fromAddress(EXPECTED_CREATE1_ADDRESS));
    }

    @Test
    void hasExpectedChildCompletionOnFailure() {
        final var captor = ArgumentCaptor.forClass(MessageFrame.class);
        givenSpawnPrereqs();
        givenBuilderPrereqs();
        given(frame.readMemory(anyLong(), anyLong())).willReturn(INITCODE);
        given(gasCalculator.createOperationGasCost(frame)).willReturn(GAS_COST);

        final var expected = new Operation.OperationResult(GAS_COST, null);
        assertSameResult(expected, subject.execute(frame, evm));

        verify(stack).addFirst(captor.capture());
        final var childFrame = captor.getValue();
        // when:
        childFrame.setState(MessageFrame.State.COMPLETED_FAILED);
        childFrame.notifyCompletion();
        verify(frame).pushStackItem(UInt256.ZERO);
    }
}
