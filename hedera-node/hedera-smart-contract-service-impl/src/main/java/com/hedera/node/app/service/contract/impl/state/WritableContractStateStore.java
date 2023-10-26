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

package com.hedera.node.app.service.contract.impl.state;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

/**
 * A fully mutable {@link ContractStateStore}.
 */
public class WritableContractStateStore implements ContractStateStore {
    private final WritableKVState<SlotKey, SlotValue> storage;
    private final WritableKVState<EntityNumber, Bytecode> bytecode;

    public WritableContractStateStore(@NonNull final WritableStates states) {
        requireNonNull(states);
        this.storage = states.get(ContractSchema.STORAGE_KEY);
        this.bytecode = states.get(ContractSchema.BYTECODE_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bytecode getBytecode(@NonNull final EntityNumber contractNumber) {
        return bytecode.get(requireNonNull(contractNumber));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putBytecode(@NonNull final EntityNumber contractNumber, @NonNull final Bytecode code) {
        bytecode.put(requireNonNull(contractNumber), requireNonNull(code));
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
