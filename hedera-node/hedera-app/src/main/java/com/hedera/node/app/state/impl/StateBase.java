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

import com.hedera.node.app.spi.state.ReadableState;
import com.hedera.node.app.spi.state.WritableState;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.*;

/**
 * A base class for implementations of {@link ReadableState} and {@link WritableState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
abstract class StateBase<K, V> implements ReadableState<K, V> {
    /** The state key, which cannot be null */
    private final String stateKey;

    /**
     * A cache of all values read from this {@link ReadableState}. If the same value is read twice,
     * rather than going to the underlying merkle data structures to read the data a second time, we
     * simply return it from the cache. We also keep track of all keys read, which is critical for
     * dealing with validating what we read during pre-handle with what may have changed before we
     * got to handle transaction.
     */
    private final Map<K, V> readCache = new HashMap<>();

    /**
     * Create a new StateBase.
     *
     * @param stateKey The state key. Cannot be null.
     */
    StateBase(@NonNull String stateKey) {
        this.stateKey = Objects.requireNonNull(stateKey);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public final String getStateKey() {
        return stateKey;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public final Optional<V> get(@NonNull K key) {
        Objects.requireNonNull(key);
        return Optional.ofNullable(
                readCache.computeIfAbsent(key, ignore -> readFromDataSource(key)));
    }

    /** {@inheritDoc} */
    @Override
    public final boolean contains(@NonNull K key) {
        // We need to cache the item because somebody may perform business logic basic on this
        // contains call, even if they never need the value itself!
        final var item = readCache.computeIfAbsent(key, ignore -> readFromDataSource(key));
        return item != null;
    }

    /**
     * Gets the set of keys that a client read from the {@link ReadableState}.
     *
     * @return The possibly empty set of keys.
     */
    @NonNull
    public final Set<K> readKeys() {
        return readCache.keySet();
    }

    /** Clears all cached data, including the set of all read keys. */
    public void reset() {
        readCache.clear();
    }

    /**
     * Reads the keys from the underlying data source (which may be a merkle data structure, a
     * fast-copyable data structure, or something else).
     *
     * @param key key to read from state
     * @return The value read from the underlying data source. May be null.
     */
    protected abstract V readFromDataSource(@NonNull K key);
}
