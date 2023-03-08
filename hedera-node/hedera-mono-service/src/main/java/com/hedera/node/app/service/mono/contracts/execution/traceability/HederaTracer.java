/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.execution.traceability;

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
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.frame.MessageFrame.Type;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

public class HederaTracer implements HederaOperationTracer {

    private static final Logger log = LogManager.getLogger(HederaTracer.class);

    @VisibleForTesting
    protected Level logLevel = Level.DEBUG;

    @VisibleForTesting
    protected List<SolidityAction> allActions;

    @VisibleForTesting
    protected List<SolidityAction> invalidActions;

    @VisibleForTesting
    protected final Deque<SolidityAction> currentActionsStack;

    private final EmitActionSidecars doEmitActionSidecars;
    private final ValidateActionSidecars doValidateActionSidecars;

    private static final int OP_CODE_CREATE = 0xF0;
    private static final int OP_CODE_CALL = 0xF1;
    private static final int OP_CODE_CALLCODE = 0xF2;
    private static final int OP_CODE_DELEGATECALL = 0xF4;
    private static final int OP_CODE_CREATE2 = 0xF5;
    private static final int OP_CODE_STATICCALL = 0xFA;

    public enum EmitActionSidecars {
        DISABLED,
        ENABLED
    }

    public enum ValidateActionSidecars {
        DISABLED,
        ENABLED
    }

    public HederaTracer(
            final EmitActionSidecars emitActionSidecars, final ValidateActionSidecars validateActionSidecars) {
        this.currentActionsStack = new ArrayDeque<>();
        this.allActions = new ArrayList<>();
        this.invalidActions = new ArrayList<>();
        this.doEmitActionSidecars = emitActionSidecars;
        this.doValidateActionSidecars = validateActionSidecars;
    }

    @Override
    public void init(final MessageFrame initialFrame) {
        if (areActionSidecarsEnabled()) {
            trackTopLevelActionFor(initialFrame);
        }
    }

    @Override
    public void finalizeOperation(final MessageFrame initialFrame) {
        if (areActionSidecarsEnabled() && isActionSidecarValidationEnabled()) {
            // Two possible error conditions are that invalid actions (mainly `oneof` fields that
            // don't have _exactly_ one alternative set) are added to the set of actions (which can
            // cause problems when ingesting sidecar records elsewhere) and that the current actions
            // stack is out-of-sync (due to some error elsewhere).  (The latter condition has not yet
            // been observed outside deliberate fault injection and probably doesn't happen in a
            // running system.)
            if (!currentActionsStack.isEmpty() || !invalidActions.isEmpty()) {
                log.atLevel(logLevel)
                        .log(
                                "Invalid at end of EVM run: {} ({})",
                                () -> formatAnomaliesAtFinalizationForLog(invalidActions),
                                () -> formatFrameContextForLog(initialFrame));
            }

            // Keep only _valid_ actions (this avoids problems when _ingesting_ action sidecars)
            if (!invalidActions.isEmpty()) {
                allActions.removeAll(invalidActions);
                invalidActions.clear();
            }
        }
    }

    @Override
    public void tracePostExecution(final MessageFrame currentFrame, final OperationResult operationResult) {
        if (areActionSidecarsEnabled()) {
            final var frameState = currentFrame.getState();
            if (frameState != State.CODE_EXECUTING) {
                if (frameState == State.CODE_SUSPENDED) {
                    final var nextFrame = currentFrame.getMessageFrameStack().peek();
                    trackInnerActionFor(nextFrame, currentFrame);
                } else {
                    popActionStack(currentFrame)
                            .ifPresent(action -> finalizeActionFor(action, currentFrame, frameState));
                }
            }
        }
    }

    private void trackTopLevelActionFor(final MessageFrame initialFrame) {
        trackNewAction(initialFrame, action -> {
            action.setCallOperationType(toCallOperationType(initialFrame.getType()));
            action.setCallingAccount(
                    EntityId.fromAddress(asMirrorAddress(initialFrame.getOriginatorAddress(), initialFrame)));
        });
    }

    private void trackInnerActionFor(final MessageFrame nextFrame, final MessageFrame parentFrame) {
        trackNewAction(nextFrame, action -> {
            action.setCallOperationType(
                    toCallOperationType(parentFrame.getCurrentOperation().getOpcode()));
            action.setCallingContract(
                    EntityId.fromAddress(asMirrorAddress(parentFrame.getContractAddress(), parentFrame)));
        });
    }

