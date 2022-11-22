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
package com.hedera.node.app.state.impl;

import com.hedera.node.app.spi.state.WritableState;
import com.swirlds.common.merkle.MerkleNode;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.*;

/**
 * A base class for implementations of {@link WritableState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
abstract class MutableStateBase<K, V> extends StateBase<K, V> implements WritableState<K, V> {
    /** A map of all modified values buffered in this mutable state */
    private final Map<K, V> modified = new HashMap<>();

    /** A set of all keys (and their implicit values) removed by this mutable state */
    private final Set<K> removed = new HashSet<>();

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
        for (final K key : removed) {
            removeFromDataSource(key);
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
        modified.clear();
        removed.clear();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public final Optional<V> getForModify(@NonNull final K key) {
        // Check whether the key has been removed!
        if (removed.contains(key)) {
            return Optional.empty();
        }

        // Either get the value from the cache, if we've already seen this key, or get the key
        // from the underlying data source and store it into the map
        // QUESTION: Should we store data source lookup misses as well, so we never go back? Use a
        // sentinel?
        final var value = modified.computeIfAbsent(key, ignore -> getForModifyFromDataSource(key));
        return Optional.ofNullable(value);
    }

    /** {@inheritDoc} */
    @Override
    public final void put(@NonNull final K key, @NonNull final V value) {
        modified.put(key, value);
        removed.remove(key);
    }

    /** {@inheritDoc} */
    @Override
    public final void remove(@NonNull final K key) {
        modified.remove(key);
        removed.add(key);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public final Set<K> modifiedKeys() {
        final var combinedKeys = modified.keySet();
        combinedKeys.addAll(removed);
        return combinedKeys;
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
     * Gets the underlying merkle node.
     *
     * @return The merkle node
     * @param <T> The type of merkle node
     */
    @NonNull
    protected abstract <T extends MerkleNode> T merkleNode();
}
