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

package com.swirlds.state.spi;

import com.swirlds.state.spi.metrics.StoreMetrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.Set;

/**
 * A mutable state.
 *
 * @param <K> The key, which must be of the appropriate kind depending on whether it is stored in
 *     memory, or on disk.
 * @param <V> The value, which must be of the appropriate kind depending on whether it is stored in
 *     memory, or on disk.
 */
public interface WritableKVState<K, V> extends ReadableKVState<K, V> {

    /**
     * Gets the value associated with the given key in a <strong>READ-WRITE</strong> way. The
     * returned value will be null if the key does not exist in the store or if the value did exist,
     * but the expiration time has been exceeded.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return The value, or null if there is no such key in the state
     * @throws NullPointerException if the key is null.
     */
    @Nullable
    V getForModify(@NonNull K key);

    /**
     * Gets the original value associated with the given key before any modifications were made to
     * it. The returned value will be {@code null} if the key does not exist.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return The original value, or null if there is no such key in the state
     * @throws NullPointerException if the key is null.
     */
    @Nullable
    V getOriginalValue(@NonNull K key);

    /**
     * Adds a new value to the store, or updates an existing value. It is generally preferred to use
     * {@link #getForModify(K)} to get a writable value, and only use this method if the key does
     * not already exist in the store.
     *
     * @param key The key. Cannot be null.
     * @param value The value. Cannot be null.
     * @throws NullPointerException if the key or value is null.
     */
    void put(@NonNull K key, @NonNull V value);

    /**
     * Removes the given key and its associated value from the map. Subsequent calls to {@link
     * #contains} with the given key will return false, and subsequent calls to {@link
     * #get} and {@link #getForModify} will return empty optionals.
     *
     * @param key The key representing the key/value to remove. Cannot be null.
     * @throws NullPointerException if the key or value is null.
     */
    void remove(@NonNull K key);

    /**
     * {@inheritDoc}
     *
     * <p>When used on a {@link WritableKVState}, this iterator will include new keys added to the
     * state but not yet committed, and omit keys that have been removed but not yet committed, and
     * of course whatever the committed backend state is.
     *
     * <p>If an iterator is created, and then a change is made to this state, then the changes will
     * not be reflected in the iterator. If an iterator is created, and then the state is committed,
     * further operations on the iterator may succeed, or may throw {@link
     * java.util.ConcurrentModificationException}, depending on the behavior of the backing store.
     */
    @Override
    @NonNull
    Iterator<K> keys();

    /**
     * Gets a {@link Set} of all keys that have been modified through this {@link WritableKVState}.
     * Keys used with {@link #getForModify(K)} and {@link #put(K, V)} and {@link #remove(K)} are
     * included in this set.
     *
     * @return A non-null set of modified keys, which may be empty.
     */
    @NonNull
    Set<K> modifiedKeys();

    /**
     * Returns {@code true} if this {@link WritableKVState} has been modified since it was created
     *
     * @return {@code true} if this {@link WritableKVState} has been modified since it was created
     */
    default boolean isModified() {
        return !modifiedKeys().isEmpty();
    }

    /**
     * Sets up metrics for the {@code WritableKVState}.
     *
     * <p>This is an intermediate solution until we are sure the data layer is reporting the right values.
     * The default implementation is empty which means that no metrics are set up.
     *
     * @param storeMetrics helper class to report utilization-metrics
     */
    default void setMetrics(@NonNull StoreMetrics storeMetrics) {}
}
