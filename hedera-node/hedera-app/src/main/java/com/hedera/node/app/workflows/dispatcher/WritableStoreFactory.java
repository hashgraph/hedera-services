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

import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.WritableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.WritableFreezeStore;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.schedule.impl.WritableScheduleStoreImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
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
    private static final Map<Class<?>, StoreEntry> STORE_FACTORY = createFactoryMap();

    private static Map<Class<?>, StoreEntry> createFactoryMap() {
        final Map<Class<?>, StoreEntry> newMap = new HashMap<>();
        // ConsensusService
        newMap.put(WritableTopicStore.class, new StoreEntry(ConsensusService.NAME, WritableTopicStore::new));
        // TokenService
        newMap.put(WritableAccountStore.class, new StoreEntry(TokenService.NAME, WritableAccountStore::new));
        newMap.put(WritableNftStore.class, new StoreEntry(TokenService.NAME, WritableNftStore::new));
        newMap.put(WritableTokenStore.class, new StoreEntry(TokenService.NAME, WritableTokenStore::new));
        newMap.put(
                WritableTokenRelationStore.class, new StoreEntry(TokenService.NAME, WritableTokenRelationStore::new));
        newMap.put(
                WritableNetworkStakingRewardsStore.class,
                new StoreEntry(TokenService.NAME, WritableNetworkStakingRewardsStore::new));
        newMap.put(WritableStakingInfoStore.class, new StoreEntry(TokenService.NAME, WritableStakingInfoStore::new));
        // FreezeService
        newMap.put(WritableFreezeStore.class, new StoreEntry(FreezeService.NAME, WritableFreezeStore::new));
        // FileService
        newMap.put(WritableFileStore.class, new StoreEntry(FileService.NAME, WritableFileStore::new));
        newMap.put(WritableUpgradeFileStore.class, new StoreEntry(FileService.NAME, WritableUpgradeFileStore::new));
        // ContractService
        newMap.put(
                WritableContractStateStore.class,
                new StoreEntry(ContractService.NAME, WritableContractStateStore::new));
        // ScheduleService
        newMap.put(WritableScheduleStore.class, new StoreEntry(ScheduleService.NAME, WritableScheduleStoreImpl::new));
        // EntityIdService
        newMap.put(WritableEntityIdStore.class, new StoreEntry(EntityIdService.NAME, WritableEntityIdStore::new));
        // Schedule Service
        newMap.put(WritableScheduleStore.class, new StoreEntry(ScheduleService.NAME, WritableScheduleStoreImpl::new));
        return Collections.unmodifiableMap(newMap);
    }

    private final String serviceName;
    private final WritableStates states;

    /**
     * Constructor of {@code WritableStoreFactory}
     *
     * @param stack       the {@link HederaState} to use
     * @param serviceName the name of the service to create stores for
     * @throws NullPointerException     if one of the arguments is {@code null}
     * @throws IllegalArgumentException if the service name is unknown
     */
    public WritableStoreFactory(@NonNull final SavepointStackImpl stack, @NonNull final String serviceName) {
        requireNonNull(stack, "The argument 'stack' cannot be null!");
        requireNonNull(serviceName, "The argument 'serviceName' cannot be null!");

        this.serviceName = serviceName;
        this.states = stack.createWritableStates(serviceName);
    }

    /**
     * Create a new store given the store's interface. This gives read and write access to the store.
     *
     * @param <C>            Interface class for a Store
     * @param storeInterface The store interface to find and create a store for
     * @return An implementation of the provided store interface
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException     if {@code storeInterface} is {@code null}
     */
    @NonNull
    public <C> C getStore(@NonNull final Class<C> storeInterface) throws IllegalArgumentException {
        requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null!");
        final var entry = STORE_FACTORY.get(storeInterface);
        if (entry != null && serviceName.equals(entry.name())) {
            final var store = entry.factory().apply(states);
            if (!storeInterface.isInstance(store)) {
                throw new IllegalArgumentException("No instance " + storeInterface
                        + " is available"); // This needs to be ensured while stores are registered
            }
            return storeInterface.cast(store);
        }
        throw new IllegalArgumentException("No store of the given class is available");
    }

    private record StoreEntry(@NonNull String name, @NonNull Function<WritableStates, ?> factory) {}
}
