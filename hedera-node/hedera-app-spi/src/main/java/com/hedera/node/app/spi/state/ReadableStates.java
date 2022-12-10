/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

/** Essentially, a map of {@link ReadableState}s. Each state may be retrieved by key. */
public interface ReadableStates {
    /**
     * Gets the {@link ReadableState} associated with the given stateKey. If the state cannot be
     * found, an exception is thrown. This should **never** happen in an application, and represents
     * a fatal bug. Applications must only ask for states that they have previously registered with
     * the {@link StateRegistry}.
     *
     * @param stateKey The key used for looking up state
     * @return The state for that key. This will never be null.
     * @param <K> The key type in the state.
     * @param <V> The value type in the state.
     * @throws NullPointerException if stateKey is null.
     * @throws IllegalArgumentException if the state cannot be found.
     */
    @NonNull
    <K, V> ReadableState<K, V> get(@NonNull String stateKey);

    /**
     * Gets whether the given state key is a member of this set.
     *
     * @param stateKey The state key
     * @return true if a subsequent call to {@link #get(String)} with this state key would succeed.
     */
    boolean contains(@NonNull String stateKey);

    /**
     * Gets the number of states contained in this instance.
     *
     * @return The number of states. The value will be non-negative.
     */
    int size();

    /**
     * Gets whether this instance is empty, that is, it has no states.
     *
     * @return True if there are no states in this instance.
     */
    default boolean isEmpty() {
        return size() == 0;
    }
}
