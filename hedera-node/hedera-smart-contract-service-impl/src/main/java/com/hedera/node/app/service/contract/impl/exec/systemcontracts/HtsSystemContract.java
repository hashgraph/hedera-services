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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CallType.UNQUALIFIED_DELEGATE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.callTypeOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asNumberedContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.successResultOf;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.PRECOMPILE_ERROR;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

@Singleton
public class HtsSystemContract extends AbstractFullContract implements HederaSystemContract {
    private static final Logger log = LogManager.getLogger(HtsSystemContract.class);

    public static final String HTS_SYSTEM_CONTRACT_NAME = "HTS";
    public static final String HTS_EVM_ADDRESS = "0x167";
    public static final ContractID HTS_CONTRACT_ID = asNumberedContractId(Address.fromHexString(HTS_EVM_ADDRESS));

    private final HtsCallFactory callFactory;

    @Inject
    public HtsSystemContract(@NonNull final GasCalculator gasCalculator, @NonNull final HtsCallFactory callFactory) {
        super(HTS_SYSTEM_CONTRACT_NAME, gasCalculator);
        this.callFactory = requireNonNull(callFactory);
    }

    @Override
    public FullResult computeFully(@NonNull final Bytes input, @NonNull final MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);
        final var callType = callTypeOf(frame);
        if (callType == UNQUALIFIED_DELEGATE) {
            return haltResult(PRECOMPILE_ERROR, frame.getRemainingGas());
        }
        final HtsCall call;
        final HtsCallAttempt attempt;
        try {
            validateTrue(input.size() >= 4, INVALID_TRANSACTION_BODY);
            attempt = callFactory.createCallAttemptFrom(input, callType, frame);
            call = requireNonNull(attempt.asExecutableCall());
            if (frame.isStatic() && !call.allowsStaticFrame()) {
                // FUTURE - we should really set an explicit halt reason here; instead we just halt the frame
                // without setting a halt reason to simulate mono-service for differential testing
                return haltResult(contractsConfigOf(frame).precompileHtsDefaultGasCost());
            }
        } catch (final Exception e) {
            log.warn("Failed to create HTS call from input {}", input, e);
            return haltResult(INVALID_OPERATION, frame.getRemainingGas());
        }
        return resultOfExecuting(attempt, call, input, frame);
    }

    @SuppressWarnings({"java:S2637", "java:S2259"}) // this function is going to be refactored soon.
    private static FullResult resultOfExecuting(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final HtsCall call,
            @NonNull final Bytes input,
            @NonNull final MessageFrame frame) {
        final HtsCall.PricedResult pricedResult;
        try {
            pricedResult = call.execute(frame);
            final var gasRequirement = pricedResult.fullResult().gasRequirement();
            final var insufficientGas = frame.getRemainingGas() < gasRequirement;
            final var dispatchedRecordBuilder = pricedResult.fullResult().recordBuilder();
            if (dispatchedRecordBuilder != null) {
                if (insufficientGas) {
                    dispatchedRecordBuilder.status(INSUFFICIENT_GAS);
                    dispatchedRecordBuilder.contractCallResult(pricedResult.asResultOfInsufficientGasRemaining(
                            attempt.senderId(), HTS_CONTRACT_ID, tuweniToPbjBytes(input), frame.getRemainingGas()));
                } else {
                    dispatchedRecordBuilder.contractCallResult(pricedResult.asResultOfCall(
                            attempt.senderId(), HTS_CONTRACT_ID, tuweniToPbjBytes(input), frame.getRemainingGas()));
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
                                            .syntheticTransactionForHtsCall(input, HTS_CONTRACT_ID, true));
                } else {
                    externalizeFailure(
                            gasRequirement,
                            input,
                            insufficientGas
                                    ? Bytes.EMPTY
                                    : pricedResult.fullResult().output(),
                            attempt,
                            status,
                            enhancement);
                }
            }
        } catch (final HandleException handleException) {
            return haltHandleException(handleException, frame.getRemainingGas());
        } catch (final Exception internal) {
            log.error("Unhandled failure for input {} to HTS system contract", input, internal);
            return haltResult(PRECOMPILE_ERROR, frame.getRemainingGas());
        }
        return pricedResult.fullResult();
    }

    private static void externalizeFailure(
            final long gasRequirement,
            @NonNull final Bytes input,
            @NonNull final Bytes output,
            @NonNull final HtsCallAttempt attempt,
            @NonNull final ResponseCodeEnum status,
            @NonNull final HederaWorldUpdater.Enhancement enhancement) {
        enhancement
                .systemOperations()
                .externalizeResult(
                        contractFunctionResultFailedFor(
                                attempt.senderId(), output, gasRequirement, status.toString(), HTS_CONTRACT_ID),
                        status,
                        enhancement.systemOperations().syntheticTransactionForHtsCall(input, HTS_CONTRACT_ID, true));
    }

    // potentially other cases could be handled here if necessary
    private static FullResult haltHandleException(final HandleException handleException, long remainingGas) {
        if (handleException.getStatus().equals(MAX_CHILD_RECORDS_EXCEEDED)) {
            return haltResult(CustomExceptionalHaltReason.INSUFFICIENT_CHILD_RECORDS, remainingGas);
        }
        throw handleException;
    }
}
