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
package com.hedera.services.base.state;

import java.time.Instant;
import java.util.Optional;

/**
 * Provides access to key/value state for a service implementation.
 *
 * @param <K> The key
 * @param <V> The value
 */
public interface State<K, V> {

    /**
     * Gets the "state key" that uniquely identifies this {@code KVState}. This must be a unique
     * value for this {@code KVState} instance. The call is idempotent, always returning the same
     * value. It must never return null.
     *
     * @return The state key. This will never be null, and will always be the same value.
     */
    String getStateKey();

    /**
     * Gets the value associated with the given key in a <strong>READ-ONLY</strong> way. The
     * returned {@link Optional} will be empty if the key does not exist in the store. If the value
     * did exist, but the expiration time has been exceeded, then the value will be unset in the
     * {@link Optional}.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return A non-null optional. It may be empty if there is no value for this associated key.
     * @throws NullPointerException if the key is null.
     */
    Optional<V> get(K key);

    /**
     * The last time current state is modified. It is needed to check if the state has changed from
     * the last time it is read in pre-handle.
     *
     * @return last modified time
     */
    Instant getLastModifiedTime();
}
