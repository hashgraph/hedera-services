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
import com.swirlds.platform.state.spi.ReadableKVState;
import com.swirlds.platform.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A read-only {@link ContractStateStore}.
 */
public class ReadableContractStateStore implements ContractStateStore {
    private static final Logger logger = LogManager.getLogger(ReadableContractStateStore.class);
    private final ReadableKVState<SlotKey, SlotValue> storage;
    private final ReadableKVState<ContractID, Bytecode> bytecode;

    public ReadableContractStateStore(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.storage = states.get(InitialModServiceContractSchema.STORAGE_KEY);
        this.bytecode = states.get(InitialModServiceContractSchema.BYTECODE_KEY);
    }

    @Override
    public Bytecode getBytecode(@NonNull ContractID contractId) {
        return bytecode.get(contractId);
    }

    /**
     * Refuses to put bytecode.
     *
     * @param contractId the contract id to put the {@link Bytecode} for
     * @param code the {@link Bytecode} to put
     * @throws UnsupportedOperationException always
     */
    @Override
    public void putBytecode(@NonNull final ContractID contractId, @NonNull final Bytecode code) {
        throw new UnsupportedOperationException("Cannot put bytecode in a read-only store");
    }

    /**
     * Refuses to remove slots.
     *
     * @param key the {@link SlotKey} to remove
     * @throws UnsupportedOperationException always
     */
    @Override
    public void removeSlot(@NonNull final SlotKey key) {
        throw new UnsupportedOperationException("Cannot remove slots from a read-only store");
    }

    /**
     * Refuses to put slots.
     *
     * @param key the {@link SlotKey} to put the {@link SlotValue} for
     * @param value the {@link SlotValue} to put
     * @throws UnsupportedOperationException always
     */
    @Override
    public void putSlot(@NonNull final SlotKey key, @NonNull final SlotValue value) {
        throw new UnsupportedOperationException("Cannot put slots in a read-only store");
    }

    /**
     * Returns an empty set of modified slot keys.
     *
     * @return an empty set of modified slot keys
     */
    @Override
    public Set<SlotKey> getModifiedSlotKeys() {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable SlotValue getSlotValue(@NonNull final SlotKey key) {
        return storage.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlotValue getSlotValueForModify(@NonNull SlotKey key) {
        throw new UnsupportedOperationException("Cannot get for modify in a read-only store");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable SlotValue getOriginalSlotValue(@NonNull SlotKey key) {
        return storage.get(key);
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
