/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.records.RecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code TransactionHandler} contains all methods for the different stages of a single operation.
 */
public interface TransactionHandler {
    /**
     * Validates fields on the transaction body that don't need to involve looking up state or the Configurations.
     * This check will be done only in pre-handle and will NOT be repeated in handle.
     *
     * @param txn the transaction body to validate
     * @throws PreCheckException if any check failed
     */
    void pureChecks(TransactionBody txn);

    /**
     * Pre-handles a transaction, extracting all non-payer keys, which signatures need to be validated
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if the transaction is invalid
     */
    void preHandle(@NonNull final PreHandleContext context) throws PreCheckException;

    /**
     * Returns an instance of the transaction-specific {@link RecordBuilder}.
     *
     * @return an instance of the transaction-specific {@link RecordBuilder}
     * @param <R> the type of the transaction-specific {@link RecordBuilder}
     */
    default <R extends RecordBuilder<R>> R newRecordBuilder() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