    private void trackNewAction(final MessageFrame messageFrame, final Consumer<SolidityAction> actionConfig) {
        final var action = new SolidityAction(
                toContractActionType(messageFrame.getType()),
                messageFrame.getRemainingGas(),
                messageFrame.getInputData().toArray(),
                messageFrame.getValue().toLong(),
                messageFrame.getMessageStackDepth());
        final var contractAddress = messageFrame.getContractAddress();
        if (messageFrame.getType() != Type.CONTRACT_CREATION
                && messageFrame.getWorldUpdater().getAccount(contractAddress) == null) {
            action.setTargetedAddress(contractAddress.toArray());
        } else {
            final var recipient = EntityId.fromAddress(asMirrorAddress(contractAddress, messageFrame));
            if (CodeV0.EMPTY_CODE.equals(messageFrame.getCode())) {
                // code can be empty when calling precompiles too, but we handle
                // that in tracePrecompileCall, after precompile execution is completed
                action.setRecipientAccount(recipient);
            } else {
                action.setRecipientContract(recipient);
            }
        }
        actionConfig.accept(action);

        allActions.add(action);
        if (isActionSidecarValidationEnabled() && !action.isValid()) {
            invalidActions.add(action);
        }
        currentActionsStack.push(action);
    }

    private void finalizeActionFor(final SolidityAction action, final MessageFrame frame, final State frameState) {

        switch (frameState) {
            case NOT_STARTED, CODE_EXECUTING, CODE_SUSPENDED:
                {
                    // these states are not "final" states needing to finalize the actions
                }
                break;

            case CODE_SUCCESS, COMPLETED_SUCCESS:
                {
                    action.setGasUsed(action.getGas() - frame.getRemainingGas());
                    // externalize output for calls only - create output is externalized in bytecode sidecar
                    if (action.getCallType() != CREATE) {
                        action.setOutput(frame.getOutputData().toArrayUnsafe());
                        if (action.getInvalidSolidityAddress() != null) {
                            // we had a successful lazy create, replace targeted address
                            // with its new Hedera id
                            final var recipientAsHederaId = EntityId.fromAddress(
                                    asMirrorAddress(Address.wrap(Bytes.of(action.getInvalidSolidityAddress())), frame));
                            action.setTargetedAddress(null);
                            action.setRecipientAccount(recipientAsHederaId);
                        }
                    } else {
                        action.setOutput(new byte[0]);
                    }
                }
                break;

            case REVERT:
                {
                    // deliberate failures do not burn extra gas
                    action.setGasUsed(action.getGas() - frame.getRemainingGas());
                    frame.getRevertReason()
                            .ifPresentOrElse(
                                    bytes -> action.setRevertReason(bytes.toArrayUnsafe()),
                                    () -> action.setRevertReason(new byte[0]));
                    if (frame.getType().equals(CONTRACT_CREATION)) {
                        action.setRecipientContract(null);
                    }
                }
                break;

            case EXCEPTIONAL_HALT, COMPLETED_FAILED:
                {
                    // exceptional exits always burn all gas
                    action.setGasUsed(action.getGas());
                    final var exceptionalHaltReasonOptional = frame.getExceptionalHaltReason();
                    if (exceptionalHaltReasonOptional.isPresent()) {
                        final var exceptionalHaltReason = exceptionalHaltReasonOptional.get();
                        action.setError(exceptionalHaltReason.name().getBytes(StandardCharsets.UTF_8));
                        // when a contract tries to call a non-existing address (resulting in a
                        // INVALID_SOLIDITY_ADDRESS failure),
                        // we have to create a synthetic action recording this, otherwise the details of the
                        // intended call
                        // (e.g. the targeted invalid address) and sequence of events leading to the failure
                        // are lost
                        if (action.getCallType().equals(CALL)
                                && exceptionalHaltReason.equals(INVALID_SOLIDITY_ADDRESS)) {
                            final var syntheticInvalidAction = new SolidityAction(
                                    CALL, frame.getRemainingGas(), null, 0, frame.getMessageStackDepth() + 1);
                            syntheticInvalidAction.setCallingContract(
                                    EntityId.fromAddress(asMirrorAddress(frame.getContractAddress(), frame)));
                            syntheticInvalidAction.setTargetedAddress(
                                    Words.toAddress(frame.getStackItem(1)).toArray());
                            syntheticInvalidAction.setError(
                                    INVALID_SOLIDITY_ADDRESS.name().getBytes(StandardCharsets.UTF_8));
                            syntheticInvalidAction.setCallOperationType(toCallOperationType(
                                    frame.getCurrentOperation().getOpcode()));
                            allActions.add(syntheticInvalidAction);
                        }
                    } else {
                        action.setError(new byte[0]);
                    }
                    if (frame.getType().equals(CONTRACT_CREATION)) {
                        action.setRecipientContract(null);
                    }
                }
                break;
        }
    }

