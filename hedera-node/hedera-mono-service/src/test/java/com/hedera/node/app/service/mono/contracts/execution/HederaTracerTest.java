/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.execution;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_CALL;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_CALLCODE;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_CREATE;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_CREATE2;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_DELEGATECALL;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_STATICCALL;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType.OP_UNKNOWN;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType.CALL;
import static com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Sets;
import com.hedera.node.app.service.mono.contracts.execution.traceability.CallOperationType;
import com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaTracer;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaTracer.EmitActionSidecars;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaTracer.ValidateActionSidecars;
import com.hedera.node.app.service.mono.contracts.execution.traceability.SolidityAction;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.SoftAssertions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.frame.MessageFrame.Type;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class HederaTracerTest {

    private static final Code code = CodeFactory.createCode(Bytes.of(4), 0, false);
    private static final Wei value = Wei.of(1L);
    private static final long initialGas = 1000L;
    private static final Bytes input = Bytes.of("inputData".getBytes());
    private static final Bytes output = Bytes.wrap("output".getBytes(StandardCharsets.UTF_8));
    private static final Address originator = Address.fromHexString("0x1");
    private static final Address contract = Address.fromHexString("0x2");
    private static final Address accountReceiver = Address.fromHexString("0x3");
    private static final Address sender = Address.fromHexString("0x4");

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private HederaTracer loggedHederaTracer;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private ContractAliases contractAliases;

    @Mock
    private OperationResult operationResult;

    private HederaTracer subject;

    @BeforeEach
    void setUp() {
        subject = new HederaTracer(EmitActionSidecars.ENABLED, ValidateActionSidecars.ENABLED);
    }

    /**
     * Helper for testing correctness of (what will eventually be) protobuf `oneof` fields
     *
     * Add to param `ss` the names of all fields you're setting that belong to the
     * `oneof` being checked.  Will ensure that only _one_ field is set, and if not,
     * produce an appropriate diagnostic.
     *
     * @param name name of the `oneof`
     * @param ss names of the _present_ fields that _belong_ to the `oneof`
     */
    void oneofChecker(final String name, final Map<String, Boolean> ss) {
        final var presentFields = ss.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        final var presentFieldNames = String.join(", ", presentFields);
        final var allFieldNames = ss.keySet().stream().sorted().collect(Collectors.joining(", "));

        if (presentFields.size() == 0) {
            assertThat(presentFields)
                    .as("%s not supplied, needs one of %s".formatted(name, allFieldNames))
                    .hasSize(1);
        } else if (presentFields.size() != 1) {
            assertThat(presentFields)
                    .as("%s has more than one field supplied, must have only one of %s"
                            .formatted(name, presentFieldNames))
                    .hasSize(1);
        }
    }

    @Test
    void oneofCheckerInternalTest() {
        assertThatNoException().isThrownBy(() -> {
            oneofChecker("check-ok", Map.of("foo", false, "bar", true, "bear", false));
        });
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> {
                    oneofChecker("check-0-fails", Map.of("foo", false, "bar", false, "bear", false));
                })
                .withMessageContaining("check-0-fails not supplied, needs one of bar, bear, foo");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> {
                    oneofChecker("check-2-fails", Map.of("foo", false, "bar", true, "bear", true));
                })
                .withMessageContaining(
                        "check-2-fails has more than one field supplied, must have only one of bar, bear");
    }

    void validateAllActionFieldsAreSet(final SolidityAction solidityAction) {
        final var oneof = new TreeMap<String, Boolean>();

        assertThat(solidityAction.getCallType())
                .as("call_type must have valid enum value")
                .isNotNull()
                .isNotEqualTo(ContractActionType.NO_ACTION);

        oneof.clear();
        oneof.put("calling_account", null != solidityAction.getCallingAccount());
        oneof.put("calling_contract", null != solidityAction.getCallingContract());
        oneofChecker("caller", oneof);

        // can't check `gas` - it's a final integer

        assertThat(solidityAction.getInput())
                .as("input must be non-null (though it can be empty)")
                .isNotNull();

        oneof.clear();
        oneof.put("recipient_account", null != solidityAction.getRecipientAccount());
        oneof.put("recipient_contract", null != solidityAction.getRecipientContract());
        oneof.put("targeted_address", null != solidityAction.getInvalidSolidityAddress());
        oneofChecker("recipient", oneof);

        // can't check `value` - it's a final integer
        // can't check `gas_used` - it's a naked integer that might legitimately be 0

        oneof.clear();
        oneof.put("output", null != solidityAction.getOutput());
        oneof.put("revert_reason", null != solidityAction.getRevertReason());
        oneof.put("error", null != solidityAction.getError());
        oneofChecker("result_data", oneof);

        // can't check `call_depth` - it's a final integer

        assertThat(solidityAction.getCallOperationType())
                .as("call_operation_type must have valid enum value")
                .isNotNull()
                .isNotEqualTo(CallOperationType.OP_UNKNOWN);
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
        given(worldUpdater.getAccount(contract)).willReturn(mock(MutableAccount.class));

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
        subject.tracePostExecution(topLevelMessageFrame, operationResult);

        // after some operations, the top level message frame spawns a child
        final Deque<MessageFrame> dequeMock = new ArrayDeque<>();
        dequeMock.addFirst(topLevelMessageFrame);
        final var firstChildFrame = mock(MessageFrame.class);
        given(firstChildFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(firstChildFrame.getCode()).willReturn(CodeV0.EMPTY_CODE);
        given(firstChildFrame.getContractAddress()).willReturn(accountReceiver);
        final long initialGasChild = initialGas - 500L;
        given(firstChildFrame.getRemainingGas()).willReturn(initialGasChild);
        given(firstChildFrame.getInputData()).willReturn(Bytes.EMPTY);
        given(firstChildFrame.getValue()).willReturn(Wei.ZERO);
        given(firstChildFrame.getDepth()).willReturn(1);
        given(firstChildFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(contractAliases.resolveForEvm(accountReceiver)).willReturn(accountReceiver);
        dequeMock.addFirst(firstChildFrame);
        given(topLevelMessageFrame.getMessageFrameStack()).willReturn(dequeMock);
        given(topLevelMessageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(topLevelMessageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(topLevelMessageFrame.getCurrentOperation().getOpcode()).willReturn(0xF0);
        given(worldUpdater.getAccount(accountReceiver)).willReturn(mock(MutableAccount.class));
        // trace child frame
        subject.tracePostExecution(topLevelMessageFrame, operationResult);
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
        subject.tracePostExecution(firstChildFrame, operationResult);
        // child frame finishes successfully
        given(firstChildFrame.getState()).willReturn(State.CODE_SUCCESS);
        subject.tracePostExecution(firstChildFrame, operationResult);
        dequeMock.removeFirst();
        // parent frame continues executing
        given(topLevelMessageFrame.getState()).willReturn(State.CODE_EXECUTING);
        subject.tracePostExecution(topLevelMessageFrame, operationResult);
        given(topLevelMessageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(topLevelMessageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(topLevelMessageFrame.getCurrentOperation().getOpcode()).willReturn(0xF2);
        final var childFrame2 = mock(MessageFrame.class);
        given(childFrame2.getCode()).willReturn(CodeV0.EMPTY_CODE);
        given(childFrame2.getType()).willReturn(Type.MESSAGE_CALL);
        given(childFrame2.getContractAddress()).willReturn(accountReceiver);
        given(childFrame2.getRemainingGas()).willReturn(500L);
        given(childFrame2.getInputData()).willReturn(Bytes.EMPTY);
        given(childFrame2.getValue()).willReturn(Wei.of(543L));
        given(childFrame2.getDepth()).willReturn(1);
        given(childFrame2.getWorldUpdater()).willReturn(worldUpdater);
        dequeMock.addFirst(childFrame2);
        // trace second child
        subject.tracePostExecution(topLevelMessageFrame, operationResult);
        // FIXME
        //        verify(operationResult, times(6)).execute();
        //        verify(topLevelMessageFrame, times(3)).getContractAddress();
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
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var actions = subject.getActions();
        final var solidityAction = actions.get(0);
        validateAllActionFieldsAreSet(solidityAction);
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
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var actions = subject.getActions();
        final var solidityAction = actions.get(0);
        validateAllActionFieldsAreSet(solidityAction);
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
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var solidityAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(solidityAction);
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
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var solidityAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(solidityAction);
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertArrayEquals(new byte[0], solidityAction.getRevertReason());
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"EXCEPTIONAL_HALT", "COMPLETED_FAILED"})
    void finalizesFailedFrameWithoutHaltReasonAsExpected(State state) {
        // given
        givenTracedExecutingFrame(Type.MESSAGE_CALL);
        // when
        subject.init(messageFrame);
        given(messageFrame.getState()).willReturn(state);
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var solidityAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(solidityAction);
        assertEquals(initialGas, solidityAction.getGasUsed());
        assertArrayEquals(new byte[0], solidityAction.getError());
        assertNull(solidityAction.getInvalidSolidityAddress());
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"EXCEPTIONAL_HALT", "COMPLETED_FAILED"})
    void finalizesFailedFrameWithHaltReasonAsExpected(State state) {
        // given
        givenTracedExecutingFrame(Type.MESSAGE_CALL);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(state);
        final var codeTooLarge = Optional.of(ExceptionalHaltReason.CODE_TOO_LARGE);
        given(messageFrame.getExceptionalHaltReason()).willReturn(codeTooLarge);
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var solidityAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(solidityAction);
        assertEquals(initialGas, solidityAction.getGasUsed());
        assertArrayEquals(codeTooLarge.get().name().getBytes(StandardCharsets.UTF_8), solidityAction.getError());
        assertNull(solidityAction.getInvalidSolidityAddress());
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"EXCEPTIONAL_HALT", "COMPLETED_FAILED"})
    void finalizesFailedFrameWithInvalidAddressRecipientAsExpected(State state) {
        // given
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getCode()).willReturn(CodeV0.EMPTY_CODE);
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
        given(worldUpdater.getAccount(accountReceiver)).willReturn(mock(MutableAccount.class));

        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(state);
        final var invalidSolidityAddress = Optional.of(INVALID_SOLIDITY_ADDRESS);
        given(messageFrame.getExceptionalHaltReason()).willReturn(invalidSolidityAddress);
        given(messageFrame.getStackItem(1)).willReturn(Bytes.of(contract.toArrayUnsafe()));
        final Operation operation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(operation);
        given(operation.getOpcode()).willReturn(0xF1);
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var topLevelAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(topLevelAction);
        assertEquals(initialGas, topLevelAction.getGasUsed());
        assertArrayEquals(
                invalidSolidityAddress.get().name().getBytes(StandardCharsets.UTF_8), topLevelAction.getError());
        assertEquals(EntityId.fromAddress(accountReceiver), topLevelAction.getRecipientAccount());
        assertNull(topLevelAction.getRecipientContract());
        assertNull(topLevelAction.getInvalidSolidityAddress());
        final var syntheticInvalidAddressAction = subject.getActions().get(1);
        validateAllActionFieldsAreSet(syntheticInvalidAddressAction);
        assertEquals(CALL, syntheticInvalidAddressAction.getCallType());
        assertEquals(OP_CALL, syntheticInvalidAddressAction.getCallOperationType());
        assertEquals(0, syntheticInvalidAddressAction.getValue());
        assertArrayEquals(new byte[0], syntheticInvalidAddressAction.getInput());
        assertEquals(messageFrame.getDepth() + 1, syntheticInvalidAddressAction.getCallDepth());
        assertEquals(EntityId.fromAddress(accountReceiver), syntheticInvalidAddressAction.getCallingContract());
        assertArrayEquals(contract.toArrayUnsafe(), syntheticInvalidAddressAction.getInvalidSolidityAddress());
        assertArrayEquals(
                invalidSolidityAddress.get().name().getBytes(StandardCharsets.UTF_8), topLevelAction.getError());
        assertEquals(messageFrame.getRemainingGas(), syntheticInvalidAddressAction.getGas());
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"EXCEPTIONAL_HALT", "COMPLETED_FAILED"})
    void finalizesFailedCreateFrameWithInvalidAddressReasonAsExpected(State state) {
        // given
        given(messageFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(messageFrame.getCode()).willReturn(CodeV0.EMPTY_CODE);
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
        given(messageFrame.getState()).willReturn(state);
        final var invalidSolidityAddress = Optional.of(INVALID_SOLIDITY_ADDRESS);
        given(messageFrame.getExceptionalHaltReason()).willReturn(invalidSolidityAddress);
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        assertEquals(1, subject.getActions().size());
        final var topLevelAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(topLevelAction);
        assertEquals(initialGas, topLevelAction.getGasUsed());
        assertArrayEquals(
                invalidSolidityAddress.get().name().getBytes(StandardCharsets.UTF_8), topLevelAction.getError());
        assertEquals(EntityId.fromAddress(accountReceiver), topLevelAction.getRecipientAccount());
        assertNull(topLevelAction.getRecipientContract());
        assertNull(topLevelAction.getInvalidSolidityAddress());
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"EXCEPTIONAL_HALT", "COMPLETED_FAILED"})
    void clearsRecipientOfFailedCreateFrame(State state) {
        // given
        givenTracedExecutingFrame(Type.CONTRACT_CREATION);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(state);
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var solidityAction = subject.getActions().get(0);
        // this is a child frame where current frame is CODE_EXECUTING, so we aren't finalized at this time
        assertNull(solidityAction.getRecipientAccount());
        assertNull(solidityAction.getRecipientContract());
    }

    @Test
    void clearsRecipientOfRevertedCreateFrame() {
        // given
        givenTracedExecutingFrame(Type.CONTRACT_CREATION);
        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.REVERT);
        subject.tracePostExecution(messageFrame, operationResult);
        // then
        final var solidityAction = subject.getActions().get(0);
        // this is a frame where current frame is CODE_EXECUTING, so we aren't finalized at this time
        assertNull(solidityAction.getRecipientAccount());
        assertNull(solidityAction.getRecipientContract());
    }

    @Test
    void finalizesPrecompileCallAsExpected() {
        // given
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);

        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.CODE_SUCCESS);
        final long remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        given(messageFrame.getOutputData()).willReturn(output);
        subject.tracePrecompileResult(messageFrame, ContractActionType.PRECOMPILE);
        // then
        final var solidityAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(solidityAction);
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
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);

        subject.init(messageFrame);
        // when
        given(messageFrame.getState()).willReturn(State.COMPLETED_SUCCESS);
        final long remainingGasAfterExecution = 343L;
        given(messageFrame.getRemainingGas()).willReturn(remainingGasAfterExecution);
        given(messageFrame.getOutputData()).willReturn(output);
        subject.tracePrecompileResult(messageFrame, ContractActionType.SYSTEM);
        // then
        final var solidityAction = subject.getActions().get(0);
        validateAllActionFieldsAreSet(solidityAction);
        assertEquals(ContractActionType.SYSTEM, solidityAction.getCallType());
        assertNull(solidityAction.getRecipientAccount());
        assertEquals(EntityId.fromAddress(contract), solidityAction.getRecipientContract());
        assertEquals(initialGas - remainingGasAfterExecution, solidityAction.getGasUsed());
        assertEquals(output.toArrayUnsafe(), solidityAction.getOutput());
    }

    @Test
    void finalizesSystemPrecompileCallAsExpectedWhenActionsNotEnabled() {
        // given
        subject = new HederaTracer(EmitActionSidecars.DISABLED, ValidateActionSidecars.DISABLED);
        // when
        subject.tracePostExecution(messageFrame, operationResult);
        subject.tracePrecompileResult(messageFrame, ContractActionType.SYSTEM);
        // then
        assertTrue(subject.getActions().isEmpty());
    }

    @Test
    void successfulLazyCreationActionsIsFinalizedAsExpected() {
        // given
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
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

        subject.tracePostExecution(messageFrame, operationResult);

        assertEquals(1, subject.getActions().size());
        final var action = subject.getActions().get(0);
        validateAllActionFieldsAreSet(action);
        assertEquals(contract, action.getRecipientAccount().toEvmAddress());
        assertNull(action.getInvalidSolidityAddress());
        assertNull(action.getRecipientContract());
    }

    @ParameterizedTest
    @EnumSource(
            value = State.class,
            names = {"EXCEPTIONAL_HALT", "COMPLETED_FAILED"})
    void failedLazyCreationActionsIsFinalizedAsExpected(State state) {
        // given
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getRemainingGas()).willReturn(initialGas);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getValue()).willReturn(value);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.aliases()).willReturn(contractAliases);
        given(contractAliases.resolveForEvm(originator)).willReturn(originator);

        subject.init(messageFrame);

        // when
        given(messageFrame.getState()).willReturn(state);
        given(messageFrame.getExceptionalHaltReason()).willReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

        subject.tracePostExecution(messageFrame, operationResult);

        assertEquals(1, subject.getActions().size());
        final var action = subject.getActions().get(0);
        validateAllActionFieldsAreSet(action);
        assertArrayEquals(contract.toArrayUnsafe(), action.getInvalidSolidityAddress());
        assertNull(action.getRecipientAccount());
        assertNull(action.getRecipientContract());
    }

    @Test
    void actionsAreNotTrackedWhenNotEnabled() {
        subject = new HederaTracer(EmitActionSidecars.DISABLED, ValidateActionSidecars.DISABLED);

        subject.init(messageFrame);
        subject.tracePostExecution(messageFrame, operationResult);

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
        given(worldUpdater.getAccount(contract)).willReturn(mock(MutableAccount.class));

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
        subject.tracePostExecution(messageFrame, operationResult);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        // this is a child frame where current frame is CODE_SUSPENDED, so we aren't finalized at this time
        assertEquals(OP_CALL, childFrame1.getCallOperationType());
    }

    @Test
    void createChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF0);
        // trace child frame
        subject.tracePostExecution(messageFrame, operationResult);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        // this is a child frame where current frame is CODE_SUSPENDED, so we aren't finalized at this time
        assertEquals(OP_CREATE, childFrame1.getCallOperationType());
    }

    @Test
    void callCodeChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF2);
        // trace child frame
        subject.tracePostExecution(messageFrame, operationResult);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        // this is a child frame where current frame is CODE_SUSPENDED, so we aren't finalized at this time
        assertEquals(OP_CALLCODE, childFrame1.getCallOperationType());
    }

    @Test
    void delegateCallChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF4);
        // trace child frame
        subject.tracePostExecution(messageFrame, operationResult);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        // this is a child frame where current frame is CODE_SUSPENDED, so we aren't finalized at this time
        assertEquals(OP_DELEGATECALL, childFrame1.getCallOperationType());
    }

    @Test
    void create2ChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xF5);
        // trace child frame
        subject.tracePostExecution(messageFrame, operationResult);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        // this is a child frame where current frame is CODE_SUSPENDED, so we aren't finalized at this time
        assertEquals(OP_CREATE2, childFrame1.getCallOperationType());
    }

    @Test
    void staticCallChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xFA);
        // trace child frame
        subject.tracePostExecution(messageFrame, operationResult);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        // this is a child frame where current frame is CODE_SUSPENDED, so we aren't finalized at this time
        assertEquals(OP_STATICCALL, childFrame1.getCallOperationType());
    }

    @Test
    void unknownCallChildFrameHasCorrectOperationType() {
        prepareSpawnOfChildFrame();
        final var mockOperation = mock(Operation.class);
        given(messageFrame.getCurrentOperation()).willReturn(mockOperation);
        given(mockOperation.getOpcode()).willReturn(0xAA);
        // trace child frame
        subject.tracePostExecution(messageFrame, operationResult);
        // assert child frame action is initialized as expected
        assertEquals(2, subject.getActions().size());
        final var childFrame1 = subject.getActions().get(1);
        // this is a child frame where current frame is CODE_SUSPENDED, so we aren't finalized at this time
        assertEquals(OP_UNKNOWN, childFrame1.getCallOperationType());
    }

    private void prepareSpawnOfChildFrame() {
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
        given(worldUpdater.getAccount(contract)).willReturn(mock(MutableAccount.class));
        subject.init(messageFrame);
        // after some operations, the top level message frame spawns a child
        final Deque<MessageFrame> dequeMock = new ArrayDeque<>();
        dequeMock.addFirst(messageFrame);
        final var firstChildFrame = mock(MessageFrame.class);
        given(firstChildFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(firstChildFrame.getCode()).willReturn(CodeV0.EMPTY_CODE);
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
        if (frameType == Type.MESSAGE_CALL) {
            given(worldUpdater.getAccount(contract)).willReturn(mock(MutableAccount.class));
        }

        subject.tracePostExecution(messageFrame, operationResult);
    }

    @Test
    void finalizeOperationTestWithValidationDisabled() {

        final var badSolidityAction1 = new SolidityAction(CALL, 7, new byte[7], 7, 7);
        final var goodSolidityAction1 = new SolidityAction(CALL, 7, new byte[7], 7, 7);
        {
            goodSolidityAction1.setCallingAccount(new EntityId());
            goodSolidityAction1.setRecipientAccount(new EntityId());
            goodSolidityAction1.setGasUsed(7);
            goodSolidityAction1.setOutput(new byte[7]);
            goodSolidityAction1.setCallOperationType(OP_CALL);
        }
        final var allActions = new ArrayList<>(List.of(badSolidityAction1, goodSolidityAction1));
        final var invalidActions = new ArrayList<>(List.of(badSolidityAction1));

        final var sut = new HederaTracer(EmitActionSidecars.ENABLED, ValidateActionSidecars.DISABLED) {
            {
                logLevel = Level.ERROR;
            }

            public void setAllActions(final Collection<SolidityAction> sas) {
                this.allActions = new ArrayList<>(sas);
            }

            public void setInvalidActions(final Collection<SolidityAction> sas) {
                this.invalidActions = new ArrayList<>(sas);
            }

            public Collection<SolidityAction> getAllActions() {
                return List.copyOf(allActions);
            }
        };

        sut.setAllActions(allActions);
        sut.setInvalidActions(invalidActions);
        sut.finalizeOperation(messageFrame);

        final var actualActions = sut.getAllActions();
        assertThat(actualActions).hasSize(2);
        assertThat(actualActions.stream().filter(SolidityAction::isValid).count())
                .isEqualTo(1);
        assertThat(actualActions).hasSameElementsAs(allActions);

        final var actualLogs = Stream.of(
                        logCaptor.debugLogs().stream(),
                        logCaptor.infoLogs().stream(),
                        logCaptor.warnLogs().stream(),
                        logCaptor.errorLogs().stream())
                .flatMap(s -> s)
                .toList();
        assertThat(actualLogs).isEmpty();
        logCaptor.clear();
    }

    @Test
    void finalizeOperationTestWithValidationEnabled() {

        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getOriginatorAddress()).willReturn(originator);
        given(messageFrame.getRecipientAddress()).willReturn(accountReceiver);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getState()).willReturn(State.CODE_EXECUTING);
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);

        Function<Collection<SolidityAction>, Collection<SolidityAction>> getOnlyValid =
                cas -> cas.stream().filter(SolidityAction::isValid).toList();
        Function<Collection<SolidityAction>, Collection<SolidityAction>> getOnlyInvalid =
                cas -> cas.stream().filter(sa -> !sa.isValid()).toList();
        Function<Collection<SolidityAction>, Integer> countValid =
                cas -> getOnlyValid.apply(cas).size();

        final var badSolidityAction1 = new SolidityAction(CALL, 7, new byte[7], 7, 7);
        assertThat(badSolidityAction1.isValid()).isFalse();
        final var badSolidityAction2 = copy(badSolidityAction1);
        final var badSolidityAction3 = copy(badSolidityAction1);
        final var goodSolidityAction1 = new SolidityAction(CALL, 7, new byte[7], 7, 7);
        {
            goodSolidityAction1.setCallingAccount(new EntityId());
            goodSolidityAction1.setRecipientAccount(new EntityId());
            goodSolidityAction1.setGasUsed(7);
            goodSolidityAction1.setOutput(new byte[7]);
            goodSolidityAction1.setCallOperationType(OP_CALL);
        }
        assertThat(goodSolidityAction1.isValid()).isTrue();
        final var goodSolidityAction2 = copy(goodSolidityAction1);
        final var goodSolidityAction3 = copy(goodSolidityAction1);

        final var actions = Set.of(
                badSolidityAction1,
                badSolidityAction2,
                badSolidityAction3,
                goodSolidityAction1,
                goodSolidityAction2,
                goodSolidityAction3);
        assertThat(countValid.apply(actions)).isEqualTo(3);
        final var powerActions = Sets.powerSet(actions);
        assertThat(powerActions).hasSize(64);

        final var namedSAs = Map.of(
                badSolidityAction1,
                "bad1",
                badSolidityAction2,
                "bad2",
                badSolidityAction3,
                "bad3",
                goodSolidityAction1,
                "good1",
                goodSolidityAction2,
                "good2",
                goodSolidityAction3,
                "good3");
        assertThat(namedSAs).hasSize(6);

        final var softly = new SoftAssertions();
        for (final var testStackDepth : IntStream.of(0, 1, 3).toArray())
            for (final var test : powerActions) {

                final var sut = new HederaTracer(EmitActionSidecars.ENABLED, ValidateActionSidecars.ENABLED) {
                    {
                        logLevel = Level.ERROR;
                    }

                    public void setAllActions(final Collection<SolidityAction> sas) {
                        this.allActions = new ArrayList<>(sas);
                    }

                    public void setInvalidActions(final Collection<SolidityAction> sas) {
                        this.invalidActions = new ArrayList<>(sas);
                    }

                    public Collection<SolidityAction> getAllActions() {
                        return List.copyOf(allActions);
                    }

                    public void pushSA() {
                        currentActionsStack.add(new SolidityAction(CALL, 7, new byte[7], 7, 7));
                    }
                };

                Function<Collection<SolidityAction>, String> toString =
                        sas -> sas.stream().map(namedSAs::get).sorted().collect(Collectors.joining(","));

                final var expectedNActions = test.size();

                final var expectedValidActions = getOnlyValid.apply(test);
                final var expectedNValidActions = expectedValidActions.size();

                final var expectedInvalidActions = getOnlyInvalid.apply(test);
                final var expectedNInvalidActions = expectedInvalidActions.size();

                final var expectedTestCaseIsValid = expectedNActions == expectedNValidActions;

                sut.setAllActions(test);
                sut.setInvalidActions(expectedInvalidActions);
                for (int k = 0; k < testStackDepth; k++) sut.pushSA();
                sut.finalizeOperation(messageFrame);

                final var actualActions = sut.getAllActions();
                softly.assertThat(actualActions).hasSize(expectedNValidActions);
                softly.assertThat(countValid.apply(actualActions)).isEqualTo(expectedNValidActions);
                softly.assertThat(actualActions).hasSameElementsAs(expectedValidActions);

                final var testName =
                        "test case [%s] with %d invalid".formatted(toString.apply(test), expectedNInvalidActions);

                final var actualLogs = Stream.of(
                                logCaptor.debugLogs().stream(),
                                logCaptor.infoLogs().stream(),
                                logCaptor.warnLogs().stream(),
                                logCaptor.errorLogs().stream())
                        .flatMap(s -> s)
                        .toList();

                if (expectedTestCaseIsValid && testStackDepth == 0) {
                    softly.assertThat(actualLogs).as(testName + " #logs").hasSize(0);
                } else {
                    softly.assertThat(actualLogs).as(testName + " #logs").hasSize(1);
                    final var actualLog = actualLogs.get(0);

                    if (testStackDepth > 0) {
                        softly.assertThat(actualLog)
                                .as(testName)
                                .contains("currentActionsStack not empty")
                                .contains("has %d elements".formatted(testStackDepth));
                    } else {
                        softly.assertThat(actualLog).as(testName).doesNotContain("currentActionsStack not empty");
                    }

                    if (expectedTestCaseIsValid) {
                        softly.assertThat(actualLog).as(testName).doesNotContain("were invalid");
                    } else {
                        softly.assertThat(actualLog)
                                .as(testName)
                                .contains("of %d actions given, %d were invalid"
                                        .formatted(expectedNActions, expectedNInvalidActions));
                    }
                }

                logCaptor.clear();
            }
        softly.assertAll();
    }

    private SolidityAction copy(final SolidityAction sa) {
        final var r =
                new SolidityAction(sa.getCallType(), sa.getGas(), sa.getInput(), sa.getValue(), sa.getCallDepth());

        r.setCallOperationType(sa.getCallOperationType());
        r.setCallingAccount(sa.getCallingAccount());
        r.setCallingContract(sa.getCallingContract());
        r.setError(sa.getError());
        r.setGasUsed(sa.getGasUsed());
        r.setOutput(sa.getOutput());
        r.setRecipientAccount(sa.getRecipientAccount());
        r.setRecipientContract(sa.getRecipientContract());
        r.setRevertReason(sa.getRevertReason());
        r.setTargetedAddress(sa.getInvalidSolidityAddress());

        assertThat(r.isValid()).isEqualTo(sa.isValid());
        return r;
    }
}
