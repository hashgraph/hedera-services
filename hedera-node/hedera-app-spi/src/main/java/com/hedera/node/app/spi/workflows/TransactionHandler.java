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

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code TransactionHandler} contains all methods for the different stages of a single operation.
 */
public interface TransactionHandler {

    /**
     * Pre-handles a transaction, extracting all non-payer keys, which signatures need to be validated
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws PreCheckException if the transaction is invalid
     */
    void preHandle(@NonNull final PreHandleContext context) throws PreCheckException;

    /**
     * Handles a transaction
     *
     * @param context the {@link HandleContext} which collects all information
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws HandleException if an expected failure occurred
     */
    default void handle(@NonNull final HandleContext context) throws HandleException {
        // TODO: remove default implementation once all handlers were updated
    }
}
