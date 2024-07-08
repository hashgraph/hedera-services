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

package com.hedera.node.app.api;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.api.ServiceApiDefinition;
import com.hedera.node.app.spi.api.ServiceApiDefinition.ServiceApiProvider;
import com.hedera.node.app.spi.api.ServiceApiFactory;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A registry for storing service API definitions and creating service API factories.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
@Singleton
public class ServiceApiRegistry {

    private record Entry(@NonNull String serviceName, @NonNull ServiceApiProvider<?> provider) {}

    private final Map<Class<?>, Entry> entries = new ConcurrentHashMap<>();

    @Inject
    public ServiceApiRegistry() {
        // dagger
    }

    /**
     * Registers a service API with the registry.
     *
     * @param serviceName the name of the service
     * @param definition the definition of the service API
     * @return {@code this} for fluent usage
     */
    public ServiceApiRegistry registerServiceApi(
            @NonNull final String serviceName, @NonNull final ServiceApiDefinition<?> definition) {
        requireNonNull(serviceName);
        if (entries.putIfAbsent(definition.serviceApiInterface(), new Entry(serviceName, definition.provider()))
                != null) {
            throw new IllegalArgumentException("Duplicate service API provider registration");
        }
        return this;
    }

    /**
     * Registers a collection of service APIs with the registry.
     *
     * @param serviceName the name of the service
     * @param definitions the definitions of the service APIs
     * @return {@code this} for fluent usage
     */
    public ServiceApiRegistry registerServiceApis(
            @NonNull final String serviceName, @NonNull final Collection<ServiceApiDefinition<?>> definitions) {
        definitions.forEach(definition -> registerServiceApi(serviceName, definition));
        return this;
    }

    /**
     * Registers an array of service APIs with the registry.
     *
     * @param serviceName the name of the service
     * @param definitions the definitions of the service APIs
     * @return {@code this} for fluent usage
     */
    public ServiceApiRegistry registerServiceApis(
            @NonNull final String serviceName, @NonNull final ServiceApiDefinition<?>... definitions) {
        return registerServiceApis(serviceName, List.of(definitions));
    }

    /**
     * Gets a service API factory for the given parameters.
     *
     * @param state the state
     * @param configuration the configuration to use for the created stores
     * @param storeMetricsService service that provides utilization metrics
     * @return the store factory
     */
    public ServiceApiFactory createServiceApiFactory(
            @NonNull HederaState state,
            @NonNull Configuration configuration,
            @NonNull StoreMetricsService storeMetricsService) {
        return new ServiceApiFactoryImpl(state, configuration, storeMetricsService);
    }

    /**
     * Factory for creating service APIs. Default implementation of {@link ServiceApiFactory}.
     */
    private class ServiceApiFactoryImpl implements ServiceApiFactory {

        private final HederaState state;
        private final Configuration configuration;
        private final StoreMetricsService storeMetricsService;

        public ServiceApiFactoryImpl(
                @NonNull final HederaState state,
                @NonNull final Configuration configuration,
                @NonNull final StoreMetricsService storeMetricsService) {
            this.state = requireNonNull(state);
            this.configuration = requireNonNull(configuration);
            this.storeMetricsService = requireNonNull(storeMetricsService);
        }

        @Override
        @NonNull
        public <C> C serviceApi(@NonNull final Class<C> apiInterface) {
            requireNonNull(apiInterface);
            final var entry = entries.get(apiInterface);
            if (entry != null) {
                final var writableStates = state.getWritableStates(entry.serviceName());
                final var api = entry.provider().newInstance(writableStates, configuration, storeMetricsService);
                if (!apiInterface.isInstance(api)) {
                    throw new IllegalArgumentException("ServiceApiDefinition for " + apiInterface + " is invalid");
                }
                return apiInterface.cast(api);
            }
            throw new IllegalArgumentException("No provider for " + apiInterface.getName() + " registered");
        }
    }
}
