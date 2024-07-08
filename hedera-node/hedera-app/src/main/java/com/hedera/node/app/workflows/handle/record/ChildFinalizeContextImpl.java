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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.node.app.workflows.handle.record.TokenContextImpl.castRecordBuilder;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.token.records.ChildFinalizeContext;
import com.hedera.node.app.spi.store.StoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Standard implementation of {@link ChildFinalizeContext}.
 */
public class ChildFinalizeContextImpl implements ChildFinalizeContext {
    private final Configuration configuration;
    private final StoreFactory storeFactory;
    private final SingleTransactionRecordBuilderImpl recordBuilder;

    public ChildFinalizeContextImpl(
            @NonNull final Configuration configuration,
            @NonNull final StoreFactory storeFactory,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        this.configuration = requireNonNull(configuration);
        this.storeFactory = requireNonNull(storeFactory);
        this.recordBuilder = requireNonNull(recordBuilder);
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return storeFactory.readableStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T writableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return storeFactory.writableStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T userTransactionRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castRecordBuilder(recordBuilder, recordBuilderClass);
    }

    @Override
    public @NonNull Configuration configuration() {
        return configuration;
    }
}
