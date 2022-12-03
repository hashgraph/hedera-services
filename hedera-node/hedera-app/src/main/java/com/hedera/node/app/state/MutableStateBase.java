/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state;

import com.hedera.node.app.spi.state.WritableState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A base class for implementations of {@link WritableState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public abstract class MutableStateBase<K, V> extends StateBase<K, V>
        implements WritableState<K, V> {
    /** A map of all modified values buffered in this mutable state */
    private final Map<K, Modification<V>> modifications = new HashMap<>();

    /**
     * Create a new StateBase.
     *
     * @param stateKey The state key. Cannot be null.
     */
    protected MutableStateBase(@NonNull final String stateKey) {
        super(stateKey);
    }

    /** Flushes all changes into the underlying data store. */
    public final void commit() {
        for (final var entry : modifications.entrySet()) {
            final var key = entry.getKey();
            final var mod = entry.getValue();
            if (mod.removed) {
                removeFromDataSource(key);
            } else if (mod.value != null) {
                putIntoDataSource(key, mod.value);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the set of modified keys and removed keys. Equivalent semantically to a "rollback"
     * operation.
     */
    @Override
    public final void reset() {
        super.reset();
        modifications.clear();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public final Optional<V> getForModify(@NonNull final K key) {
        // If there is a modification, then we've already done a "put" or "remove"
        // and should return based on the modification
        final var mod = modifications.get(key);
        if (mod != null) {
            return mod.removed ? Optional.empty() : Optional.ofNullable(mod.value);
        }

        // If the modifications map does not contain an answer, but the read cache of the
        // super class does, then it means we've looked this up before but never modified it.
        // So we can just delegate to the super class.
        if (hasBeenRead(key)) {
            return super.get(key);
        }

        // We have not queried this key before, so let's look it up and store that we have
        // read this key. And then return the value.
        final var val = getForModifyFromDataSource(key);
        markRead(key, val);
        return Optional.ofNullable(val);
    }

    /** {@inheritDoc} */
    @Override
    public final void put(@NonNull final K key, @NonNull final V value) {
        modifications.put(key, new Modification<>(false, value));
    }

    /** {@inheritDoc} */
    @Override
    public final void remove(@NonNull final K key) {
        modifications.put(key, new Modification<>(true, null));
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public final Set<K> modifiedKeys() {
        return modifications.keySet();
    }

    /**
     * Reads from the underlying data source in such a way as to cause any fast-copyable data
     * structures underneath to make a fast copy.
     *
     * @param key key to read from state
     * @return The value read from the underlying data source. May be null.
     */
    protected abstract V getForModifyFromDataSource(@NonNull K key);

    /**
     * Puts the given key/value pair into the underlying data source.
     *
     * @param key key to update
     * @param value value to put
     */
    protected abstract void putIntoDataSource(@NonNull K key, @NonNull V value);

    /**
     * Removes the given key and implicit value from the underlying data source.
     *
     * @param key key to remove from the underlying data source
     */
    protected abstract void removeFromDataSource(@NonNull K key);

    /**
     * The record of a modification that was made OR ATTEMPTED TO BE MADE by this {@link
     * MutableStateBase}. If a key is put, removed, or "getForModify", then a record of that work
     * will be made in the {@link MutableStateBase#modifications} map. Even if a "getForModify" is
     * made on a key that doesn't exist in the backing storage, we will create a {@link
     * Modification} and store it, so we don't go looking up that value again later.
     *
     * @param removed Whether this modification represents a removal operation
     * @param value The value, which may be null
     * @param <V> The type of value
     */
    private record Modification<V>(boolean removed, @Nullable V value) {}
}
