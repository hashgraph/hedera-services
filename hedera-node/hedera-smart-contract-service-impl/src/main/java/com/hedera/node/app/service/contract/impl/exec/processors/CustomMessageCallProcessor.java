/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.processors;

import static com.hedera.hapi.streams.ContractActionType.PRECOMPILE;
import static com.hedera.hapi.streams.ContractActionType.SYSTEM;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INSUFFICIENT_CHILD_RECORDS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_FEE_SUBMITTED;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SIGNATURE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.acquiredSenderAuthorizationViaDelegateCall;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.alreadyHalted;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.isTopLevelTransaction;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.recordBuilderFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.setPropagatedCallFailure;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.transfersValue;
import static com.hedera.node.app.service.contract.impl.hevm.HevmPropagatedCallFailure.MISSING_RECEIVER_SIGNATURE;
import static com.hedera.node.app.service.contract.impl.hevm.HevmPropagatedCallFailure.RESULT_CANNOT_BE_EXTERNALIZED;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.PRECOMPILE_ERROR;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmAccount;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * A {@link MessageCallProcessor} customized to,
 * <ol>
 *  <li>Call Hedera-specific precompiles.</li>
 *  <li>Impose Hedera restrictions in the system account range.</li>
 *  <li>Do lazy creation when appropriate.</li>
 * </ol>
 * Note these only require changing {@link MessageCallProcessor#start(MessageFrame, OperationTracer)},
 * and the core {@link MessageCallProcessor#process(MessageFrame, OperationTracer)} logic we inherit.
 */
public class CustomMessageCallProcessor extends MessageCallProcessor {
    private final FeatureFlags featureFlags;
    private final AddressChecks addressChecks;
    private final PrecompileContractRegistry precompiles;
    private final Map<Address, HederaSystemContract> systemContracts;

    private enum ForLazyCreation {
        YES,
        NO,
    }

    public CustomMessageCallProcessor(
            @NonNull final EVM evm,
            @NonNull final FeatureFlags featureFlags,
            @NonNull final PrecompileContractRegistry precompiles,
            @NonNull final AddressChecks addressChecks,
            @NonNull final Map<Address, HederaSystemContract> systemContracts) {
        super(evm, precompiles);
        this.featureFlags = Objects.requireNonNull(featureFlags);
        this.precompiles = Objects.requireNonNull(precompiles);
        this.addressChecks = Objects.requireNonNull(addressChecks);
        this.systemContracts = Objects.requireNonNull(systemContracts);
    }

    /**
     * Starts the execution of a message call based on the contract address of the given frame,
     * or halts the frame with an appropriate reason if this cannot be done.
     *
     * <p>This contract address may reference,
     * <ol>
     *     <li>A Hedera system contract.</li>
     *     <li>A native EVM precompile.</li>
     *     <li>A Hedera system account (up to {@code 0.0.750}).</li>
     *     <li>A valid lazy-creation target address.</li>
     *     <li>An existing contract.</li>
     *     <li>An existing account.</li>
     * </ol>
     *
     * @param frame the frame to start
     * @param tracer the operation tracer
     */
    @Override
    public void start(@NonNull final MessageFrame frame, @NonNull final OperationTracer tracer) {
        final var codeAddress = frame.getContractAddress();
        // This must be done first as the system contract address range overlaps with system
        // accounts. Note that unlike EVM precompiles, we do allow sending value "to" Hedera
        // system contracts because they sometimes require fees greater than be reasonably
        // paid using gas; for example, when creating a new token. But the system contract
        // only diverts this value to the network's fee collection accounts, instead of
        // actually receiving it.
        if (systemContracts.containsKey(codeAddress)) {
            doExecuteSystemContract(systemContracts.get(codeAddress), frame, tracer);
            return;
        }

        // Check to see if the code address is a system account and possibly halt
        if (addressChecks.isSystemAccount(codeAddress)) {
            doHaltIfInvalidSystemCall(codeAddress, frame, tracer);
            if (alreadyHalted(frame)) {
                return;
            }
        }

        // Transfer value to the contract if required and possibly halt
        if (transfersValue(frame)) {
            doTransferValueOrHalt(frame, tracer);
            if (alreadyHalted(frame)) {
                return;
            }
        }

        // Handle evm precompiles
        final var evmPrecompile = precompiles.get(codeAddress);
        if (evmPrecompile != null) {
            doExecutePrecompile(evmPrecompile, frame, tracer);
            return;
        }

        // For mono-service fidelity, we need to consider called contracts
        // as a special case eligible for staking rewards
        if (isTopLevelTransaction(frame)) {
            final var maybeCalledContract = proxyUpdaterFor(frame).get(codeAddress);
            if (maybeCalledContract instanceof ProxyEvmAccount a && a.isContract()) {
                recordBuilderFor(frame).trackExplicitRewardSituation(a.hederaId());
            }
        }

        frame.setState(MessageFrame.State.CODE_EXECUTING);
    }

    public boolean isImplicitCreationEnabled(@NonNull Configuration config) {
        return featureFlags.isImplicitCreationEnabled(config);
    }

    private void doExecutePrecompile(
            @NonNull final PrecompiledContract precompile,
            @NonNull final MessageFrame frame,
            @NonNull final OperationTracer tracer) {
        final var gasRequirement = precompile.gasRequirement(frame.getInputData());
        if (frame.getRemainingGas() < gasRequirement) {
            doHalt(frame, INSUFFICIENT_GAS);
        } else {
            frame.decrementRemainingGas(gasRequirement);
            final var result = precompile.computePrecompile(frame.getInputData(), frame);
            tracer.tracePrecompileCall(frame, gasRequirement, result.getOutput());
            if (result.isRefundGas()) {
                frame.incrementRemainingGas(gasRequirement);
            }
            finishPrecompileExecution(frame, result, PRECOMPILE, (ActionSidecarContentTracer) tracer);
        }
    }

    /**
     * This method is necessary as the system contracts do not calculate their gas requirements until after
     * the call to computePrecompile. Thus, the logic for checking for sufficient gas must be done in a different
     * order vs normal precompiles.
     *
     * @param systemContract the system contract to execute
     * @param frame the current frame
     * @param tracer the operation tracer
     */
    private void doExecuteSystemContract(
            @NonNull final HederaSystemContract systemContract,
            @NonNull final MessageFrame frame,
            @NonNull final OperationTracer tracer) {
        final var fullResult = systemContract.computeFully(frame.getInputData(), frame);
        final var gasRequirement = fullResult.gasRequirement();
        tracer.tracePrecompileCall(frame, gasRequirement, fullResult.output());
        if (frame.getRemainingGas() < gasRequirement) {
            doHalt(frame, INSUFFICIENT_GAS);
        } else {
            if (!fullResult.isRefundGas()) {
                frame.decrementRemainingGas(gasRequirement);
            }
            finishPrecompileExecution(frame, fullResult.result(), SYSTEM, (ActionSidecarContentTracer) tracer);
        }
    }

    private void finishPrecompileExecution(
            @NonNull final MessageFrame frame,
            @NonNull final PrecompileContractResult result,
            @NonNull final ContractActionType type,
            @NonNull final ActionSidecarContentTracer tracer) {
        if (result.getState() == MessageFrame.State.REVERT) {
            frame.setRevertReason(result.getOutput());
        } else {
            frame.setOutputData(result.getOutput());
        }
        frame.setState(result.getState());
        frame.setExceptionalHaltReason(result.getHaltReason());
        tracer.tracePrecompileResult(frame, type);
    }

    private void doTransferValueOrHalt(
            @NonNull final MessageFrame frame, @NonNull final OperationTracer operationTracer) {
        final var proxyWorldUpdater = (ProxyWorldUpdater) frame.getWorldUpdater();
        // Try to lazy-create the recipient address if it doesn't exist
        if (!addressChecks.isPresent(frame.getRecipientAddress(), frame)) {
            final var maybeReasonToHalt = proxyWorldUpdater.tryLazyCreation(frame.getRecipientAddress(), frame);
            maybeReasonToHalt.ifPresent(reason -> doHaltOnFailedLazyCreation(frame, reason, operationTracer));
        }
        if (!alreadyHalted(frame)) {
            final var maybeReasonToHalt = proxyWorldUpdater.tryTransfer(
                    frame.getSenderAddress(),
                    frame.getRecipientAddress(),
                    frame.getValue().toLong(),
                    acquiredSenderAuthorizationViaDelegateCall(frame));
            maybeReasonToHalt.ifPresent(reason -> {
                if (reason == INVALID_SIGNATURE) {
                    setPropagatedCallFailure(frame, MISSING_RECEIVER_SIGNATURE);
                }
                doHalt(frame, reason, operationTracer);
            });
        }
    }

    private void doHaltIfInvalidSystemCall(
            @NonNull final Address codeAddress,
            @NonNull final MessageFrame frame,
            @NonNull final OperationTracer operationTracer) {
        if (precompiles.get(codeAddress) == null) {
            doHalt(frame, PRECOMPILE_ERROR, operationTracer);
        } else if (transfersValue(frame)) {
            doHalt(frame, INVALID_FEE_SUBMITTED, operationTracer);
        }
    }

    private void doHaltOnFailedLazyCreation(
            @NonNull final MessageFrame frame,
            @NonNull final ExceptionalHaltReason reason,
            @NonNull final OperationTracer tracer) {
        doHalt(frame, reason, tracer, ForLazyCreation.YES);
    }

    private void doHalt(
            @NonNull final MessageFrame frame,
            @NonNull final ExceptionalHaltReason reason,
            @NonNull final OperationTracer tracer) {
        doHalt(frame, reason, tracer, ForLazyCreation.NO);
    }

    private void doHalt(@NonNull final MessageFrame frame, @NonNull final ExceptionalHaltReason reason) {
        doHalt(frame, reason, null, ForLazyCreation.NO);
    }

    private void doHalt(
            @NonNull final MessageFrame frame,
            @NonNull final ExceptionalHaltReason reason,
            @Nullable final OperationTracer operationTracer,
            @NonNull final ForLazyCreation forLazyCreation) {
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.of(reason));
        if (forLazyCreation == ForLazyCreation.YES) {
            frame.decrementRemainingGas(frame.getRemainingGas());
            if (reason == INSUFFICIENT_CHILD_RECORDS) {
                setPropagatedCallFailure(frame, RESULT_CANNOT_BE_EXTERNALIZED);
            }
        }
        if (operationTracer != null) {
            if (forLazyCreation == ForLazyCreation.YES) {
                operationTracer.traceAccountCreationResult(frame, Optional.of(reason));
            } else {
                operationTracer.tracePostExecution(
                        frame, new Operation.OperationResult(frame.getRemainingGas(), reason));
            }
        }
    }
}
