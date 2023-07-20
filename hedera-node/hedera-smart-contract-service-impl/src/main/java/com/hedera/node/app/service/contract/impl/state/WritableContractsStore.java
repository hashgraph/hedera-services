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

/**
 * A minimal adapter required by {@link com.hedera.node.app.spi.workflows.HandleContext}
 * to get access to the slot and bytecode {@link WritableKVState}s.
 */
public class WritableContractsStore {
    private final WritableKVState<SlotKey, SlotValue> storage;
    private final WritableKVState<EntityNumber, Bytecode> bytecode;

    public WritableContractsStore(@NonNull final WritableStates state) {
        requireNonNull(state);
        this.storage = state.get(ContractSchema.STORAGE_KEY);
        this.bytecode = state.get(ContractSchema.BYTECODE_KEY);
    }

    /**
     * Returns the {@link WritableKVState} for the slot storage.
     *
     * @return the slots {@link WritableKVState}
     */
    public WritableKVState<SlotKey, SlotValue> storage() {
        return storage;
    }

    /**
     * Returns the {@link WritableKVState} for the bytecode storage.
     *
     * @return the bytecode {@link WritableKVState}
     */
    public WritableKVState<EntityNumber, Bytecode> bytecode() {
        return bytecode;
    }
}
