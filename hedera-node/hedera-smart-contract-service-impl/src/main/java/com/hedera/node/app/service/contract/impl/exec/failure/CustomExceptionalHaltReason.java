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

package com.hedera.node.app.service.contract.impl.exec.failure;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

public enum CustomExceptionalHaltReason implements ExceptionalHaltReason {
    INVALID_SOLIDITY_ADDRESS("Invalid account reference"),
    SELF_DESTRUCT_TO_SELF("Self destruct to the same address"),
    CONTRACT_IS_TREASURY("Token treasuries cannot be deleted"),
    INVALID_SIGNATURE("Invalid signature"),
    TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES("Accounts with positive fungible token balances cannot be deleted"),
    CONTRACT_STILL_OWNS_NFTS("Accounts who own nfts cannot be deleted"),
    ERROR_DECODING_PRECOMPILE_INPUT("Error when decoding precompile input."),
    FAILURE_DURING_LAZY_ACCOUNT_CREATION("Failure during lazy account creation"),
    NOT_SUPPORTED("Not supported."),
    CONTRACT_ENTITY_LIMIT_REACHED("Contract entity limit reached."),
    INVALID_FEE_SUBMITTED("Invalid fee submitted for an EVM call.");

    private final String description;

    CustomExceptionalHaltReason(@NonNull final String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Returns the "preferred" status for the given halt reason.
     *
     * @param reason the halt reason
     * @return the status
     */
    public static ResponseCodeEnum statusFor(@NonNull final ExceptionalHaltReason reason) {
        requireNonNull(reason);
        if (reason == SELF_DESTRUCT_TO_SELF) {
            return ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
        } else if (reason == INVALID_SOLIDITY_ADDRESS) {
            return ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
        } else if (reason == INVALID_SIGNATURE) {
            return ResponseCodeEnum.INVALID_SIGNATURE;
        } else if (reason == CONTRACT_ENTITY_LIMIT_REACHED) {
            return ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
        } else if (reason == ExceptionalHaltReason.INSUFFICIENT_GAS) {
            return ResponseCodeEnum.INSUFFICIENT_GAS;
        } else if (reason == ExceptionalHaltReason.ILLEGAL_STATE_CHANGE) {
            return ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
        } else {
            return ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
        }
    }

    public static String errorMessageFor(@NonNull final ExceptionalHaltReason reason) {
        requireNonNull(reason);
        return reason.toString();
    }
}
