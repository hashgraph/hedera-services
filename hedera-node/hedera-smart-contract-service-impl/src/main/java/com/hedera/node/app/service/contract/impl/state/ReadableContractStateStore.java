package com.hedera.node.app.service.contract.impl.state;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A read-only {@link ContractStateStore}.
 */
public class ReadableContractStateStore implements ContractStateStore {
    private final ReadableKVState<SlotKey, SlotValue> storage;
    private final ReadableKVState<EntityNumber, Bytecode> bytecode;


    public ReadableContractStateStore(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.storage = states.get(ContractSchema.STORAGE_KEY);
        this.bytecode = states.get(ContractSchema.BYTECODE_KEY);
    }

    @Override
    public Bytecode getBytecode(@NonNull EntityNumber contractNumber) {
        return bytecode.get(contractNumber);
    }

    /**
     * Refuses to put bytecode.
     *
     * @param contractNumber the contract number to put the {@link Bytecode} for
     * @param code the {@link Bytecode} to put
     * @throws UnsupportedOperationException always
     */
    @Override
    public void putBytecode(@NonNull final EntityNumber contractNumber, @NonNull final Bytecode code) {
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
}
