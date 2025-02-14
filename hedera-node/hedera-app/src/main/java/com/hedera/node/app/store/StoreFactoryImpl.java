// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.store;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.spi.store.StoreFactory;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

/**
 * Factory for creating stores and service APIs. Default implementation of {@link StoreFactory}.
 */
public class StoreFactoryImpl implements StoreFactory {

    private final ReadableStoreFactory readableStoreFactory;
    private final WritableStoreFactory writableStoreFactory;
    private final ServiceApiFactory serviceApiFactory;

    /**
     * Returns a {@link StoreFactory} based on the given state, configuration, and store metrics for the given service.
     *
     * @param state                 the state to create stores from
     * @param serviceName           the name of the service to scope the stores to
     * @param configuration         the configuration for the service
     * @param writableEntityIdStore the writable entity id store
     * @return a new {@link StoreFactory} instance
     */
    public static StoreFactory from(
            @NonNull final State state,
            @NonNull final String serviceName,
            @NonNull final Configuration configuration,
            @NonNull final WritableEntityIdStore writableEntityIdStore,
            @NonNull final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory) {
        requireNonNull(state);
        requireNonNull(serviceName);
        return new StoreFactoryImpl(
                new ReadableStoreFactory(state, softwareVersionFactory),
                new WritableStoreFactory(state, serviceName, writableEntityIdStore),
                new ServiceApiFactory(state, configuration));
    }

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
