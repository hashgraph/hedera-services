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

package com.hedera.node.app.service.contract.impl.test.exec.utils;

import static com.hedera.hapi.streams.CallOperationType.OP_CALL;
import static com.hedera.hapi.streams.CallOperationType.OP_CREATE;
import static com.hedera.hapi.streams.ContractActionType.*;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.MISSING_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionsHelper;
import com.hedera.node.app.service.contract.impl.exec.utils.Wrapper;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogBuilder;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionStackTest {
    @Mock
    private Account account;

    @Mock
    private Operation operation;

    @Mock
    private ActionsHelper helper;

    @Mock
    private MessageFrame childFrame;

    @Mock
    private MessageFrame parentFrame;

    @Mock
    private ProxyWorldUpdater worldUpdater;

    @Mock
    private Deque<MessageFrame> frameStack;

    @Mock
    private Logger log;

    @Mock
    private LogBuilder logBuilder;

    private List<Wrapper<ContractAction>> invalidActions = new ArrayList<>();
    private List<Wrapper<ContractAction>> allActions = new ArrayList<>();
    private Deque<Wrapper<ContractAction>> actionsStack = new ArrayDeque<>();

    private ActionStack subject;

    @BeforeEach
    void setUp() {
        subject = new ActionStack(helper, invalidActions, allActions, actionsStack);
    }

    @Test
    void loggingAnomaliesIsNoopWithEmptyStackAndNoInvalid() {
        subject.sanitizeFinalActionsAndLogAnomalies(parentFrame, log, Level.ERROR);
        verifyNoInteractions(log);
    }

    @Test
    void logsAndSanitizesInvalidAsExpected() {
        given(log.atLevel(Level.ERROR)).willReturn(logBuilder);
        final var contextCaptor = forClass(String.class);
        final var anomaliesCaptor = forClass(String.class);
        final var invalidWrapper = new Wrapper<>(MISSING_ADDRESS_CALL_ACTION);
        invalidActions.add(invalidWrapper);
        allActions.add(invalidWrapper);
        given(parentFrame.getOriginatorAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(parentFrame.getSenderAddress()).willReturn(EIP_1014_ADDRESS);
        given(parentFrame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(parentFrame.getContractAddress()).willReturn(HTS_PRECOMPILE_ADDRESS);
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getState()).willReturn(MessageFrame.State.COMPLETED_FAILED);
        given(helper.prettyPrint(MISSING_ADDRESS_CALL_ACTION)).willReturn("<pretty-printed action>");

        subject.sanitizeFinalActionsAndLogAnomalies(parentFrame, log, Level.ERROR);

        verify(logBuilder)
                .log(eq("Invalid at end of EVM run: {} ({})"), anomaliesCaptor.capture(), contextCaptor.capture());
        final var expectedAnomalies = "of 1 actions given, 1 were invalid; invalid: <pretty-printed action>";
        assertEquals(expectedAnomalies, anomaliesCaptor.getValue());
        final var expectedContext =
                "originator 0000000000000000000000000000001234576890 sender 89abcdef89abcdef89abcdef89abcdef89abcdef recipient 89abcdef89abcdef89abcdef89abcdef89abcdef contract 0000000000000000000000000000000000000167 type MESSAGE_CALL state COMPLETED_FAILED";
        assertEquals(expectedContext, contextCaptor.getValue());

        assertTrue(invalidActions.isEmpty());
        assertTrue(allActions.isEmpty());
    }

    @Test
    void logsIfStackNotEmpty() {
        given(log.atLevel(Level.ERROR)).willReturn(logBuilder);
        final var contextCaptor = forClass(String.class);
        final var anomaliesCaptor = forClass(String.class);
        final var invalidWrapper = new Wrapper<>(MISSING_ADDRESS_CALL_ACTION);
        actionsStack.push(invalidWrapper);
        given(parentFrame.getOriginatorAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(parentFrame.getSenderAddress()).willReturn(EIP_1014_ADDRESS);
        given(parentFrame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(parentFrame.getContractAddress()).willReturn(HTS_PRECOMPILE_ADDRESS);
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getState()).willReturn(MessageFrame.State.COMPLETED_FAILED);

        subject.sanitizeFinalActionsAndLogAnomalies(parentFrame, log, Level.ERROR);

        verify(logBuilder)
                .log(eq("Invalid at end of EVM run: {} ({})"), anomaliesCaptor.capture(), contextCaptor.capture());
        final var expectedAnomalies = "currentActionsStack not empty, has 1 elements left";
        assertEquals(expectedAnomalies, anomaliesCaptor.getValue());
        final var expectedContext =
                "originator 0000000000000000000000000000001234576890 sender 89abcdef89abcdef89abcdef89abcdef89abcdef recipient 89abcdef89abcdef89abcdef89abcdef89abcdef contract 0000000000000000000000000000000000000167 type MESSAGE_CALL state COMPLETED_FAILED";
        assertEquals(expectedContext, contextCaptor.getValue());
    }

    @Test
    void withoutHaltReasonJustUsesEmptyErrorAndNullsCalledContract() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.EXCEPTIONAL_HALT);
        final var wrappedAction = new Wrapper<>(CREATE_ACTION);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);
        given(parentFrame.getType()).willReturn(CONTRACT_CREATION);

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = wrappedAction.get();
        assertNull(finalAction.recipientContract());
        assertEquals(REMAINING_GAS, finalAction.gasUsed());
        assertSame(Bytes.EMPTY, finalAction.error());
        assertTrue(actionsStack.isEmpty());
    }

    @Test
    void configuresPrecompileActionAsExpected() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.EXCEPTIONAL_HALT);
        final var pretendPrecompileAction = CALL_ACTION
                .copyBuilder()
                .targetedAddress(tuweniToPbjBytes(HTS_PRECOMPILE_ADDRESS))
                .build();
        final var wrappedAction = new Wrapper<>(pretendPrecompileAction);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getContractAddress()).willReturn(HTS_PRECOMPILE_ADDRESS);

        subject.finalizeLastActionAsPrecompileIn(parentFrame, PRECOMPILE, true);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = wrappedAction.get();
        assertEquals(
                ContractID.newBuilder()
                        .contractNum(ConversionUtils.numberOfLongZero(HTS_PRECOMPILE_ADDRESS))
                        .build(),
                finalAction.recipientContract());
        assertEquals(PRECOMPILE, finalAction.callType());
        assertTrue(actionsStack.isEmpty());
        assertEquals(1, invalidActions.size());
        assertSame(finalAction, invalidActions.get(0).get());
        assertEquals(wrappedAction, invalidActions.get(0));
    }

    @Test
    void doesNotPushInvalidIfInappropriate() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.EXCEPTIONAL_HALT);
        final var pretendPrecompileAction = CALL_ACTION
                .copyBuilder()
                .targetedAddress(tuweniToPbjBytes(HTS_PRECOMPILE_ADDRESS))
                .build();
        final var wrappedAction = new Wrapper<>(pretendPrecompileAction);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getContractAddress()).willReturn(HTS_PRECOMPILE_ADDRESS);
        given(helper.isValid(any())).willReturn(true);

        subject.finalizeLastActionAsPrecompileIn(parentFrame, PRECOMPILE, true);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = wrappedAction.get();
        assertEquals(
                ContractID.newBuilder()
                        .contractNum(ConversionUtils.numberOfLongZero(HTS_PRECOMPILE_ADDRESS))
                        .build(),
                finalAction.recipientContract());
        assertEquals(PRECOMPILE, finalAction.callType());
        assertTrue(actionsStack.isEmpty());
        assertTrue(invalidActions.isEmpty());
    }

    @Test
    void withNonMissingHaltReasonJustSetsIt() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.EXCEPTIONAL_HALT);
        final var wrappedAction = new Wrapper<>(CALL_ACTION);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getExceptionalHaltReason()).willReturn(Optional.of(ILLEGAL_STATE_CHANGE));

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = wrappedAction.get();
        assertEquals(REMAINING_GAS, finalAction.gasUsed());
        assertEquals(Bytes.wrap(ILLEGAL_STATE_CHANGE.toString()), finalAction.error());
        assertTrue(actionsStack.isEmpty());
    }

    @Test
    void withInvalidSolidityAddressHaltReasonAddsSyntheticInvalidActionToCall() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.EXCEPTIONAL_HALT);
        final var wrappedAction = new Wrapper<>(CALL_ACTION);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getExceptionalHaltReason()).willReturn(Optional.of(MISSING_ADDRESS));
        given(helper.createSynthActionForMissingAddressIn(parentFrame)).willReturn(MISSING_ADDRESS_CALL_ACTION);

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(2, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = wrappedAction.get();
        assertEquals(REMAINING_GAS, finalAction.gasUsed());
        final var syntheticWrappedAction = allActions.get(1);
        final var syntheticAction = syntheticWrappedAction.get();
        assertSame(MISSING_ADDRESS_CALL_ACTION, syntheticAction);
    }

    @Test
    void withInvalidSolidityAddressHaltReasonDoesNotAddSyntheticInvalidActionToCreate() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.EXCEPTIONAL_HALT);
        final var wrappedAction = new Wrapper<>(CREATE_ACTION);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);
        given(parentFrame.getType()).willReturn(CONTRACT_CREATION);
        given(parentFrame.getExceptionalHaltReason()).willReturn(Optional.of(MISSING_ADDRESS));

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = wrappedAction.get();
        assertEquals(REMAINING_GAS, finalAction.gasUsed());
    }

    @ParameterizedTest
    @CsvSource({"NOT_STARTED,true", "CODE_EXECUTING,false", "CODE_SUSPENDED,true"})
    void finalizationDoesNothingButMaybePopStackForUnexpectedStates(
            @NonNull final MessageFrame.State state, final boolean actionIsOnStack) {
        given(parentFrame.getState()).willReturn(state);
        final var wrappedAction = new Wrapper<>(CALL_ACTION);
        allActions.add(wrappedAction);
        if (actionIsOnStack) {
            actionsStack.push(wrappedAction);
        }

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        assertEquals(CALL_ACTION, allActions.get(0).get());
        assertTrue(actionsStack.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "COMPLETED_SUCCESS,CREATE,false",
        "CODE_SUCCESS,CREATE,false",
        "COMPLETED_SUCCESS,CALL,false",
        "CODE_SUCCESS,CALL,false",
        "COMPLETED_SUCCESS,CALL,true",
        "CODE_SUCCESS,CALL,true",
    })
    void finalizationWorksAsExpectedForSuccessfulLazyCreatesAndNormalCalls(
            @NonNull final MessageFrame.State state,
            @NonNull final ContractActionType type,
            final boolean isLazyCreation) {
        given(parentFrame.getState()).willReturn(state);
        final var gasUsed = REMAINING_GAS / 3;
        given(parentFrame.getRemainingGas()).willReturn(REMAINING_GAS - gasUsed);
        if (type == CALL) {
            given(parentFrame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));
        }
        final var action = (type == CREATE) ? CREATE_ACTION : (isLazyCreation ? LAZY_CREATE_ACTION : CALL_ACTION);
        if (isLazyCreation) {
            givenResolvableEvmAddress();
        }

        final var wrappedAction = new Wrapper<>(action);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = allActions.get(0).get();
        assertEquals(gasUsed, finalAction.gasUsed());
        if (type == CREATE) {
            assertEquals(Bytes.EMPTY, finalAction.output());
        } else {
            assertEquals(OUTPUT_DATA, finalAction.output());
            if (isLazyCreation) {
                assertNull(finalAction.targetedAddress());
                assertEquals(CALLED_EOA_ID, finalAction.recipientAccount());
            }
        }
        assertTrue(actionsStack.isEmpty());
    }

    @Test
    void revertReasonPreservedIfPresentAndCreationRecipientNulledOut() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.REVERT);
        final var gasUsed = REMAINING_GAS / 3;
        given(parentFrame.getRemainingGas()).willReturn(REMAINING_GAS - gasUsed);
        given(parentFrame.getRevertReason()).willReturn(Optional.of(SOME_REVERT_REASON));
        given(parentFrame.getType()).willReturn(CONTRACT_CREATION);

        final var wrappedAction = new Wrapper<>(CREATE_ACTION);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = allActions.get(0).get();
        assertEquals(gasUsed, finalAction.gasUsed());
        assertNull(finalAction.recipientContract());
        assertEquals(tuweniToPbjBytes(SOME_REVERT_REASON), finalAction.revertReason());
    }

    @Test
    void emptyRevertReasonUsedIfMissing() {
        given(parentFrame.getState()).willReturn(MessageFrame.State.REVERT);
        final var gasUsed = REMAINING_GAS / 3;
        given(parentFrame.getRemainingGas()).willReturn(REMAINING_GAS - gasUsed);
        given(parentFrame.getType()).willReturn(CONTRACT_CREATION);

        final var wrappedAction = new Wrapper<>(CALL_ACTION);
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);

        subject.finalizeLastActionIn(parentFrame, false);

        assertEquals(1, allActions.size());
        assertEquals(wrappedAction, allActions.get(0));
        final var finalAction = allActions.get(0).get();
        assertEquals(gasUsed, finalAction.gasUsed());
        assertEquals(Bytes.EMPTY, finalAction.revertReason());
    }

    @Test
    void tracksTopLevelCreationAsExpected() {
        givenResolvableEvmAddress();

        given(parentFrame.getType()).willReturn(CONTRACT_CREATION);
        given(parentFrame.getOriginatorAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(parentFrame.getRemainingGas()).willReturn(REMAINING_GAS);
        given(parentFrame.getInputData()).willReturn(pbjToTuweniBytes(CALL_DATA));
        given(parentFrame.getValue()).willReturn(WEI_VALUE);
        given(parentFrame.getMessageStackDepth()).willReturn(STACK_DEPTH);
        given(parentFrame.getCode()).willReturn(CONTRACT_CODE);
        given(parentFrame.getContractAddress()).willReturn(EIP_1014_ADDRESS);

        subject.pushActionOfTopLevel(parentFrame);

        assertEquals(1, allActions.size());
        assertEquals(1, actionsStack.size());
        final var action = actionsStack.peek().get();
        assertSame(action, allActions.get(0).get());

        assertEquals(CREATE, action.callType());
        assertEquals(OP_CREATE, action.callOperationType());
        assertEquals(REMAINING_GAS, action.gas());
        assertEquals(CALL_DATA, action.input());
        assertEquals(VALUE, action.value());
        assertEquals(STACK_DEPTH, action.callDepth());
        assertEquals(CALLED_CONTRACT_ID, action.recipientContract());
        assertEquals(NON_SYSTEM_ACCOUNT_ID, action.callingAccount());
    }

    @Test
    void tracksTopLevelCallToEoaAsExpected() {
        givenResolvableEvmAddress();

        given(worldUpdater.get(EIP_1014_ADDRESS)).willReturn(account);
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getOriginatorAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(parentFrame.getRemainingGas()).willReturn(REMAINING_GAS);
        given(parentFrame.getInputData()).willReturn(pbjToTuweniBytes(CALL_DATA));
        given(parentFrame.getValue()).willReturn(WEI_VALUE);
        given(parentFrame.getMessageStackDepth()).willReturn(STACK_DEPTH);
        given(parentFrame.getCode()).willReturn(CodeV0.EMPTY_CODE);
        given(parentFrame.getContractAddress()).willReturn(EIP_1014_ADDRESS);

        subject.pushActionOfTopLevel(parentFrame);

        assertEquals(1, allActions.size());
        assertEquals(1, actionsStack.size());
        final var action = actionsStack.peek().get();
        assertSame(action, allActions.get(0).get());

        assertEquals(CALL, action.callType());
        assertEquals(OP_CALL, action.callOperationType());
        assertEquals(REMAINING_GAS, action.gas());
        assertEquals(CALL_DATA, action.input());
        assertEquals(VALUE, action.value());
        assertEquals(STACK_DEPTH, action.callDepth());
        assertEquals(CALLED_EOA_ID, action.recipientAccount());
        assertEquals(NON_SYSTEM_ACCOUNT_ID, action.callingAccount());
    }

    @Test
    void tracksTopLevelCallToMissingAsExpected() {
        given(parentFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(parentFrame.getOriginatorAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(parentFrame.getRemainingGas()).willReturn(REMAINING_GAS);
        given(parentFrame.getInputData()).willReturn(pbjToTuweniBytes(CALL_DATA));
        given(parentFrame.getValue()).willReturn(WEI_VALUE);
        given(parentFrame.getMessageStackDepth()).willReturn(STACK_DEPTH);
        given(parentFrame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        given(parentFrame.getWorldUpdater()).willReturn(worldUpdater);

        subject.pushActionOfTopLevel(parentFrame);

        assertEquals(1, allActions.size());
        assertEquals(1, actionsStack.size());
        final var action = actionsStack.peek().get();
        assertSame(action, allActions.get(0).get());

        assertEquals(CALL, action.callType());
        assertEquals(OP_CALL, action.callOperationType());
        assertEquals(REMAINING_GAS, action.gas());
        assertEquals(CALL_DATA, action.input());
        assertEquals(VALUE, action.value());
        assertEquals(STACK_DEPTH, action.callDepth());
        assertNull(action.recipientAccount());
        assertEquals(tuweniToPbjBytes(EIP_1014_ADDRESS), action.targetedAddress());
        assertEquals(NON_SYSTEM_ACCOUNT_ID, action.callingAccount());
    }

    @Test
    void tracksIntermediateCallAsExpected() {
        given(operation.getOpcode()).willReturn(0xF1);
        given(parentFrame.getCurrentOperation()).willReturn(operation);
        given(parentFrame.getMessageFrameStack()).willReturn(frameStack);
        given(parentFrame.getContractAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frameStack.peek()).willReturn(childFrame);

        given(childFrame.getType()).willReturn(MessageFrame.Type.MESSAGE_CALL);
        given(childFrame.getRemainingGas()).willReturn(REMAINING_GAS);
        given(childFrame.getInputData()).willReturn(pbjToTuweniBytes(CALL_DATA));
        given(childFrame.getValue()).willReturn(WEI_VALUE);
        given(childFrame.getMessageStackDepth()).willReturn(STACK_DEPTH);
        given(childFrame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        given(childFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(EIP_1014_ADDRESS)).willReturn(account);
        given(worldUpdater.getHederaContractId(EIP_1014_ADDRESS)).willReturn(CALLED_CONTRACT_ID);

        subject.pushActionOfIntermediate(parentFrame);

        assertEquals(1, allActions.size());
        assertEquals(1, actionsStack.size());
        final var action = actionsStack.peek().get();
        assertSame(action, allActions.get(0).get());

        assertEquals(CALL, action.callType());
        assertEquals(OP_CALL, action.callOperationType());
        assertEquals(REMAINING_GAS, action.gas());
        assertEquals(CALL_DATA, action.input());
        assertEquals(VALUE, action.value());
        assertEquals(STACK_DEPTH, action.callDepth());
        assertEquals(CALLED_CONTRACT_ID, action.recipientContract());
        assertEquals(NON_SYSTEM_CONTRACT_ID, action.callingContract());
    }

    private void givenResolvableEvmAddress() {
        given(parentFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.getHederaContractId(EIP_1014_ADDRESS)).willReturn(CALLED_CONTRACT_ID);
    }
}
