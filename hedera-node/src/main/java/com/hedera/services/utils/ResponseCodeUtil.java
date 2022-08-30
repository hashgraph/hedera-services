/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static java.util.stream.Collectors.toMap;

import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.contracts.operation.HederaExceptionalHaltReason;
import com.hedera.services.exceptions.ResourceLimitException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Map;
import java.util.stream.Stream;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

public final class ResponseCodeUtil {
    static final Map<BytesKey, ResponseCodeEnum> RESOURCE_EXHAUSTION_REVERSIONS =
            Stream.of(
                            MAX_CHILD_RECORDS_EXCEEDED,
                            MAX_CONTRACT_STORAGE_EXCEEDED,
                            MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED,
                            MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED,
                            INSUFFICIENT_BALANCES_FOR_STORAGE_RENT)
                    .collect(
                            toMap(
                                    status ->
                                            new BytesKey(
                                                    new ResourceLimitException(status)
                                                            .messageBytes()
                                                            .toArrayUnsafe()),
                                    status -> status));

    private ResponseCodeUtil() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ResponseCodeEnum getStatusOrDefault(
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
        return result.getRevertReason()
                .map(
                        status ->
                                RESOURCE_EXHAUSTION_REVERSIONS.getOrDefault(
                                        new BytesKey(
                                                result.getRevertReason().get().toArrayUnsafe()),
                                        CONTRACT_REVERT_EXECUTED))
                .orElse(CONTRACT_EXECUTION_EXCEPTION);
    }
}
