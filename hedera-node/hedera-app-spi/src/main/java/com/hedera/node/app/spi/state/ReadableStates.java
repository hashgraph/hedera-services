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
import java.util.Set;

/** Essentially, a map of {@link ReadableKVState}s. Each state may be retrieved by key. */
public interface ReadableStates {
    /**
     * Gets the {@link ReadableKVState} associated with the given stateKey. If the state cannot be
     * found, an exception is thrown. This should **never** happen in an application, and represents
     * a fatal bug. Applications must only ask for states that they have previously registered with
     * the {@link Schema}.
     *
     * <p>This method is idempotent. When called with the same stateKey, the same {@link
     * ReadableKVState} instance is returned.
     *
     * @param stateKey The key used for looking up state
     * @return The state for that key. This will never be null.
     * @param <K> The key type in the state.
     * @param <V> The value type in the state.
     * @throws NullPointerException if stateKey is null.
     * @throws IllegalArgumentException if the state cannot be found.
     */
    @NonNull
    <K extends Comparable<K>, V> ReadableKVState<K, V> get(@NonNull String stateKey);

    @NonNull
    <T> ReadableSingletonState<T> getSingleton(@NonNull String stateKey);

    /**
     * Gets whether the given state key is a member of this set.
     *
     * @param stateKey The state key
     * @return true if a subsequent call to {@link #get(String)} with this state key would succeed.
     */
    boolean contains(@NonNull String stateKey);

    /**
     * Gets the set of all state keys supported by this map of states.
     *
     * @return The set of all state keys.
     */
    @NonNull
    Set<String> stateKeys();

    /**
     * Gets the number of states contained in this instance.
     *
     * @return The number of states. The value will be non-negative.
     */
    default int size() {
        return stateKeys().size();
    }

    /**
     * Gets whether this instance is empty, that is, it has no states.
     *
     * @return True if there are no states in this instance.
     */
    default boolean isEmpty() {
        return stateKeys().isEmpty();
    }
}
