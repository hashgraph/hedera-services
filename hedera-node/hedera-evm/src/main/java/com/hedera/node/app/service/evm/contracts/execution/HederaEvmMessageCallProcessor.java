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

package com.hedera.node.app.service.evm.contracts.execution;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/** Overrides Besu precompiler handling, so we can break model layers in Precompile execution */
public class HederaEvmMessageCallProcessor extends MessageCallProcessor {
    private static final Optional<ExceptionalHaltReason> ILLEGAL_STATE_CHANGE =
            Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);

    protected final Map<Address, PrecompiledContract> hederaPrecompiles;
    protected long gasRequirement;
    protected Bytes output;
    private final Predicate<Address> isNativePrecompile;
    private final Predicate<Address> precompileDetector;

    public HederaEvmMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList,
            final Predicate<Address> precompileDetector) {
        super(evm, precompiles);
        hederaPrecompiles = new HashMap<>();
        hederaPrecompileList.forEach((k, v) -> hederaPrecompiles.put(Address.fromHexString(k), v));
        this.isNativePrecompile = addr -> precompiles.get(addr) != null;
        this.precompileDetector = precompileDetector;
    }

    @Override
    public void start(final MessageFrame frame, final OperationTracer operationTracer) {
        final var hederaPrecompile = hederaPrecompiles.get(frame.getContractAddress());
        if (hederaPrecompile != null) {
            executeHederaPrecompile(hederaPrecompile, frame, operationTracer);
        } else {
            // Non-system-precompile execution flow
            final var frameHasValue = frame.getValue().greaterThan(Wei.ZERO);
            if (precompileDetector.test(frame.getContractAddress())) {
                // we have a non-system-contract call to a system address
                if (!isNativePrecompile.test(frame.getContractAddress())) {
                    // a call to a system address, on which a native precompile does not exist, should always fail
                    frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
                    frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
                    operationTracer.tracePostExecution(
                            frame,
                            new Operation.OperationResult(
                                    frame.getRemainingGas(), ExceptionalHaltReason.PRECOMPILE_ERROR));
                    return;
                } else if (frameHasValue) {
                    // cannot send value to native precompile calls, since there are collisions with system account
                    // and value will be transferred to the system account, which is undesired
                    frame.setExceptionalHaltReason(Optional.of(HederaExceptionalHaltReason.INVALID_FEE_SUBMITTED));
                    frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
                    operationTracer.tracePostExecution(
                            frame,
                            new Operation.OperationResult(
                                    frame.getRemainingGas(), HederaExceptionalHaltReason.INVALID_FEE_SUBMITTED));
                    return;
                }
            } else if (frameHasValue) {
                final var updater = (AbstractLedgerEvmWorldUpdater) frame.getWorldUpdater();
                if (updater.isTokenAddress(frame.getRecipientAddress())) {
                    frame.setExceptionalHaltReason(ILLEGAL_STATE_CHANGE);
                    frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
                    operationTracer.tracePostExecution(
                            frame,
                            new Operation.OperationResult(
                                    frame.getRemainingGas(), ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
                    return;
                } else if (updater.get(frame.getRecipientAddress()) == null) {
                    executeLazyCreate(frame, operationTracer);
                    if (frame.getState() == EXCEPTIONAL_HALT) {
                        return;
                    }
                }
            }

            super.start(frame, operationTracer);
        }
    }

    protected void executeHederaPrecompile(
            final PrecompiledContract contract, final MessageFrame frame, final OperationTracer operationTracer) {
        if (contract instanceof EvmHTSPrecompiledContract htsPrecompile) {
            var updater = (AbstractLedgerEvmWorldUpdater) frame.getWorldUpdater();
            final var costedResult = htsPrecompile.computeCosted(
                    frame.getInputData(),
                    frame,
                    (now, minimumTinybarCost) -> minimumTinybarCost,
                    updater.tokenAccessor());
            output = costedResult.getValue();
            gasRequirement = costedResult.getKey();
        }
        if (!"HTS".equals(contract.getName()) && !"EvmHTS".equals(contract.getName())) {
            output = contract.computePrecompile(frame.getInputData(), frame).getOutput();
            gasRequirement = contract.gasRequirement(frame.getInputData());
        }

        operationTracer.tracePrecompileCall(frame, gasRequirement, output);
        if (frame.getState() == REVERT) {
            return;
        }
        if (frame.getRemainingGas() < gasRequirement) {
            frame.decrementRemainingGas(frame.getRemainingGas());
            frame.setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
            frame.setState(EXCEPTIONAL_HALT);
        } else if (output != null) {
            frame.decrementRemainingGas(gasRequirement);
            frame.setOutputData(output);
            frame.setState(COMPLETED_SUCCESS);
        } else {
            frame.setState(EXCEPTIONAL_HALT);
        }
    }

    protected void executeLazyCreate(final MessageFrame frame, final OperationTracer operationTracer) {
        // no-op
    }
}
