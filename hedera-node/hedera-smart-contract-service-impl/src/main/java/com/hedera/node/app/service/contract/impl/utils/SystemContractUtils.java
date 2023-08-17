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

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;

/**
 * Utilities for system contracts.
 */
public final class SystemContractUtils {

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
            final long gasUsed, final Bytes result, final ContractID contractID) {
        return ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .contractCallResult(tuweniToPbjBytes(result))
                .contractID(contractID)
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
}
