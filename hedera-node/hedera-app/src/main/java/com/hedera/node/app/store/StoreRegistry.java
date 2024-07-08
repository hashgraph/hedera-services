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

import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.store.ReadableStoreDefinition;
import com.hedera.node.app.spi.store.ReadableStoreDefinition.ReadableStoreProvider;
import com.hedera.node.app.spi.store.ReadableStoreFactory;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.store.WritableStoreDefinition;
import com.hedera.node.app.spi.store.WritableStoreDefinition.WritableStoreProvider;
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
 * A registry for storing readable and writable store definitions and creating store factories.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
@Singleton
public class StoreRegistry {

    private record ReadableStoreEntry(@NonNull String serviceName, @NonNull ReadableStoreProvider<?> provider) {}

    private record WritableStoreEntry(@NonNull String serviceName, @NonNull WritableStoreProvider<?> provider) {}

    private final Map<Class<?>, ReadableStoreEntry> readableStoreEntries = new ConcurrentHashMap<>();
    private final Map<Class<?>, WritableStoreEntry> writableStoreEntries = new ConcurrentHashMap<>();

    @Inject
    public StoreRegistry() {
        // dagger
    }

    /**
     * Registers a readable store with the registry.
     *
     * @param serviceName the name of the service
     * @param definition the definition of the readable store
     * @return {@code this} for fluent usage
     */
    public StoreRegistry registerReadableStore(
            @NonNull final String serviceName, @NonNull final ReadableStoreDefinition<?> definition) {
        requireNonNull(serviceName);
        if (readableStoreEntries.putIfAbsent(
                        definition.storeInterface(), new ReadableStoreEntry(serviceName, definition.provider()))
                != null) {
            throw new IllegalArgumentException("Duplicate readable store provider registration");
        }
        return this;
    }

    /**
     * Registers a collection of readable stores with the registry.
     *
     * @param serviceName the name of the service
     * @param definitions the definitions of the readable stores
     * @return {@code this} for fluent usage
     */
    public StoreRegistry registerReadableStores(
            @NonNull final String serviceName, @NonNull final Collection<ReadableStoreDefinition<?>> definitions) {
        definitions.forEach(definition -> registerReadableStore(serviceName, definition));
        return this;
    }

    /**
     * Registers an array of readable stores with the registry.
     *
     * @param serviceName the name of the service
     * @param definitions the definitions of the readable stores
     * @return {@code this} for fluent usage
     */
    public StoreRegistry registerReadableStores(
            @NonNull final String serviceName, @NonNull final ReadableStoreDefinition<?>... definitions) {
        return registerReadableStores(serviceName, List.of(definitions));
    }

    /**
     * Registers a writable store with the registry.
     *
     * @param serviceName the name of the service
     * @param definition the definition of the writable store
     * @return {@code this} for fluent usage
     */
    public StoreRegistry registerWritableStore(
            @NonNull String serviceName, @NonNull WritableStoreDefinition<?> definition) {
        requireNonNull(serviceName);
        if (writableStoreEntries.putIfAbsent(
                        definition.storeInterface(), new WritableStoreEntry(serviceName, definition.provider()))
                != null) {
            throw new IllegalArgumentException("Duplicate writable store provider registration");
        }
        return this;
    }

    /**
     * Registers a collection of writable stores with the registry.
     *
     * @param serviceName the name of the service
     * @param definitions the definitions of the writable stores
     * @return {@code this} for fluent usage
     */
    public StoreRegistry registerWritableStores(
            @NonNull final String serviceName, @NonNull final Collection<WritableStoreDefinition<?>> definitions) {
        definitions.forEach(definition -> registerWritableStore(serviceName, definition));
        return this;
    }

    /**
     * Registers an array of writable stores with the registry.
     *
     * @param serviceName the name of the service
     * @param definitions the definitions of the writable stores
     * @return {@code this} for fluent usage
     */
    public StoreRegistry registerWritableStores(
            @NonNull final String serviceName, @NonNull final WritableStoreDefinition<?>... definitions) {
        return registerWritableStores(serviceName, List.of(definitions));
    }

