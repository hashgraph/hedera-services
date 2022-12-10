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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Optional;

/**
 * Provides access to key/value state for a service implementation. This interface is implemented by
 * the Hedera application, and provided to the service implementation at the appropriate times. The
 * methods of this class provide read-only access to the state.
 *
 * @param <K> The key, which must be of the appropriate kind depending on whether it is stored in
 *     memory, or on disk.
 * @param <V> The value, which must be of the appropriate kind depending on whether it is stored in
 *     memory, or on disk.
 */
public interface ReadableState<K, V> {
    /**
     * Gets the "state key" that uniquely identifies this {@link ReadableState} within the {@link
     * StateRegistry} which are scoped to the service implementation. The key is therefore not
     * globally unique, only unique within the service implementation itself.
     *
     * <p>The call is idempotent, always returning the same value. It must never return null.
     *
     * @return The state key. This will never be null, and will always be the same value for an
     *     instance of {@link ReadableState}.
     */
    @NonNull
    String getStateKey();

    /**
     * Gets whether the given key exists in this {@link ReadableState}.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return true if the key exists in the state.
     */
    default boolean contains(@NonNull K key) {
        return get(key).isPresent();
    }

    /**
     * Gets the value associated with the given key in a <strong>READ-ONLY</strong> way. The
     * returned {@link Optional} will be empty if the key does not exist in the state. If the value
     * did exist, but the expiration time has been exceeded, then the value will be unset in the
     * {@link Optional}.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return A non-null optional. It may be empty if there is no value for this associated key.
     * @throws NullPointerException if the key is null.
     */
    @NonNull
    Optional<V> get(@NonNull K key);

    /**
     * Used during migration ONLY. PLEASE DO NOT COME TO RELY ON THIS METHOD! It will be hopelessly
     * slow on large data sets like on disk!
     *
     * @return an iterator over all keys in the state
     */
    @NonNull
    Iterator<K> keys();
}
