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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static com.hedera.hapi.streams.CallOperationType.*;
import static com.hedera.hapi.streams.ContractActionType.CALL;
import static com.hedera.hapi.streams.ContractActionType.CREATE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.*;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Encapsulates a stack of contract actions.
 */
public class ActionStack {
    private static final int OP_CODE_CREATE = 0xF0;
    private static final int OP_CODE_CALL = 0xF1;
    private static final int OP_CODE_CALLCODE = 0xF2;
    private static final int OP_CODE_DELEGATECALL = 0xF4;
    private static final int OP_CODE_CREATE2 = 0xF5;
    private static final int OP_CODE_STATICCALL = 0xFA;
    private final List<ContractAction> allActions;
    private final List<ContractAction> invalidActions;
    private final Deque<ContractAction> actionsStack;

    public ActionStack() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayDeque<>());
    }

    /**
     * Convenience constructor for testing.
     *
     * @param allActions     the container to use for all tracked actions
     * @param invalidActions the container to use for invalid actions
     * @param actionsStack   the stack to use for actions
     */
    public ActionStack(
            @NonNull final List<ContractAction> allActions,
            @NonNull final List<ContractAction> invalidActions,
            @NonNull final Deque<ContractAction> actionsStack) {
        this.allActions = allActions;
        this.invalidActions = invalidActions;
        this.actionsStack = actionsStack;
    }

    /**
     * Given a {@link MessageFrame} which should be the initial frame for a HAPI
     * contract operation, pushes its action onto the stack.
     *
     * <p>The action's type will derive from the type of the frame itself
     * ({@code CONTRACT_CREATION} or {@code MESSAGE_CALL}); and the action's
     * calling account will derive from the frame's
     * {@link MessageFrame#getOriginatorAddress()}.
     *
     * @param frame the initial frame of a HAPI contract operation
     */
    public void pushActionOfTopLevel(@NonNull final MessageFrame frame) {
        final var builder = ContractAction.newBuilder()
                .callOperationType(asCallOperationType(frame.getType()))
                .callingAccount(accountIdWith(hederaIdNumOfOriginatorIn(frame)));
        completeAction(builder, frame);
    }

    /**
     * Given a {@link MessageFrame} which should be an intermediate frame
     * for a contract operation, pushes its action onto the stack.
     *
     * <p>The action's type will derive from the opcode being executed in the {@code frame};
     * and the calling contract will be the {@link MessageFrame#getContractAddress()} of the
     * given {@code frame}.
     *
     * @param frame the frame executing an action
     */
    public void pushActionOfIntermediate(@NonNull final MessageFrame frame) {
        final var builder = ContractAction.newBuilder()
                .callOperationType(
                        asCallOperationType(frame.getCurrentOperation().getOpcode()))
                .callingContract(contractIdWith(hederaIdNumOfContractIn(frame)));
        completeAction(builder, requireNonNull(frame.getMessageFrameStack().peek()));
    }

    private void completeAction(@NonNull ContractAction.Builder builder, @NonNull final MessageFrame frame) {
        builder.callType(asActionType(frame.getType()))
                .gas(frame.getRemainingGas())
                .input(tuweniToPbjBytes(frame.getInputData()))
                .value(frame.getValue().toLong())
                .callDepth(frame.getMessageStackDepth());
        if (frame.getType() == MESSAGE_CALL && isMissing(frame, frame.getContractAddress())) {
            builder.targetedAddress(tuweniToPbjBytes(frame.getContractAddress()));
        } else if (CodeV0.EMPTY_CODE.equals(frame.getCode())) {
            builder.recipientAccount(accountIdWith(hederaIdNumOfContractIn(frame)));
        } else {
            builder.recipientContract(contractIdWith(hederaIdNumOfContractIn(frame)));
        }
        final var action = builder.build();
        allActions.add(action);
        actionsStack.push(action);
    }

    private ContractActionType asActionType(final MessageFrame.Type type) {
        return switch (type) {
            case CONTRACT_CREATION -> CREATE;
            case MESSAGE_CALL -> CALL;
        };
    }

    private CallOperationType asCallOperationType(final MessageFrame.Type type) {
        return type == CONTRACT_CREATION ? OP_CREATE : OP_CALL;
    }

    private CallOperationType asCallOperationType(final int opCode) {
        return switch (opCode) {
            case OP_CODE_CREATE -> OP_CREATE;
            case OP_CODE_CALL -> OP_CALL;
            case OP_CODE_CALLCODE -> OP_CALLCODE;
            case OP_CODE_DELEGATECALL -> OP_DELEGATECALL;
            case OP_CODE_CREATE2 -> OP_CREATE2;
            case OP_CODE_STATICCALL -> OP_STATICCALL;
            default -> OP_UNKNOWN;
        };
    }

    private boolean isMissing(@NonNull final MessageFrame frame, @NonNull final Address address) {
        return frame.getWorldUpdater().get(address) == null;
    }

    private AccountID accountIdWith(final long num) {
        return AccountID.newBuilder().accountNum(num).build();
    }

    private ContractID contractIdWith(final long num) {
        return ContractID.newBuilder().contractNum(num).build();
    }
}