    /**
     * Gets a readable store factory for the given state.
     *
     * @param state the state
     * @return the readable store factory
     */
    public ReadableStoreFactory createReadableStoreFactory(@NonNull HederaState state) {
        return new ReadableStoreFactoryImpl(state);
    }

    /**
     * Gets a store factory for the given parameters.
     *
     * @param state the state
     * @param serviceName the name of the service the writable stores will be scoped to
     * @param configuration the configuration to use for the created stores
     * @param storeMetricsService service that provides utilization metrics
     * @return the store factory
     */
    public StoreFactory createStoreFactory(
            @NonNull HederaState state,
            @NonNull String serviceName,
            @NonNull Configuration configuration,
            @NonNull StoreMetricsService storeMetricsService) {
        return new StoreFactoryImpl(state, serviceName, configuration, storeMetricsService);
    }

    /**
     * Factory for creating readable stores. Default implementation of {@link ReadableStoreFactory}.
     */
    private class ReadableStoreFactoryImpl implements ReadableStoreFactory {

        private final HederaState state;

        /**
         * Constructor of {@code ReadableStoreFactory}
         *
         * @param state the {@link HederaState} to use
         */
        private ReadableStoreFactoryImpl(@NonNull final HederaState state) {
            this.state = requireNonNull(state);
        }

        /**
         * Create a new store given the store's interface. This gives read-only access to the store.
         *
         * @param storeInterface The store interface to find and create a store for
         * @param <C>            Interface class for a Store
         * @return An implementation of the provided store interface
         * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
         * @throws NullPointerException     if {@code storeInterface} is {@code null}
         */
        @Override
        @NonNull
        public <C> C getStore(@NonNull final Class<C> storeInterface) throws IllegalArgumentException {
            requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null!");
            final var entry = StoreRegistry.this.readableStoreEntries.get(storeInterface);
            if (entry != null) {
                final var readableStates = state.getReadableStates(entry.serviceName());
                final var store = entry.provider().newInstance(readableStates);
                if (!storeInterface.isInstance(store)) {
                    throw new IllegalArgumentException("ReadableStoreDefinition for " + storeInterface + " is invalid");
                }
                return storeInterface.cast(store);
            }
            throw new IllegalArgumentException("No provider for " + storeInterface.getName() + " registered");
        }
    }

    /**
     * Factory for creating stores. Default implementation of {@link StoreFactory}.
     */
    private class StoreFactoryImpl implements StoreFactory {

        private final HederaState state;
        private final String serviceName;
        private final Configuration configuration;
        private final StoreMetricsService storeMetricsService;
        private final ReadableStoreFactory readableStoreFactory;

        public StoreFactoryImpl(
                @NonNull final HederaState state,
                @NonNull final String serviceName,
                @NonNull final Configuration configuration,
                @NonNull final StoreMetricsService storeMetricsService) {
            this.state = requireNonNull(state);
            this.serviceName = requireNonNull(serviceName);
            this.configuration = requireNonNull(configuration);
            this.storeMetricsService = requireNonNull(storeMetricsService);
            this.readableStoreFactory = StoreRegistry.this.createReadableStoreFactory(state);
        }

        @NonNull
        @Override
        public <T> T readableStore(@NonNull Class<T> storeInterface) {
            return readableStoreFactory.getStore(storeInterface);
        }

        @NonNull
        @Override
        public <T> T writableStore(@NonNull Class<T> storeInterface) {
            requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null!");
            final var entry = StoreRegistry.this.writableStoreEntries.get(storeInterface);
            if (entry != null && serviceName.equals(entry.serviceName())) {
                final var states = state.getWritableStates(serviceName);
                final var store = entry.provider().newInstance(states, configuration, storeMetricsService);
                if (!storeInterface.isInstance(store)) {
                    throw new IllegalArgumentException("WritableStoreDefinition for " + storeInterface + " is invalid");
                }
                return storeInterface.cast(store);
            }
            throw new IllegalArgumentException("No provider for " + storeInterface.getName() + " registered");
        }

        @NonNull
        @Override
        public ReadableStoreFactory asReadOnly() {
            return readableStoreFactory;
        }
    }
}
