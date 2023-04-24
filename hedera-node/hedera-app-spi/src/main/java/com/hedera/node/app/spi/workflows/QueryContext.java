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

package com.hedera.node.app.spi.workflows;

import com.hedera.hapi.node.transaction.Query;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Context of a single query. Contains all query specific information.
 */
public interface QueryContext {

    /**
     * Returns the {@link Query} that is currently being processed.
     *
     * @return the {@link Query} that is currently being processed
     */
    @NonNull
    Query query();

    /**
     * Create a new store given the store's interface. This gives read-only access to the store.
     *
     * @param storeInterface The store interface to find and create a store for
     * @return An implementation of store interface provided, or null if the store
     * @param <C> Interface class for a Store
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code clazz} is {@code null}
     */
    @NonNull
    <C> C createStore(@NonNull Class<C> storeInterface);
}
