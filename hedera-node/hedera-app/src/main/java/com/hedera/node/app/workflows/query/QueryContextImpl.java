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

package com.hedera.node.app.workflows.query;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Simple implementation of {@link QueryContext}.
 */
public class QueryContextImpl implements QueryContext {

    private final ReadableStoreFactory storeFactory;
    private final Query query;

    /**
     * Constructor of {@code QueryContextImpl}.
     *
     * @param storeFactory the {@link ReadableStoreFactory} used to create the stores
     * @param query the query that is currently being processed
     * @throws NullPointerException if {@code query} is {@code null}
     */
    public QueryContextImpl(@NonNull final ReadableStoreFactory storeFactory, @NonNull final Query query) {
        this.storeFactory = requireNonNull(storeFactory, "The supplied argument 'storeFactory' cannot be null!");
        this.query = requireNonNull(query, "The supplied argument 'query' cannot be null!");
    }

    @Override
    @NonNull
    public Query query() {
        return query;
    }

    @Override
    @NonNull
    public <C> C createStore(@NonNull Class<C> storeInterface) {
        return storeFactory.createStore(storeInterface);
    }
}
