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

import com.swirlds.common.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/** Defines a registry of states for services. */
public interface StateRegistry {

    /**
     * Gets the current {@link SoftwareVersion} of this application at the time of startup. This may
     * be different from the {@link #getExistingVersion()} if the system is starting with an older,
     * existing body of state from an older version.
     *
     * @return The version of the current system.
     */
    @NonNull
    SoftwareVersion getCurrentVersion();

    /**
     * Gets the {@link SoftwareVersion} of the state at the time of startup. This may be different
     * from {@link #getCurrentVersion()} if the system is starting with an older, existing body of
     * state from an older version.
     *
     * @return The version of the system's state that was loaded, or {@link
     *     SoftwareVersion#NO_VERSION}.
     */
    @Nullable
    SoftwareVersion getExistingVersion();

    /**
     * Gets an existing state from the {@link StateRegistry}, identified by {@code stateKey}.
     *
     * @param stateKey The key of the state to get.
     * @return the {@link WritableState} associated with the key, if there is one. This state should
     *     <b>NOT</b> be held but should only be used during construction of the service. It refers
     *     to a mutable state, which will not be mutable once the service is up and running! The
     *     returned value may be null if there is no such state.
     * @param <K> The key for the state.
     * @param <V> The value for the state.
     */
    @Nullable
    <K, V> WritableState<K, V> getState(@NonNull String stateKey);

    /**
     * Replaces any existing state associated with {@code stateKey} with a new, empty state as
     * defined by {@link StateDefinition}. Or, create a new state that didn't exist before.
     *
     * <p>If you need to migrate data from an old state, please use {@link #getState(String)} first
     * to get the old state, then replace the state with this method, and then call {@link
     * #getState(String)} again to get the new state. Then migrate data from old to new.
     *
     * @param stateKey The state key. Cannot be null and must be a valid state key.
     * @return A {@link StateDefinition} to be used to define the state.
     */
    StateDefinition defineNewState(@NonNull String stateKey);

    /**
     * Removes the specified state from the registry.
     *
     * @param stateKey The key of the state to remove
     */
    void removeState(@NonNull String stateKey);
}
