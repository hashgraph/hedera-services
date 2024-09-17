/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

/**
 * Enumerates the types of transactions that may be handled by the workflow. Almost all transactions are unexceptional,
 * but the first transactions at genesis and after an upgrade require special handling since the network needs to
 * prepare for all following transactions at these boundary conditions.
 */
public enum TransactionType {
    /**
     * The first transaction at network genesis.
     */
    GENESIS_TRANSACTION,
    /**
     * The first transaction after an upgrade.
     */
    POST_UPGRADE_TRANSACTION,
    /**
     * All other transactions.
     */
    ORDINARY_TRANSACTION,
}
