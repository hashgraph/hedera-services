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
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.MISSING_ADDRESS;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Encapsulates a stack of contract actions.
 */
public class ActionStack {
    private final ActionsHelper helper;
    private final List<Wrapper<ContractAction>> allActions;
    private final Deque<Wrapper<ContractAction>> actionsStack;
    private final List<Wrapper<ContractAction>> invalidActions;

    public ActionStack() {
        this(new ActionsHelper(), new ArrayList<>(), new ArrayList<>(), new ArrayDeque<>());
    }

    /**
     * Convenience constructor for testing.
     *
     * @param helper         the helper to use for action creation
     * @param allActions     the container to use for all tracked actions
     * @param invalidActions the container to use for invalid actions
     * @param actionsStack   the stack to use for actions
     */
    public ActionStack(
            @NonNull final ActionsHelper helper,
            @NonNull final List<Wrapper<ContractAction>> invalidActions,
            @NonNull final List<Wrapper<ContractAction>> allActions,
            @NonNull final Deque<Wrapper<ContractAction>> actionsStack) {
        this.helper = helper;
        this.invalidActions = invalidActions;
        this.allActions = allActions;
        this.actionsStack = actionsStack;
    }

    /**
     * Finalizes the last action created in this stack in the context of the given frame; and
     * also pops the action from the stack if it is topmost. The nature of finalization work
     * depends on the state of the given frame.
     * <ul>
     *     <li>For the {@code NOT_STARTED}, {@code CODE_EXECUTING}, and {@code CODE_SUSPENDED}
     *     states, finalization is a no-op (these are not even final states, really).</li>
     *     <li>For the {@code CODE_SUCCESS} and {@code COMPLETED_SUCCESS} states, finalization
     *     sets the gas used and the output data; and for calls that performed a lazy creation,
     *     the details of that creation are also set.</li>
     *     <li>For the {@code REVERT} state, finalization again sets gas used, along with the
     *     revert reason; and for a reverted creation, nulls out the recipient contract.</li>
     *     <li>For {@code EXCEPTIONAL_HALT} and {@code COMPLETED_FAILED} states, finalization
     *     again sets gas used and nulls out the recipient contract for a halted creation; and
     *     sets the error causing the failure. When the error is {@code INVALID_SOLIDITY_ADDRESS},
     *     also constructs a synthetic action to represent the invalid call.
     * </ul>
     *
     * @param frame    the frame to use for finalization context
     * @param validate whether to validate the final action
     */
    public void finalizeLastActionIn(@NonNull final MessageFrame frame, final boolean validate) {
        internalFinalize(validate, frame, UnaryOperator.identity());
    }

    /**
     * Does the same work as {@link #finalizeLastActionIn(MessageFrame, boolean)}, but takes a couple
     * extra steps to ensure the final action is customized for the given precompile type.
     *
     * @param frame the frame to use for finalization context
     * @param type  the finalized action's precompile type
     * @param validate whether to validate the final action
     */
    public void finalizeLastActionAsPrecompileIn(
            @NonNull final MessageFrame frame,
            @NonNull final ContractActionType type,
            final boolean validate) {
        internalFinalize(validate, frame, action -> action.copyBuilder()
                .recipientContract(asNumberedContractId(frame.getContractAddress()))
                .callType(type)
                .build());
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
        completePush(builder, frame);
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
                .callOperationType(ConversionUtils.asCallOperationType(
                        frame.getCurrentOperation().getOpcode()))
                .callingContract(contractIdWith(hederaIdNumOfContractIn(frame)));
        completePush(builder, requireNonNull(frame.getMessageFrameStack().peek()));
    }

    /**
     * Given the initial {@link MessageFrame} for all operations applied to this stack,
     * sanitizes the final actions and logs any anomalies.
     */
    public void sanitizeFinalActionsAndLogAnomalies(
            @NonNull final MessageFrame frame, @NonNull final Logger log, @NonNull final Level level) {
        // Two possible error conditions are that invalid actions (mainly `oneof` fields that
        // don't have _exactly_ one alternative set) are added to the set of actions (which can
        // cause problems when ingesting sidecar records elsewhere) and that the current actions
        // stack is out-of-sync (due to some error elsewhere).  (The latter condition has not yet
        // been observed outside deliberate fault injection and probably doesn't happen in a
        // running system.)
        if (!actionsStack.isEmpty() || !invalidActions.isEmpty()) {
            final var anomalies = formatAnomaliesAtFinalizationForLog();
            final var frameContext = formatFrameContextForLog(frame);
            log.atLevel(level).log("Invalid at end of EVM run: {} ({})", anomalies, frameContext);
        }

        // Keep only _valid_ actions (this avoids problems when _ingesting_ action sidecars)
        if (!invalidActions.isEmpty()) {
            allActions.removeAll(invalidActions);
            invalidActions.clear();
        }
    }

