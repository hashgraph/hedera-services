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
import static com.hedera.hapi.streams.ContractActionType.CALL;
import static com.hedera.hapi.streams.ContractActionType.CREATE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.streams.ContractAction;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionStackTest {
    @Mock
    private Account account;

    @Mock
    private Operation operation;

    @Mock
    private MessageFrame childFrame;

    @Mock
    private MessageFrame parentFrame;

    @Mock
    private ProxyWorldUpdater worldUpdater;

    @Mock
    private Deque<MessageFrame> frameStack;

    private List<ContractAction> allActions = new ArrayList<>();
    private List<ContractAction> invalidActions = new ArrayList<>();
    private Deque<ContractAction> actionsStack = new ArrayDeque<>();

    private ActionStack subject;

    @BeforeEach
    void setUp() {
        subject = new ActionStack(allActions, invalidActions, actionsStack);
    }

    @Test
    void tracksTopLevelCreationAsExpected() {
        givenResolvableEvmAddress();

        given(parentFrame.getType()).willReturn(MessageFrame.Type.CONTRACT_CREATION);
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
        final var action = actionsStack.peek();
        assertSame(action, allActions.get(0));

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
        final var action = actionsStack.peek();
        assertSame(action, allActions.get(0));

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
        final var action = actionsStack.peek();
        assertSame(action, allActions.get(0));

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
        givenResolvableEvmAddress();

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

        subject.pushActionOfIntermediate(parentFrame);

        assertEquals(1, allActions.size());
        assertEquals(1, actionsStack.size());
        final var action = actionsStack.peek();
        assertSame(action, allActions.get(0));

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
