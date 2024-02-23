/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.lifecycle;

/**
 * Type of a transaction
 */
public enum TransactionType {
    /**
     * When a transaction is CreateAccount or CreateAccountFCQ
     */
    Create,
    /**
     * When a transaction is CreateAccount or CreateAccountFCQ, but the account
     * already exists in state.
     */
    CreateExistingAccount,
    /**
     * When a transaction is UpdateAccount or UpdateAccountFCQ
     */
    Update,
    /**
     * When a transaction is UpdateAccount or UpdateAccountFCQ, and
     * the account does not belong to state.
     */
    UpdateNotExistentAccount,
    /**
     * When a transaction is Append
     */
    Append,
    /**
     * When a transaction is DeleteAccount or DeleteAccountFCQ
     */
    Delete,
    /**
     * When a transaction is DeleteAccount or DeleteAccountFCQ, and
     * the account does not belong to state.
     */
    DeleteNotExistentAccount,
    /**
     * When a transaction is TransferBalance, or TransferBalanceFCQ
     */
    Transfer,
    /**
     * When an entity is removed because it is expired
     */
    Expire,
    /**
     * When an entity is added at restart or reconnect, not by a transaction
     */
    Rebuild,
    /**
     * When a transaction is creating and associating a token to an account
     */
    MintToken,
    /**
     * When a transaction is transferring an NFT
     */
    TransferToken,
    /**
     * When a transaction is burning an NFT
     */
    BurnToken
}
