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

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.admin.impl.ReadableSpecialFileStore;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for all readable stores. It creates new readable stores based on the {@link HederaState}.
 *
 * <p>The initial implementation creates all known stores hard-coded. In a future version, this will be replaced by a
 * dynamic approach.
 */
public class ReadableStoreFactory implements PreHandleContext.ReadableStoreFactory {

    // This is the hard-coded part that needs to be replaced by a dynamic approach later,
    // e.g. services have to register their stores
    private static final Map<Class<?>, StoreEntry> STORE_FACTORY = Map.of(
            AccountAccess.class, new StoreEntry(TokenService.NAME, ReadableAccountStore::new),
            ReadableAccountStore.class, new StoreEntry(TokenService.NAME, ReadableAccountStore::new),
            ReadableTokenStore.class, new StoreEntry(TokenService.NAME, ReadableTokenStore::new),
            ReadableTopicStore.class, new StoreEntry(ConsensusService.NAME, ReadableTopicStore::new),
            ReadableScheduleStore.class, new StoreEntry(ScheduleService.NAME, ReadableScheduleStore::new),
            ReadableSpecialFileStore.class, new StoreEntry(FreezeService.NAME, ReadableSpecialFileStore::new));

    private final HederaState state;

    /**
     * Constructor of {@code ReadableStoreFactory}
     *
     * @param state the {@link HederaState} to use
     */
    public ReadableStoreFactory(@NonNull final HederaState state) {
        this.state = requireNonNull(state, "The supplied argument 'state' cannot be null!");
    }

    /**
     * Create a new store given the store's interface. This gives read-only access to the store.
     *
     * @param storeInterface The store interface to find and create a store for
     * @param <C> Interface class for a Store
     * @return An implementation of the provided store interface
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code clazz} is {@code null}
     */
    @Override
    @NonNull
    public <C> C createStore(@NonNull final Class<C> storeInterface) throws IllegalArgumentException {
        final var entry = STORE_FACTORY.get(storeInterface);
        if (entry != null) {
            final var readableStates = state.createReadableStates(entry.name);
            final var store = entry.factory.apply(readableStates);
            assert storeInterface.isInstance(store); // This needs to be ensured while stores are registered
            return storeInterface.cast(store);
        }
        throw new IllegalArgumentException("No store of the given class is available");
    }

    private record StoreEntry(@NonNull String name, @NonNull Function<ReadableStates, ?> factory) {}
}
