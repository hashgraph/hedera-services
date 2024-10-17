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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.common;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CallType.UNQUALIFIED_DELEGATE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.successResultOf;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.PRECOMPILE_ERROR;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.AbstractFullContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * Abstract class for native system contracts.
 * Descendents are {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract} and
 * {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.HasSystemContract}.
 */
@Singleton
public abstract class AbstractNativeSystemContract extends AbstractFullContract implements HederaSystemContract {
    private static final Logger log = LogManager.getLogger(AbstractNativeSystemContract.class);
    /**
     * Function selector byte length
     */
    public static final int FUNCTION_SELECTOR_LENGTH = 4;

    private final CallFactory callFactory;
    private final ContractID contractID;

    protected AbstractNativeSystemContract(
            @NonNull String name,
            @NonNull CallFactory callFactory,
            @NonNull ContractID contractID,
            @NonNull GasCalculator gasCalculator) {
        super(name, gasCalculator);
        this.callFactory = requireNonNull(callFactory);
        this.contractID = requireNonNull(contractID);
    }

    @Override
    public FullResult computeFully(@NonNull final Bytes input, @NonNull final MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);
        final var callType = callTypeOf(frame);
        if (callType == UNQUALIFIED_DELEGATE) {
            return haltResult(PRECOMPILE_ERROR, frame.getRemainingGas());
        }
        final Call call;
        final AbstractCallAttempt attempt;
        try {
            validateTrue(input.size() >= FUNCTION_SELECTOR_LENGTH, INVALID_TRANSACTION_BODY);
            attempt = callFactory.createCallAttemptFrom(input, callType, frame);
            call = requireNonNull(attempt.asExecutableCall());
            if (frame.isStatic() && !call.allowsStaticFrame()) {
                // FUTURE - we should really set an explicit halt reason here; instead we just halt the frame
                // without setting a halt reason to simulate mono-service for differential testing
                return haltResult(contractsConfigOf(frame).precompileHtsDefaultGasCost());
            }
        } catch (final Exception ignore) {
            // Input that cannot be translated to an executable call, for any
            // reason, halts the frame and consumes all remaining gas
            return haltResult(INVALID_OPERATION, frame.getRemainingGas());
        }
        return resultOfExecuting(attempt, call, input, frame, this.contractID);
    }

    @SuppressWarnings({"java:S2637", "java:S2259"}) // this function is going to be refactored soon.
    private static FullResult resultOfExecuting(
            @NonNull final AbstractCallAttempt attempt,
            @NonNull final Call call,
            @NonNull final Bytes input,
            @NonNull final MessageFrame frame,
            @NonNull final ContractID contractID) {
        final Call.PricedResult pricedResult;
        try {
            pricedResult = call.execute(frame);
            final var gasRequirement = pricedResult.fullResult().gasRequirement();
            final var insufficientGas = frame.getRemainingGas() < gasRequirement;
            final var dispatchedRecordBuilder = pricedResult.fullResult().recordBuilder();
            if (dispatchedRecordBuilder != null) {
                if (insufficientGas) {
                    dispatchedRecordBuilder.status(INSUFFICIENT_GAS);
                    dispatchedRecordBuilder.contractCallResult(pricedResult.asResultOfInsufficientGasRemaining(
                            attempt.senderId(), contractID, tuweniToPbjBytes(input), frame.getRemainingGas()));
                } else {
                    dispatchedRecordBuilder.contractCallResult(pricedResult.asResultOfCall(
                            attempt.senderId(), contractID, tuweniToPbjBytes(input), frame.getRemainingGas()));
                }
            } else if (pricedResult.isViewCall()) {
                final var proxyWorldUpdater = proxyUpdaterFor(frame);
                final var enhancement = proxyWorldUpdater.enhancement();
                // Insufficient gas preempts any other response code
                final var status = insufficientGas ? INSUFFICIENT_GAS : pricedResult.responseCode();
                if (status == SUCCESS) {
                    enhancement
                            .systemOperations()
                            .externalizeResult(
                                    successResultOf(
                                            attempt.senderId(),
                                            pricedResult.fullResult(),
                                            frame,
                                            !call.allowsStaticFrame()),
                                    pricedResult.responseCode(),
                                    enhancement
                                            .systemOperations()
                                            .syntheticTransactionForNativeCall(input, contractID, true));
                } else {
                    externalizeFailure(
                            gasRequirement,
                            input,
                            insufficientGas
                                    ? Bytes.EMPTY
                                    : pricedResult.fullResult().output(),
                            attempt,
                            status,
                            enhancement,
                            contractID);
                }
            }
        } catch (final HandleException handleException) {
            return haltHandleException(handleException, frame.getRemainingGas());
        } catch (final Exception internal) {
            log.error("Unhandled failure for input {} to native system contract", input, internal);
            return haltResult(PRECOMPILE_ERROR, frame.getRemainingGas());
        }
        return pricedResult.fullResult();
    }

    private static void externalizeFailure(
            final long gasRequirement,
            @NonNull final Bytes input,
            @NonNull final Bytes output,
            @NonNull final AbstractCallAttempt attempt,
            @NonNull final ResponseCodeEnum status,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final ContractID contractID) {
        enhancement
                .systemOperations()
                .externalizeResult(
                        contractFunctionResultFailedFor(
                                attempt.senderId(), output, gasRequirement, status.toString(), contractID),
                        status,
                        enhancement.systemOperations().syntheticTransactionForNativeCall(input, contractID, true));
    }

    // potentially other cases could be handled here if necessary
    private static FullResult haltHandleException(
            @NonNull final HandleException handleException, final long remainingGas) {
        if (handleException.getStatus().equals(MAX_CHILD_RECORDS_EXCEEDED)) {
            return haltResult(CustomExceptionalHaltReason.INSUFFICIENT_CHILD_RECORDS, remainingGas);
        }
        throw handleException;
    }

    //
    protected abstract FrameUtils.CallType callTypeOf(@NonNull final MessageFrame frame);
}
