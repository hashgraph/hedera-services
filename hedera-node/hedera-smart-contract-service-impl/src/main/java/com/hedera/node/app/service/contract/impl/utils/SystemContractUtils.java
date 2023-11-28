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

package com.hedera.node.app.service.contract.impl.utils;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;

import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmAccount;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
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
    TODO: here is how contractId is set in mono:
    PrngSystemPrecompiledContract.computePrecompile > createSuccessfulChildRecord >
    addContractCallResultToRecord > PrecompileUtils.addContractCallResultToRecord.
    For the contractId in EvmFnResult is passed HTS_PRECOMPILE_MIRROR_ENTITY_ID - which is using the
    HTC contract address(0x167). This seems like a bug to me.
    Is this how it's suppose to work? Should I fix it in mono or should I just mimic it here and
    create a story to later fix it?
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
     * @param gasUsed       Report the gas used.
     * @param result        The result of the contract call.
     * @param contractID    The contract ID.
     * @return              The created contract function result for a successful call.
     */
    @NonNull
    public static ContractFunctionResult contractFunctionResultSuccessFor(
            final long gasUsed, final Bytes result, final ContractID contractID, MessageFrame frame) {
        var updater = (ProxyWorldUpdater) frame.getWorldUpdater();
        final var senderId = ((ProxyEvmAccount) updater.getAccount(frame.getSenderAddress())).hederaId();

        return ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .contractCallResult(tuweniToPbjBytes(result))
                .contractID(contractID)
                .functionParameters(tuweniToPbjBytes(frame.getInputData()))
                .gas(369823) // TODO: remove - currently the gas calculation is not working
                .senderId(senderId)
                .contractID(HTS_PRECOMPILE_MIRROR_ID)
                .build();
    }

    /**
     * Create an error contract function result.
     * @param gasUsed       Report the gas used.
     * @param errorMsg      The error message to report back to the caller.
     * @param contractID    The contract ID.
     * @return              The created contract function result when for a failed call.
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

    private static ContractID contractIdFromEvmAddress(final byte[] bytes) {
        return ContractID.newBuilder()
                .contractNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }
}
