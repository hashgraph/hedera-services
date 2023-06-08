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

package com.hedera.node.app.spi.meta.bni;

/**
 * Provides distributed transaction management for smart contract execution.
 *
 * <p>That is, allows the {@code ContractService} to make atomic changes within
 * an EVM message frame across multiple parts of system state, even though the
 * {@code ContractService} cannot directly mutate most of that state itself.
 *
 * <p>These parts of system state include:
 * <ol>
 *  <li>Contract storage and bytecode.</li>
 *  <li>Accounts and contracts (balances, expiration and deletion metadata, keys, and so on).</li>
 *  <li>Tokens, token balances, and token associations.</li>
 *  <li>Records.</li>
 *  <li>Entity ids.</li>
 * </ol>
 *
 * <p>Only the first part of state is under direct control of the {@code ContractService}.
 */
public interface ContractTransactionManager {
    /**
     * Returns a new {@link Scope} that is a child of the current {@link Scope}. Changes
     * made in the child will not be visible to the parent until the child calls {@link Scope#commit()}.
     *
     * @return a new {@link Scope}
     */
    Scope begin();
}
