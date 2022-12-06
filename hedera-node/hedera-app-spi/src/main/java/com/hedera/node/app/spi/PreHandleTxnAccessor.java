/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

import com.hederahashgraph.api.proto.java.HederaFunctionality;

/** Returns needed pre-transaction handlers for different modules. */
public interface PreHandleTxnAccessor {
    /**
     * Gets Account lookup to fetch any account
     *
     * @return account lookup
     */
    AccountKeyLookup getAccountKeyLookup();
    /**
     * Gets pre-transaction handler for CryptoService
     *
     * @return pre-transaction handler for CryptoService
     */
    PreTransactionHandler getCryptoPreTransactionHandler();

    /**
     * Gets pre-transaction handler for ScheduleService
     *
     * @return pre-transaction handler for ScheduleService
     */
    PreTransactionHandler getSchedulePreTransactionHandler();

    /**
     * Gets the pre-transaction handler based on the type of transaction
     *
     * @param function type of transaction
     * @return pre-transaction handler based on the type of transaction
     */
    default PreTransactionHandler getPreTxnHandler(final HederaFunctionality function) {
        if (function == CryptoTransfer
                || function == CryptoCreate
                || function == CryptoDelete
                || function == CryptoDeleteAllowance
                || function == CryptoApproveAllowance
                || function == CryptoUpdate) {
            return getCryptoPreTransactionHandler();
        } else if (function == ScheduleCreate
                || function == ScheduleDelete
                || function == ScheduleSign) {
            return getSchedulePreTransactionHandler();
        }
        throw new UnsupportedOperationException(
                "Pre-transaction handler for given function "
                        + function
                        + " is not supported yet !");
    }
}
