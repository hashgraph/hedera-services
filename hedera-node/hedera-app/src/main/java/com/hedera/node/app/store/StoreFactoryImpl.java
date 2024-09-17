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

package com.hedera.node.app.store;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.store.StoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for creating stores and service APIs. Default implementation of {@link StoreFactory}.
 */
public class StoreFactoryImpl implements StoreFactory {

    private final ReadableStoreFactory readableStoreFactory;
    private final WritableStoreFactory writableStoreFactory;
    private final ServiceApiFactory serviceApiFactory;

    public StoreFactoryImpl(
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final WritableStoreFactory writableStoreFactory,
            @NonNull final ServiceApiFactory serviceApiFactory) {
        this.readableStoreFactory = requireNonNull(readableStoreFactory);
        this.writableStoreFactory = requireNonNull(writableStoreFactory);
        this.serviceApiFactory = requireNonNull(serviceApiFactory);
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface);
        return readableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T writableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface);
        return writableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T serviceApi(@NonNull Class<T> apiInterface) {
        requireNonNull(apiInterface);
        return serviceApiFactory.getApi(apiInterface);
    }

    public ReadableStoreFactory asReadOnly() {
        return readableStoreFactory;
    }
}
