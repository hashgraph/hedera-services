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

package com.hedera.node.app.service.contract.impl.utils;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static java.util.Objects.requireNonNull;

import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Utilities for system contracts.
 */
public final class SystemContractUtils {

    /*
    The contractFunctionResultSuccessFor is called from Prgn contract and we are setting the HTS address - this is done
    to mirror the current mono behaviour(PrngSystemPrecompiledContract.computePrecompile > createSuccessfulChildRecord >
    addContractCallResultToRecord > PrecompileUtils.addContractCallResultToRecord). This will be
    fixed after the differential testing in this story https://github.com/hashgraph/hedera-services/issues/10552
     */
    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final ContractID HTS_PRECOMPILE_MIRROR_ID = contractIdFromEvmAddress(
            Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());

    private SystemContractUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public enum ResultStatus {
        IS_SUCCESS,
        IS_ERROR
    }

    /**
     * Create a successful contract function result.
     *
     * @param gasUsed Report the gas used.
     * @param result The result of the contract call.
     * @param gas The remaining gas.
     * @param inputData The input data.
     * @param senderId The sender id.
     * @return The created contract function result for a successful call.
     */
    @NonNull
    public static ContractFunctionResult successResultOfZeroValueTraceable(
            final long gasUsed,
            final Bytes result,
            final long gas,
            @NonNull final Bytes inputData,
            @NonNull final AccountID senderId) {
        return ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .gas(gas)
                .contractCallResult(tuweniToPbjBytes(result))
                .functionParameters(tuweniToPbjBytes(inputData))
                .senderId(senderId)
                .contractID(HTS_PRECOMPILE_MIRROR_ID)
                .build();
    }

    /**
     * Create a successful contract function result for the given frame with
     * the known sender and result.
     *
     * @param senderId the sender id
     * @param fullResult the full result
     * @param frame the frame
     * @param includeTraceabilityFields whether to include traceability fields
     * @return the created contract function result for a successful call
     */
    public static @NonNull ContractFunctionResult successResultOf(
            @NonNull final AccountID senderId,
            @NonNull final FullResult fullResult,
            @NonNull final MessageFrame frame,
            final boolean includeTraceabilityFields) {
        requireNonNull(senderId);
        requireNonNull(fullResult);
        requireNonNull(frame);
        final var builder = ContractFunctionResult.newBuilder()
                .gasUsed(fullResult.gasRequirement())
                .contractCallResult(tuweniToPbjBytes(fullResult.result().getOutput()))
                .senderId(senderId)
                .contractID(HTS_PRECOMPILE_MIRROR_ID);
        if (includeTraceabilityFields) {
            builder.gas(frame.getRemainingGas())
                    .amount(frame.getValue().toLong())
                    .functionParameters(tuweniToPbjBytes(frame.getInputData()));
        }
        return builder.build();
    }

    /**
     * Create an error contract function result.
     *
     * @param gasUsed Report the gas used.
     * @param errorMsg The error message to report back to the caller.
     * @param contractID The contract ID.
     * @return The created contract function result when for a failed call.
     */
    @NonNull
    public static ContractFunctionResult contractFunctionResultFailedFor(
            final long gasUsed, final String errorMsg, final ContractID contractID) {
        return ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .errorMessage(errorMsg)
                .contractID(contractID)
                .build();
    }

    /**
     * Create an error contract function result.
     *
     * @param gasUsed Report the gas used.
     * @param errorMsg The error message to report back to the caller.
     * @param contractID The contract ID.
     * @param contractCallResult Bytes representation of the contract call result error
     * @return The created contract function result when for a failed call.
     */
    @NonNull
    public static ContractFunctionResult contractFunctionResultFailedForProto(
            final long gasUsed,
            final String errorMsg,
            final ContractID contractID,
            final com.hedera.pbj.runtime.io.buffer.Bytes contractCallResult) {
        return ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .contractID(contractID)
                .errorMessage(errorMsg)
                .contractCallResult(contractCallResult)
                .build();
    }

    private static ContractID contractIdFromEvmAddress(final byte[] bytes) {
        return ContractID.newBuilder()
                .contractNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }
}
