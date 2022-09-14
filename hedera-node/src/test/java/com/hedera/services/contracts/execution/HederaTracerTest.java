/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution;

import static com.hedera.services.contracts.execution.traceability.CallOperationType.OP_CALL;
import static com.hedera.services.contracts.execution.traceability.CallOperationType.OP_CALLCODE;
import static com.hedera.services.contracts.execution.traceability.CallOperationType.OP_CREATE;
import static com.hedera.services.contracts.execution.traceability.CallOperationType.OP_CREATE2;
import static com.hedera.services.contracts.execution.traceability.CallOperationType.OP_DELEGATECALL;
import static com.hedera.services.contracts.execution.traceability.CallOperationType.OP_STATICCALL;
import static com.hedera.services.contracts.execution.traceability.CallOperationType.OP_UNKNOWN;
import static com.hedera.services.contracts.execution.traceability.ContractActionType.CALL;
import static com.hedera.services.contracts.execution.traceability.ContractActionType.CREATE;
import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.hedera.services.contracts.execution.traceability.ContractActionType;
import com.hedera.services.contracts.execution.traceability.HederaTracer;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.frame.MessageFrame.Type;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaTracerTest {

    private static final Code code = Code.createLegacyCode(Bytes.of(4), Hash.EMPTY);
    private static final Wei value = Wei.of(1L);
    private static final long initialGas = 1000L;
    private static final Bytes input = Bytes.of("inputData".getBytes());
    private static final Bytes output = Bytes.wrap("output".getBytes(StandardCharsets.UTF_8));
    private static final Address originator = Address.fromHexString("0x1");
    private static final Address contract = Address.fromHexString("0x2");
    private static final Address accountReceiver = Address.fromHexString("0x3");

    @Mock private MessageFrame messageFrame;

    @Mock private HederaStackedWorldStateUpdater worldUpdater;

    @Mock private ContractAliases contractAliases;

    @Mock private OperationTracer.ExecuteOperation eo;

    private HederaTracer subject;

    @BeforeEach
    void setUp() {
        subject = new HederaTracer(true);
    }

    @Test
    void initializesActionsAsExpectedOnNewFrames() {
        Operation mockOperation = mock(Operation.class);

        // mock out top level frame
        final var topLevelMessageFrame = mock(MessageFrame.class);
        given(topLevelMessageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(topLevelMessageFrame.getCode()).willReturn(code);
        given(topLevelMessageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(topLevelMessageFrame.getOriginatorAddress()).willReturn(originator);
        given(topLevelMessageFrame.getContractAddress()).willReturn(contract);
        given(topLevelMessageFrame.getRemainingGas()).willReturn(initialGas);
        given(topLevelMessageFrame.getInputData()).willReturn(input);
        given(topLevelMessageFrame.getValue()).willReturn(value);
        given(topLevelMessageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(contract)).willReturn(contract);

        // trace top level frame
        subject.init(topLevelMessageFrame);

        assertEquals(1, subject.getActions().size());
        final var topLevelAction = subject.getActions().get(0);
        assertEquals(CALL, topLevelAction.getCallType());
        assertEquals(EntityId.fromAddress(originator), topLevelAction.getCallingAccount());
        assertNull(topLevelAction.getCallingContract());
        assertEquals(initialGas, topLevelAction.getGas());
        assertArrayEquals(input.toArrayUnsafe(), topLevelAction.getInput());
        assertEquals(EntityId.fromAddress(contract), topLevelAction.getRecipientContract());
        assertNull(topLevelAction.getRecipientAccount());
        assertEquals(value.toLong(), topLevelAction.getValue());
        assertEquals(0, topLevelAction.getCallDepth());
        assertEquals(OP_CALL, topLevelAction.getCallOperationType());

        // we execute some operations
        subject.traceExecution(topLevelMessageFrame, eo);

        // after some operations, the top level message frame spawns a child
        final Deque<MessageFrame> dequeMock = new ArrayDeque<>();
        dequeMock.addFirst(topLevelMessageFrame);
        final var firstChildFrame = mock(MessageFrame.class);
        given(firstChildFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(firstChildFrame.getCode()).willReturn(Code.EMPTY);
        given(firstChildFrame.getContractAddress()).willReturn(accountReceiver);
        final long initialGasChild = initialGas - 500L;
        given(firstChildFrame.getRemainingGas()).willReturn(initialGasChild);
        given(firstChildFrame.getInputData()).willReturn(Bytes.EMPTY);
        given(firstChildFrame.getValue()).willReturn(Wei.ZERO);
        given(firstChildFrame.getMessageStackDepth()).willReturn(1);
        given(firstChildFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(contractAliases.resolveForEvm(accountReceiver)).willReturn(accountReceiver);
        dequeMock.addFirst(firstChildFrame);
        given(topLevelMessageFrame.getMessageFrameStack()).willReturn(dequeMock);
        given(topLevelMessageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(topLevelMessageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(topLevelMessageFrame.getCurrentOperation().getOpcode()).willReturn(0xF0);
        // trace child frame
        subject.traceExecution(topLevelMessageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(CREATE, childFrame1.getCallType());
        assertNull(childFrame1.getCallingAccount());
        assertEquals(EntityId.fromAddress(contract), childFrame1.getCallingContract());
        assertEquals(initialGasChild, childFrame1.getGas());
        assertArrayEquals(Bytes.EMPTY.toArrayUnsafe(), childFrame1.getInput());
        assertEquals(EntityId.fromAddress(accountReceiver), childFrame1.getRecipientAccount());
        assertEquals(Wei.ZERO.toLong(), childFrame1.getValue());
        assertEquals(1, childFrame1.getCallDepth());
        assertEquals(OP_CREATE, childFrame1.getCallOperationType());
        // child frame executes operations
        given(firstChildFrame.getState()).willReturn(State.CODE_EXECUTING);
        subject.traceExecution(firstChildFrame, eo);
        // child frame finishes successfully
        given(firstChildFrame.getState()).willReturn(State.CODE_SUCCESS);
        subject.traceExecution(firstChildFrame, eo);
        dequeMock.removeFirst();
        // parent frame continues executing
        given(topLevelMessageFrame.getState()).willReturn(State.CODE_EXECUTING);
        subject.traceExecution(topLevelMessageFrame, eo);
        given(topLevelMessageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(topLevelMessageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(topLevelMessageFrame.getCurrentOperation().getOpcode()).willReturn(0xF2);
        final var childFrame2 = mock(MessageFrame.class);
        given(childFrame2.getCode()).willReturn(Code.EMPTY);
        given(childFrame2.getType()).willReturn(Type.MESSAGE_CALL);
        given(childFrame2.getContractAddress()).willReturn(accountReceiver);
        given(childFrame2.getRemainingGas()).willReturn(500L);
        given(childFrame2.getInputData()).willReturn(Bytes.EMPTY);
        given(childFrame2.getValue()).willReturn(Wei.of(543L));
        given(childFrame2.getMessageStackDepth()).willReturn(1);
        given(childFrame2.getWorldUpdater()).willReturn(worldUpdater);
        dequeMock.addFirst(childFrame2);
        // trace second child
        subject.traceExecution(topLevelMessageFrame, eo);
        verify(eo, times(6)).execute();
        verify(topLevelMessageFrame, times(3)).getContractAddress();
        // assert call depth is correct
        assertEquals(3, subject.getActions().size());
        assertEquals(1, subject.getActions().get(2).getCallDepth());
    }

    @Test
    void finalizesCodeSuccessfulCallMessageFrameAsExpected() {
        // given
        givenTracedExecutingFrame(Type.MESSAGE_CALL);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.CODE_SUCCESS);
        final long remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        given(messageFrame.getOutputData()).willReturn(output);
        subject.traceExecution(messageFrame, eo);
        // then
        final var actions = subject.getActions();
        final var solidityAction = actions.get(0);
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertEquals(output.toArrayUnsafe(), solidityAction.getOutput());
        assertEquals(OP_CALL, solidityAction.getCallOperationType());
    }

    @Test
    void finalizesCodeSuccessfulCreateMessageFrameAsExpected() {
        // given
        givenTracedExecutingFrame(Type.CONTRACT_CREATION);

        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.CODE_SUCCESS);
        final long remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        subject.traceExecution(messageFrame, eo);
        // then
        final var actions = subject.getActions();
        final var solidityAction = actions.get(0);
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertArrayEquals(new byte[0], solidityAction.getOutput());
        assertEquals(OP_CREATE, solidityAction.getCallOperationType());
    }

    @Test
    void finalizesRevertedFrameWithRevertReasonAsExpected() {
        // given
        givenTracedExecutingFrame(Type.MESSAGE_CALL);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.REVERT);
        final var remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        final var revertReason = Bytes.wrap("thatsTheReason".getBytes(StandardCharsets.UTF_8));
        given(messageFrame.getRevertReason()).willReturn(Optional.of(revertReason));
        subject.traceExecution(messageFrame, eo);
        // then
        final var solidityAction = subject.getActions().get(0);
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertEquals(revertReason.toArrayUnsafe(), solidityAction.getRevertReason());
    }

    @Test
    void finalizesRevertedFrameWithoutRevertReasonAsExpected() {
        // given
        givenTracedExecutingFrame(Type.MESSAGE_CALL);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.REVERT);
        final long remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        given(messageFrame.getRevertReason()).willReturn(Optional.empty());
        subject.traceExecution(messageFrame, eo);
        // then
        final var solidityAction = subject.getActions().get(0);
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertArrayEquals(new byte[0], solidityAction.getRevertReason());
    }

    @Test
    void finalizesExceptionallyHaltedFrameWithoutHaltReasonAsExpected() {
        // given
        givenTracedExecutingFrame(Type.MESSAGE_CALL);
        // when
        subject.init(messageFrame);
        given(messageFrame.getState()).willReturn(State.EXCEPTIONAL_HALT);
        subject.traceExecution(messageFrame, eo);
        // then
        final var solidityAction = subject.getActions().get(0);
        assertEquals(initialGas, solidityAction.getGasUsed());
        assertArrayEquals(new byte[0], solidityAction.getError());
        assertNull(solidityAction.getInvalidSolidityAddress());
    }

    @Test
    void finalizesExceptionallyHaltedFrameWithHaltReasonAsExpected() {
        // given
        givenTracedExecutingFrame(Type.MESSAGE_CALL);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.EXCEPTIONAL_HALT);
        final var codeTooLarge = Optional.of(ExceptionalHaltReason.CODE_TOO_LARGE);
        given(messageFrame.getExceptionalHaltReason()).willReturn(codeTooLarge);
        subject.traceExecution(messageFrame, eo);
        // then
        final var solidityAction = subject.getActions().get(0);
        assertEquals(initialGas, solidityAction.getGasUsed());
        assertArrayEquals(
                codeTooLarge.get().name().getBytes(StandardCharsets.UTF_8),
                solidityAction.getError());
        assertNull(solidityAction.getInvalidSolidityAddress());
    }

    @Test
    void finalizesExceptionallyHaltedFrameWithInvalidAddressRecipientAsExpected() {
        // given
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getCode()).willReturn(Code.EMPTY);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(accountReceiver);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(accountReceiver)).willReturn(accountReceiver);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.EXCEPTIONAL_HALT);
        final var invalidSolidityAddress = Optional.of(INVALID_SOLIDITY_ADDRESS);
        given(messageFrame.getExceptionalHaltReason()).willReturn(invalidSolidityAddress);
        given(messageFrame.getStackItem(1)).willReturn(Bytes.of(contract.toArrayUnsafe()));
        final Operation operation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(operation);
        given(operation.getOpcode()).willReturn(0xF1);
        subject.traceExecution(messageFrame, eo);
        // then
        final var topLevelAction = subject.getActions().get(0);
        assertEquals(initialGas, topLevelAction.getGasUsed());
        assertArrayEquals(
                invalidSolidityAddress.get().name().getBytes(StandardCharsets.UTF_8),
                topLevelAction.getError());
        assertEquals(EntityId.fromAddress(accountReceiver), topLevelAction.getRecipientAccount());
        assertNull(topLevelAction.getRecipientContract());
        assertNull(topLevelAction.getInvalidSolidityAddress());
        final var syntheticInvalidAddressAction = subject.getActions().get(1);
        assertEquals(CALL, syntheticInvalidAddressAction.getCallType());
        assertEquals(OP_CALL, syntheticInvalidAddressAction.getCallOperationType());
        assertEquals(0, syntheticInvalidAddressAction.getValue());
        assertArrayEquals(new byte[0], syntheticInvalidAddressAction.getInput());
        assertEquals(
                messageFrame.getMessageStackDepth() + 1,
                syntheticInvalidAddressAction.getCallDepth());
        assertEquals(
                EntityId.fromAddress(accountReceiver),
                syntheticInvalidAddressAction.getCallingContract());
        assertArrayEquals(
                contract.toArrayUnsafe(),
                syntheticInvalidAddressAction.getInvalidSolidityAddress());
        assertArrayEquals(
                invalidSolidityAddress.get().name().getBytes(StandardCharsets.UTF_8),
                topLevelAction.getError());
        assertEquals(messageFrame.getRemainingGas(), syntheticInvalidAddressAction.getGas());
    }

    @Test
    void finalizesPrecompileCallAsExpected() {
        // given
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getCode()).willReturn(Code.EMPTY);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(contract)).willReturn(contract);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.CODE_SUCCESS);
        final long remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        given(messageFrame.getOutputData()).willReturn(output);
        subject.tracePrecompileResult(messageFrame, ContractActionType.PRECOMPILE);
        // then
        final var solidityAction = subject.getActions().get(0);
        assertEquals(ContractActionType.PRECOMPILE, solidityAction.getCallType());
        assertNull(solidityAction.getRecipientAccount());
        assertEquals(EntityId.fromAddress(contract), solidityAction.getRecipientContract());
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertEquals(output.toArrayUnsafe(), solidityAction.getOutput());
    }

    @Test
    void finalizesSystemPrecompileCallAsExpected() {
        // given
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getCode()).willReturn(Code.EMPTY);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(contract)).willReturn(contract);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.COMPLETED_SUCCESS);
        final long remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        given(messageFrame.getOutputData()).willReturn(output);
        subject.tracePrecompileResult(messageFrame, ContractActionType.SYSTEM);
        // then
        final var solidityAction = subject.getActions().get(0);
        assertEquals(ContractActionType.SYSTEM, solidityAction.getCallType());
        assertNull(solidityAction.getRecipientAccount());
        assertEquals(EntityId.fromAddress(contract), solidityAction.getRecipientContract());
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertEquals(output.toArrayUnsafe(), solidityAction.getOutput());
    }

    @Test
    void finalizesSystemPrecompileCallAsExpectedWhenActionsNotEnabled() {
        // given
        subject = new HederaTracer(false);
        // when
        subject.traceExecution(messageFrame, eo);
        subject.tracePrecompileResult(messageFrame, ContractActionType.SYSTEM);
        // then
        assertTrue(subject.getActions().isEmpty());
    }

    @Test
    void traceAccountCreationResult() {
        final var haltReason = Optional.of(INVALID_SOLIDITY_ADDRESS);

        subject.traceAccountCreationResult(messageFrame, haltReason);

        verify(messageFrame).setExceptionalHaltReason(haltReason);
    }

    @Test
    void actionsAreNotTrackedWhenNotEnabled() {
        subject = new HederaTracer(false);

        subject.init(messageFrame);
        subject.traceExecution(messageFrame, eo);

        assertTrue(subject.getActions().isEmpty());
    }

    @Test
    void topLevelCreationFrameHasCreateOperationType() {
        given(messageFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(messageFrame.getCode()).willReturn(code);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(contract)).willReturn(contract);

        subject.init(messageFrame);

        assertEquals(1, subject.getActions().size());
        var action = subject.getActions().get(0);
        assertEquals(OP_CREATE, action.getCallOperationType());
    }

    @Test
    void topLevelCallFrameHasCallOperationType() {
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getCode()).willReturn(code);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(contract)).willReturn(contract);

        subject.init(messageFrame);

        assertEquals(1, subject.getActions().size());
        var action = subject.getActions().get(0);
        assertEquals(OP_CALL, action.getCallOperationType());
    }

    @Test
    void callChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF1);
        // trace child frame
        subject.traceExecution(messageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(OP_CALL, childFrame1.getCallOperationType());
    }

    @Test
    void createChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF0);
        // trace child frame
        subject.traceExecution(messageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(OP_CREATE, childFrame1.getCallOperationType());
    }

    @Test
    void callCodeChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF2);
        // trace child frame
        subject.traceExecution(messageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(OP_CALLCODE, childFrame1.getCallOperationType());
    }

    @Test
    void delegateCallChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF4);
        // trace child frame
        subject.traceExecution(messageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(OP_DELEGATECALL, childFrame1.getCallOperationType());
    }

    @Test
    void create2ChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF5);
        // trace child frame
        subject.traceExecution(messageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(OP_CREATE2, childFrame1.getCallOperationType());
    }

    @Test
    void staticCallChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xFA);
        // trace child frame
        subject.traceExecution(messageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(OP_STATICCALL, childFrame1.getCallOperationType());
    }

    @Test
    void unknownCallChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xAA);
        // trace child frame
        subject.traceExecution(messageFrame, eo);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        assertEquals(OP_UNKNOWN, childFrame1.getCallOperationType());
    }

    private void prepareSpawnOfChildFrame() {
        given(messageFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(messageFrame.getCode()).willReturn(code);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(contract)).willReturn(contract);
        subject.init(messageFrame);
        // after some operations, the top level message frame spawns a child
        final Deque<MessageFrame> dequeMock = new ArrayDeque<>();
        dequeMock.addFirst(messageFrame);
        final var firstChildFrame = mock(MessageFrame.class);
        given(firstChildFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(firstChildFrame.getCode()).willReturn(Code.EMPTY);
        given(firstChildFrame.getContractAddress()).willReturn(accountReceiver);
        given(firstChildFrame.getRemainingGas()).willReturn(initialGas);
        given(firstChildFrame.getInputData()).willReturn(Bytes.EMPTY);
        given(firstChildFrame.getValue()).willReturn(Wei.ZERO);
        given(firstChildFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(contractAliases.resolveForEvm(accountReceiver)).willReturn(accountReceiver);
        dequeMock.addFirst(firstChildFrame);
        given(messageFrame.getMessageFrameStack()).willReturn(dequeMock);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
    }

    private void givenTracedExecutingFrame(final Type frameType) {
        given(messageFrame.getType()).willReturn(frameType);
        given(messageFrame.getCode()).willReturn(code);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);
        given(contractAliases.resolveForEvm(contract)).willReturn(contract);

        subject.traceExecution(messageFrame, eo);
    }
}
