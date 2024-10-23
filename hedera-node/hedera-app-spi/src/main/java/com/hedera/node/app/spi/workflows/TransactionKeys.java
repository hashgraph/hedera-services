/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Contains all keys and hollow accounts (required and optional) of a transaction.
 */
public interface TransactionKeys {
    /**
     * Getter for the payer key
     *
     * @return the payer key
     */
    @NonNull
    Key payerKey();

    /**
     * Returns an immutable copy of the set of required non-payer keys.
     *
     * @return the {@link Set} with the required non-payer keys
     */
    @NonNull
    Set<Key> requiredNonPayerKeys();

    /**
     * Gets an immutable copy of the set of required hollow accounts that need signatures.
     *
     * @return the {@link Set} of hollow accounts required
     */
    @NonNull
    Set<Account> requiredHollowAccounts();

    /**
     * Returns an immutable copy of the set of optional non-payer keys.
     *
     * @return the {@link Set} with the optional non-payer keys.  This set may be empty.
     */
    @NonNull
    Set<Key> optionalNonPayerKeys();

    /**
     * Gets an immutable copy of the set of optional hollow accounts that may need signatures.
     *
     * @return the {@link Set} of hollow accounts possibly required
     */
    @NonNull
    Set<Account> optionalHollowAccounts();
}
