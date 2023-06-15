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

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.WritableUpdateFileStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for all writable stores. It creates new writable stores based on the {@link HederaState}.
 *
 * <p>The initial implementation creates all known stores hard-coded. In a future version, this will be replaced by a
 * dynamic approach.
 */
public class WritableStoreFactory {

    // This is the hard-coded part that needs to be replaced by a dynamic approach later,
    // e.g. services have to register their stores
    private static final Map<String, Map<Class<?>, Function<WritableStates, ?>>> STORE_FACTORY = Map.of(
            ConsensusService.NAME,
            Map.of(WritableTopicStore.class, WritableTopicStore::new),
            TokenService.NAME,
            Map.of(
                    WritableAccountStore.class, WritableAccountStore::new,
                    WritableTokenStore.class, WritableTokenStore::new,
                    WritableTokenRelationStore.class, WritableTokenRelationStore::new),
            FreezeService.NAME,
            Map.of(WritableUpdateFileStore.class, WritableUpdateFileStore::new));

    private final Map<Class<?>, Function<WritableStates, ?>> storeFactories;
    private final WritableStates states;

    /**
     * Constructor of {@code WritableStoreFactory}
     *
     * @param stack the {@link HederaState} to use
     * @param serviceName the name of the service to create stores for
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws IllegalArgumentException if the service name is unknown
     */
    public WritableStoreFactory(@NonNull final SavepointStackImpl stack, @NonNull final String serviceName) {
        requireNonNull(stack, "The argument 'stack' cannot be null!");
        requireNonNull(serviceName, "The argument 'serviceName' cannot be null!");

        this.storeFactories = STORE_FACTORY.get(serviceName);
        if (storeFactories == null) {
            throw new IllegalArgumentException("No store factories for the given service name are available");
        }

        this.states = stack.createWritableStates(serviceName);
    }

    /**
     * Create a new store given the store's interface. This gives read and write access to the store.
     *
     * @param <C> Interface class for a Store
     * @param storeInterface The store interface to find and create a store for
     * @return An implementation of the provided store interface
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code storeInterface} is {@code null}
     */
    @NonNull
    public <C> C getStore(@NonNull final Class<C> storeInterface) throws IllegalArgumentException {
        requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null!");
        final var factory = storeFactories.get(storeInterface);
        if (factory != null) {
            final var store = factory.apply(states);
            assert storeInterface.isInstance(store); // This needs to be ensured while stores are registered
            return storeInterface.cast(store);
        }
        throw new IllegalArgumentException("No store of the given class is available");
    }
}
