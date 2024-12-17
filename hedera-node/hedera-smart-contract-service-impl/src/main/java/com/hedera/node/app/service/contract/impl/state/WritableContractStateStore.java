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

package com.hedera.node.app.service.contract.impl.state;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.metrics.StoreMetricsService.StoreType;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

/**
 * A fully mutable {@link ContractStateStore}.
 */
public class WritableContractStateStore implements ContractStateStore {
    private final WritableKVState<SlotKey, SlotValue> storage;
    private final WritableKVState<ContractID, Bytecode> bytecode;

    /**
     * Create a new {@link WritableContractStateStore} instance.
     *
     * @param states The state to use.
     * @param configuration The configuration used to read the maximum capacity.
     * @param storeMetricsService Service that provides utilization metrics.
     */
    public WritableContractStateStore(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        requireNonNull(states);
        this.storage = states.get(V0490ContractSchema.STORAGE_KEY);
        this.bytecode = states.get(V0490ContractSchema.BYTECODE_KEY);

        final ContractsConfig contractsConfig = configuration.getConfigData(ContractsConfig.class);

        final long maxSlotStorageCapacity = contractsConfig.maxKvPairsAggregate();
        final var storageSlotsMetrics = storeMetricsService.get(StoreType.SLOT_STORAGE, maxSlotStorageCapacity);
        storage.setMetrics(storageSlotsMetrics);

        final long maxContractsCapacity = contractsConfig.maxNumber();
        final var contractStoreMetrics = storeMetricsService.get(StoreType.CONTRACT, maxContractsCapacity);
        bytecode.setMetrics(contractStoreMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bytecode getBytecode(@NonNull final ContractID contractID) {
        return bytecode.get(requireNonNull(contractID));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putBytecode(@NonNull final ContractID contractID, @NonNull final Bytecode code) {
        bytecode.put(requireNonNull(contractID), requireNonNull(code));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeSlot(@NonNull final SlotKey key) {
        storage.remove(requireNonNull(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSlot(@NonNull final SlotKey key, @NonNull final SlotValue value) {
        storage.put(requireNonNull(key), requireNonNull(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<SlotKey> getModifiedSlotKeys() {
        return storage.modifiedKeys();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable SlotValue getSlotValue(@NonNull final SlotKey key) {
        return storage.get(requireNonNull(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlotValue getSlotValueForModify(@NonNull SlotKey key) {
        return storage.getForModify(requireNonNull(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable SlotValue getOriginalSlotValue(@NonNull final SlotKey key) {
        return storage.getOriginalValue(requireNonNull(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getNumSlots() {
        return storage.size();
    }

    @Override
    public long getNumBytecodes() {
        return bytecode.size();
    }
}
