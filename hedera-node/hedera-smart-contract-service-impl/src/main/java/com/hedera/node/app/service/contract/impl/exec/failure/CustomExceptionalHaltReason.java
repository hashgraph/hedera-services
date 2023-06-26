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

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

/**
 * Some {@link ExceptionalHaltReason}s that are not part of the Besu core.
 */
public enum CustomExceptionalHaltReason implements ExceptionalHaltReason {
    /**
     * An EVM operation referenced an account that does not exist.
     */
    MISSING_ADDRESS("Invalid account reference"),
    TOKEN_TREASURY_SELFDESTRUCT("Token treasury cannot be deleted"),
    TOKEN_HOLDER_SELFDESTRUCT("Accounts still holding tokens cannot be deleted"),
    SELFDESTRUCT_TO_SELF("Selfdestruct must give a different beneficiary"),
    INVALID_RECEIVER_SIGNATURE("Receiver signature required but not provided"),
    TOO_MANY_CHILD_RECORDS("Too many child records for available slots"),
    ACCOUNTS_LIMIT_REACHED("Accounts limit reached"),
    INVALID_VALUE_TRANSFER("Value transfer not allowed to system or expired accounts");

    private final String description;

    CustomExceptionalHaltReason(final String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
