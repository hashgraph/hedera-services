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

package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.Set;

/**
 * Provides access to key/value state for a service implementation. This interface is implemented by
 * the Hedera application, and provided to the service implementation at the appropriate times. The
 * methods of this class provide read access to the state.
 *
 * <p>Null values <strong>cannot be stored</strong> in this state. Null is used to indicate the
 * absence of a value.
 *
 * @param <K> The type of the key
 * @param <V> The type of the value
 */
public interface ReadableKVState<K extends Comparable<K>, V> {
    /**
     * Gets the "state key" that uniquely identifies this {@link ReadableKVState} within the {@link
     * Schema} which are scoped to the service implementation. The key is therefore not globally
     * unique, only unique within the service implementation itself.
     *
     * <p>The call is idempotent, always returning the same value. It must never return null.
     *
     * @return The state key. This will never be null, and will always be the same value for an
     *     instance of {@link ReadableKVState}.
     */
    @NonNull
    String getStateKey();

    /**
     * Gets whether the given key exists in this {@link ReadableKVState}.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return true if the key exists in the state.
     */
    default boolean contains(@NonNull final K key) {
        return get(key) != null;
    }

    /**
     * Gets the value associated with the given key in a <strong>READ-ONLY</strong> way. The
     * returned value will be null if the key does not exist in the state, or if the key did exist
     * but the data had expired.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return The value, or null if the key was not found in this {@link ReadableKVState}.
     * @throws NullPointerException if the key is null.
     */
    @Nullable
    V get(@NonNull K key);

    /**
     * Used during migration ONLY. PLEASE DO NOT COME TO RELY ON THIS METHOD! It will be hopelessly
     * slow on large data sets like on disk!
     *
     * @return an iterator over all keys in the state
     */
    @NonNull
    Iterator<K> keys();

    /**
     * Gets the set of keys that a client read from the {@link ReadableKVState}.
     *
     * @return The possibly empty set of keys.
     */
    @NonNull
    Set<K> readKeys();

    /**
     * Gets the number of keys in the {@link ReadableKVState}.
     * @return number of keys in the {@link ReadableKVState}.
     */
    @NonNull
    long size();
}
