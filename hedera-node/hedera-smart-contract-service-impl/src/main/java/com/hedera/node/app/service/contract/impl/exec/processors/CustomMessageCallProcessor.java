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

package com.hedera.node.app.service.contract.impl.exec.processors;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_VALUE_TRANSFER;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.alreadyHalted;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.isDelegateCall;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.transfersValue;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.PRECOMPILE_ERROR;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
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
    private final Map<Address, PrecompiledContract> hederaPrecompiles;

    public CustomMessageCallProcessor(
            @NonNull final EVM evm,
            @NonNull final FeatureFlags featureFlags,
            @NonNull final PrecompileContractRegistry precompiles,
            @NonNull final AddressChecks addressChecks,
            @NonNull final Map<Address, PrecompiledContract> hederaPrecompiles) {
        super(evm, precompiles);
        this.featureFlags = Objects.requireNonNull(featureFlags);
        this.precompiles = Objects.requireNonNull(precompiles);
        this.addressChecks = Objects.requireNonNull(addressChecks);
        this.hederaPrecompiles = Objects.requireNonNull(hederaPrecompiles);
    }

    /**
     * Starts the execution of a message call based on the contract address of the given frame,
     * or halts the frame with an appropriate reason if this cannot be done.
     *
     * <p>This contract address may reference,
     * <ol>
     *     <li>A Hedera precompile.</li>
     *     <li>A native EVM precompile.</li>
     *     <li>A Hedera system account (up to {@code 0.0.750}).</li>
     *     <li>A valid lazy-creation target address.</li>
     *     <li>An existing contract.</li>
     *     <li>An existing account.</li>
     * </ol>
     *
     * @param frame  the frame to start
     * @param tracer the operation tracer
     */
    @Override
    public void start(@NonNull final MessageFrame frame, @NonNull final OperationTracer tracer) {
        final var codeAddress = frame.getContractAddress();
        if (hederaPrecompiles.containsKey(codeAddress)) {
            throw new UnsupportedOperationException("Hedera precompiles");
        } else if (addressChecks.isSystemAccount(codeAddress)) {
            doHaltIfInvalidSystemCall(codeAddress, frame, tracer);
        } else if (transfersValue(frame)) {
            doTransferValueOrHalt(frame, tracer);
        }
        if (!alreadyHalted(frame)) {
            final var evmPrecompile = precompiles.get(codeAddress);
            if (evmPrecompile != null) {
                doExecute(evmPrecompile, frame, tracer);
            } else {
                frame.setState(MessageFrame.State.CODE_EXECUTING);
            }
        }
    }

    public boolean isImplicitCreationEnabled(@NonNull Configuration config) {
        return featureFlags.isImplicitCreationEnabled(config);
    }

    private void doExecute(
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
            if (frame.getState() == MessageFrame.State.REVERT) {
                frame.setRevertReason(result.getOutput());
            } else {
                frame.setOutputData(result.getOutput());
            }
            frame.setState(result.getState());
            frame.setExceptionalHaltReason(result.getHaltReason());
        }
    }

    private void doTransferValueOrHalt(
            @NonNull final MessageFrame frame, @NonNull final OperationTracer operationTracer) {
        final var proxyWorldUpdater = (ProxyWorldUpdater) frame.getWorldUpdater();
        // Lazy-create the recipient address if it doesn't exist
        if (!addressChecks.isPresent(frame.getRecipientAddress(), frame)) {
            final var maybeReasonToHalt = proxyWorldUpdater.tryLazyCreation(frame.getRecipientAddress(), frame);
            maybeReasonToHalt.ifPresent(reason -> doHalt(frame, reason, operationTracer));
        }
        if (!alreadyHalted(frame)) {
            final var maybeReasonToHalt = proxyWorldUpdater.tryTransferFromContract(
                    frame.getSenderAddress(),
                    frame.getRecipientAddress(),
                    frame.getValue().toLong(),
                    isDelegateCall(frame));
            maybeReasonToHalt.ifPresent(reason -> doHalt(frame, reason, operationTracer));
        }
    }

    private void doHaltIfInvalidSystemCall(
            @NonNull final Address codeAddress,
            @NonNull final MessageFrame frame,
            @NonNull final OperationTracer operationTracer) {
        if (precompiles.get(codeAddress) == null) {
            doHalt(frame, PRECOMPILE_ERROR, operationTracer);
        } else if (transfersValue(frame)) {
            doHalt(frame, INVALID_VALUE_TRANSFER, operationTracer);
        }
    }

    private void doHalt(@NonNull final MessageFrame frame, @NonNull final ExceptionalHaltReason reason) {
        doHalt(frame, reason, null);
    }

    private void doHalt(
            @NonNull final MessageFrame frame,
            @NonNull final ExceptionalHaltReason reason,
            @Nullable final OperationTracer operationTracer) {
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.of(reason));
        if (operationTracer != null) {
            operationTracer.tracePostExecution(frame, new Operation.OperationResult(frame.getRemainingGas(), reason));
        }
    }
}
