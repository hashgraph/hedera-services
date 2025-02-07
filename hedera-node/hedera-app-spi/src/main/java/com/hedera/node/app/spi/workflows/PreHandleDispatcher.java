/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
 * A {@code PreHandleDispatcher} takes a validated transaction and dispatches it to the correct handler
 *
 * @deprecated This interface is not needed anymore
 */
@Deprecated(forRemoval = true)
@FunctionalInterface
public interface PreHandleDispatcher {
    /**
     * Dispatch a request. It is forwarded to the correct handler, which takes care of the specific functionality
     *
     * @param context the {@link PreHandleContext} for the dispatched transaction
     * @throws NullPointerException if {@code transactionBody} is {@code null}
     * @throws WorkflowException if the transaction within the context is invalid
     */
    void dispatch(@NonNull PreHandleContext context);
}