    @Override
    public void tracePrecompileResult(final MessageFrame frame, final ContractActionType type) {
        if (areActionSidecarsEnabled()) {
            popActionStack(frame).ifPresent(lastAction -> {
                lastAction.setCallType(type);
                lastAction.setRecipientAccount(null);
                lastAction.setTargetedAddress(null);
                lastAction.setRecipientContract(EntityId.fromAddress(frame.getContractAddress()));
                finalizeActionFor(lastAction, frame, frame.getState());
            });
        }
    }

    @Override
    public void traceAccountCreationResult(final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
        frame.setExceptionalHaltReason(haltReason);
        if (areActionSidecarsEnabled()) {
            // we take the last action from the list since there is a chance
            // it has already been popped from the stack
            final var lastAction = allActions.get(allActions.size() - 1);
            finalizeActionFor(lastAction, frame, frame.getState());
        }
    }

    public List<SolidityAction> getActions() {
        return allActions;
    }

    private boolean areActionSidecarsEnabled() {
        return EmitActionSidecars.ENABLED == doEmitActionSidecars;
    }

    private boolean isActionSidecarValidationEnabled() {
        return ValidateActionSidecars.ENABLED == doValidateActionSidecars;
    }

    private ContractActionType toContractActionType(final MessageFrame.Type type) {
        return switch (type) {
            case CONTRACT_CREATION -> CREATE;
            case MESSAGE_CALL -> CALL;
        };
    }

    private CallOperationType toCallOperationType(final int opCode) {
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

    private CallOperationType toCallOperationType(final Type type) {
        return type == CONTRACT_CREATION ? OP_CREATE : OP_CALL;
    }

    private Address asMirrorAddress(final Address addressOrAlias, final MessageFrame messageFrame) {
        final var aliases = ((HederaStackedWorldStateUpdater) messageFrame.getWorldUpdater()).aliases();
        return aliases.resolveForEvm(addressOrAlias);
    }

    private Optional<SolidityAction> popActionStack(final MessageFrame frame) {
        if (!currentActionsStack.isEmpty()) {
            return Optional.of(currentActionsStack.pop());
        } else {
            log.atLevel(logLevel).log("Action stack prematurely empty ({})", () -> formatFrameContextForLog(frame));
            return Optional.empty();
        }
    }

    private String formatAnomaliesAtFinalizationForLog(final List<SolidityAction> invalidActions) {
        final var msgs = new ArrayList<String>();
        if (!this.currentActionsStack.isEmpty())
            msgs.add("currentActionsStack not empty, has %d elements left".formatted(this.currentActionsStack.size()));
        if (!invalidActions.isEmpty()) {
            msgs.add("of %d actions given, %d were invalid".formatted(this.allActions.size(), invalidActions.size()));
            for (final var ia : invalidActions) {
                msgs.add("invalid: %s".formatted(ia.toFullString()));
            }
        }
        return String.join("; ", msgs);
    }

    private static String formatFrameContextForLog(final MessageFrame frame) {
        if (null == frame) return "<no frame for context>";

        final Function<Address, String> addressToString = Address::toUnprefixedHexString;

        final var originator = get(frame, MessageFrame::getOriginatorAddress, addressToString);
        final var sender = get(frame, MessageFrame::getSenderAddress, addressToString);
        final var recipient = get(frame, MessageFrame::getRecipientAddress, addressToString);
        final var contract = get(frame, MessageFrame::getContractAddress, addressToString);
        final var type = get(frame, MessageFrame::getType, Object::toString);
        final var state = get(frame, MessageFrame::getState, Object::toString);

        return "originator %s sender %s recipient %s contract %s type %s state %s"
                .formatted(originator, sender, recipient, contract, type, state);
    }

    private static <E, I> String get(
            final E subject, final Function<E, I> getter, final Function<I, String> processor) {
        return null != subject ? processor.compose(getter).apply(subject) : "null";
    }
}
