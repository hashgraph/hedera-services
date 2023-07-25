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

package com.hedera.node.app.workflows.dispatcher;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.ids.EntityIdServiceApiImpl;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.api.TokenServiceApiImpl;
import com.hedera.node.app.spi.ids.EntityIdServiceApi;
import com.hedera.node.app.spi.store.WritableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A factory for creating service APIs.
 */
public class ServiceApiFactory {
    private final Supplier<Configuration> configuration;
    private final WritableStoreFactory writableStoreFactory;

    private static final Map<Class<?>, BiFunction<Configuration, WritableStoreFactory, ?>> API_FACTORY = Map.of(
            TokenServiceApi.class, TokenServiceApiImpl::new,
            EntityIdServiceApi.class, EntityIdServiceApiImpl::new);

    public ServiceApiFactory(
            @NonNull final Supplier<Configuration> configuration,
            @NonNull final WritableStoreFactory writableStoreFactory) {
        this.configuration = requireNonNull(configuration);
        this.writableStoreFactory = requireNonNull(writableStoreFactory);
    }

    public <C> C getApi(@NonNull final Class<C> apiInterface) throws IllegalArgumentException {
        requireNonNull(apiInterface);
        final var factory = API_FACTORY.get(apiInterface);
        if (factory != null) {
            final var api = factory.apply(configuration.get(), writableStoreFactory);
            assert apiInterface.isInstance(api); // This needs to be ensured while apis are registered
            return apiInterface.cast(api);
        }
        throw new IllegalArgumentException("No store of the given class is available");
    }
}
