package com.hedera.node.app.service.contract.impl.state;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

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