    private void completePush(@NonNull ContractAction.Builder builder, @NonNull final MessageFrame frame) {
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
        final var wrappedAction = new Wrapper<>(builder.build());
        allActions.add(wrappedAction);
        actionsStack.push(wrappedAction);
    }

    private void internalFinalize(
            final boolean validate,
            @NonNull final MessageFrame frame,
            @NonNull final UnaryOperator<ContractAction> transform) {
        requireNonNull(frame);
        requireNonNull(transform);
        final var lastWrappedAction = requireNonNull(allActions.get(allActions.size() - 1));
        // Intentional use of referential equality here, only pop if this exact wrapper is on the stack
        if (lastWrappedAction == actionsStack.peek()) {
            actionsStack.pop();
        }
        // Swap in the final form of the last action
        lastWrappedAction.set(transform.apply(finalFormOf(lastWrappedAction.get(), frame)));
        if (validate && !helper.isValid(lastWrappedAction.get())) {
            invalidActions.add(lastWrappedAction);
        }
    }

    private ContractAction finalFormOf(
            @NonNull final ContractAction action,
            @NonNull final MessageFrame frame) {
        return switch (frame.getState()) {
            case NOT_STARTED, CODE_EXECUTING, CODE_SUSPENDED -> action;
            case CODE_SUCCESS, COMPLETED_SUCCESS -> {
                final var builder = action.copyBuilder();
                builder.gasUsed(action.gas() - frame.getRemainingGas());
                if (action.callType() == CREATE) {
                    builder.output(Bytes.EMPTY);
                } else {
                    builder.output(tuweniToPbjBytes(frame.getOutputData()));
                    if (action.targetedAddress() != null) {
                        builder.targetedAddress(null);
                        final var lazyCreatedAddress = pbjToBesuAddress(action.targetedAddressOrThrow());
                        builder.recipientAccount(accountIdWith(hederaIdNumberIn(frame, lazyCreatedAddress)));
                    }
                }
                yield builder.build();
            }
            case REVERT -> {
                final var builder = action.copyBuilder();
                builder.gasUsed(action.gas() - frame.getRemainingGas());
                frame.getRevertReason()
                        .ifPresentOrElse(
                                reason -> builder.revertReason(tuweniToPbjBytes(reason)),
                                () -> builder.revertReason(Bytes.EMPTY));
                if (frame.getType() == CONTRACT_CREATION) {
                    builder.recipientContract((ContractID) null);
                }
                yield builder.build();
            }
            case EXCEPTIONAL_HALT, COMPLETED_FAILED -> {
                final var builder = action.copyBuilder();
                builder.gasUsed(action.gas());
                final var maybeHaltReason = frame.getExceptionalHaltReason();
                if (maybeHaltReason.isPresent()) {
                    final var haltReason = maybeHaltReason.get();
                    builder.error(Bytes.wrap(haltReason.name().getBytes(UTF_8)));
                    if (CALL.equals(action.callType()) && MISSING_ADDRESS.equals(haltReason)) {
                        allActions.add(new Wrapper<>(helper.createSynthActionForMissingAddressIn(frame)));
                    }
                } else {
                    builder.error(Bytes.EMPTY);
                }
                if (frame.getType() == CONTRACT_CREATION) {
                    builder.recipientContract((ContractID) null);
                }
                yield builder.build();
            }
        };
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

    private boolean isMissing(@NonNull final MessageFrame frame, @NonNull final Address address) {
        return frame.getWorldUpdater().get(address) == null;
    }

    private AccountID accountIdWith(final long num) {
        return AccountID.newBuilder().accountNum(num).build();
    }

    private ContractID contractIdWith(final long num) {
        return ContractID.newBuilder().contractNum(num).build();
    }

    private String formatAnomaliesAtFinalizationForLog() {
        final var msgs = new ArrayList<String>();
        if (!actionsStack.isEmpty())
            msgs.add("currentActionsStack not empty, has %d elements left".formatted(actionsStack.size()));
        if (!invalidActions.isEmpty()) {
            msgs.add("of %d actions given, %d were invalid".formatted(this.allActions.size(), invalidActions.size()));
            for (final var ia : invalidActions) {
                msgs.add("invalid: %s".formatted(helper.prettyPrint(ia.get())));
            }
        }
        return String.join("; ", msgs);
    }

    private static String formatFrameContextForLog(@NonNull final MessageFrame frame) {
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
