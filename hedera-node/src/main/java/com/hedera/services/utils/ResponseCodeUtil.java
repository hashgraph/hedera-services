/*
 * Copyright (C) 2021 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.contracts.operation.HederaExceptionalHaltReason;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

public final class ResponseCodeUtil {
    private ResponseCodeUtil() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ResponseCodeEnum getStatus(
            final TransactionProcessingResult result, ResponseCodeEnum success) {
        if (result.isSuccessful()) {
            return success;
        }
        var maybeHaltReason = result.getHaltReason();
        if (maybeHaltReason.isPresent()) {
            var haltReason = maybeHaltReason.get();
            if (HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF == haltReason) {
                return ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
            } else if (HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS == haltReason) {
                return ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
            } else if (HederaExceptionalHaltReason.INVALID_SIGNATURE == haltReason) {
                return ResponseCodeEnum.INVALID_SIGNATURE;
            } else if (ExceptionalHaltReason.INSUFFICIENT_GAS == haltReason) {
                return ResponseCodeEnum.INSUFFICIENT_GAS;
            } else if (ExceptionalHaltReason.ILLEGAL_STATE_CHANGE == haltReason) {
                return ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
            }
        }
        if (result.getRevertReason().isPresent()) {
            return ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
        } else {
            return ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
        }
    }
}
