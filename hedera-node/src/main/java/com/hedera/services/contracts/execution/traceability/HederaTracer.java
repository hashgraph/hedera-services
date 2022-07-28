/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution.traceability;

import static com.hedera.services.contracts.execution.traceability.ContractActionType.CALL;
import static com.hedera.services.contracts.execution.traceability.ContractActionType.CREATE;
import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;

import com.hedera.services.state.submerkle.EntityId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;

@Singleton
public class HederaTracer implements HederaOperationTracer {

    private final List<SolidityAction> allActions;
    private final Deque<SolidityAction> currentActionsStack;
    private boolean areActionSidecarsEnabled;

    @Inject
    public HederaTracer() {
        this.currentActionsStack = new ArrayDeque<>();
        this.allActions = new ArrayList<>();
    }

    @Override
    public void reset(final boolean areActionSidecarsEnabled) {
        this.currentActionsStack.clear();
        this.allActions.clear();
        this.areActionSidecarsEnabled = areActionSidecarsEnabled;
    }

    @Override
    public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {
        if (areActionSidecarsEnabled && currentActionsStack.isEmpty()) {
            trackActionFor(frame, 0);
        }

        executeOperation.execute();

        if (areActionSidecarsEnabled) {
            final var frameState = frame.getState();
            if (frameState != State.CODE_EXECUTING) {
                if (frameState == State.CODE_SUSPENDED) {
                    final var nextFrame = frame.getMessageFrameStack().peek();
                    trackActionFor(nextFrame, nextFrame.getMessageFrameStack().size() - 1);
                } else {
                    finalizeActionFor(currentActionsStack.pop(), frame, frameState);
                }
            }
        }
    }

    private void trackActionFor(final MessageFrame frame, final int callDepth) {
        // code can be empty when calling precompiles too, but we handle
        // that in tracePrecompileCall, after precompile execution is completed
        final var isCallToAccount = Code.EMPTY.equals(frame.getCode());
        final var isTopLevelEVMTransaction = callDepth == 0;
        final var action =
                new SolidityAction(
                        toContractActionType(frame.getType()),
                        isTopLevelEVMTransaction
                                ? EntityId.fromAddress(frame.getOriginatorAddress())
                                : null,
                        !isTopLevelEVMTransaction
                                ? EntityId.fromAddress(frame.getSenderAddress())
                                : null,
                        frame.getRemainingGas(),
                        frame.getInputData().toArray(),
                        isCallToAccount ? EntityId.fromAddress(frame.getContractAddress()) : null,
                        !isCallToAccount ? EntityId.fromAddress(frame.getContractAddress()) : null,
                        frame.getValue().toLong(),
                        callDepth);
        allActions.add(action);
        currentActionsStack.push(action);
    }

    private void finalizeActionFor(
            final SolidityAction action, final MessageFrame frame, final State frameState) {
        if (frameState == State.CODE_SUCCESS || frameState == State.COMPLETED_SUCCESS) {
            action.setGasUsed(action.getGas() - frame.getRemainingGas());
            // externalize output for calls only - create output is externalized in bytecode sidecar
            if (action.getCallType() != CREATE) {
                action.setOutput(frame.getOutputData().toArrayUnsafe());
            } else {
                action.setOutput(new byte[0]);
            }
        } else if (frameState == State.REVERT) {
            // deliberate failures do not burn extra gas
            action.setGasUsed(action.getGas() - frame.getRemainingGas());
            frame.getRevertReason()
                    .ifPresentOrElse(
                            bytes -> action.setRevertReason(bytes.toArrayUnsafe()),
                            () -> action.setRevertReason(new byte[0]));
        } else if (frameState == State.EXCEPTIONAL_HALT) {
            // exceptional exits always burn all gas
            action.setGasUsed(action.getGas());
            final var exceptionalHaltReasonOptional = frame.getExceptionalHaltReason();
            if (exceptionalHaltReasonOptional.isPresent()) {
                final var exceptionalHaltReason = exceptionalHaltReasonOptional.get();
                action.setError(
                        exceptionalHaltReason.getDescription().getBytes(StandardCharsets.UTF_8));
                if (exceptionalHaltReason.equals(INVALID_SOLIDITY_ADDRESS)) {
                    if (action.getRecipientAccount() != null) {
                        action.setInvalidSolidityAddress(
                                action.getRecipientAccount().toEvmAddress().toArrayUnsafe());
                        action.setRecipientAccount(null);
                    } else {
                        action.setInvalidSolidityAddress(
                                action.getRecipientContract().toEvmAddress().toArrayUnsafe());
                        action.setRecipientContract(null);
                    }
                }
            } else {
                action.setError(new byte[0]);
            }
        }
    }

    @Override
    public void tracePrecompileResult(final MessageFrame frame, final ContractActionType type) {
        if (areActionSidecarsEnabled) {
            final var lastAction = currentActionsStack.pop();
            lastAction.setCallType(type);
            lastAction.setRecipientAccount(null);
            lastAction.setRecipientContract(EntityId.fromAddress(frame.getContractAddress()));
            finalizeActionFor(lastAction, frame, frame.getState());
        }
    }

    @Override
    public void traceAccountCreationResult(
            final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
        frame.setExceptionalHaltReason(haltReason);
    }

    private ContractActionType toContractActionType(final MessageFrame.Type type) {
        return switch (type) {
            case CONTRACT_CREATION -> CREATE;
            case MESSAGE_CALL -> CALL;
        };
    }

    @Override
    public List<SolidityAction> getActions() {
        return allActions;
    }
}
