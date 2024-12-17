/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.state;

import com.swirlds.state.spi.CommittableWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Defines a listener to be notified of changes made to the {@link com.swirlds.state.spi.WritableStates} returned by
 * a {@link State}. In general, the only {@link State} implementations that will support registering listeners are those
 * that return {@link com.swirlds.state.spi.WritableStates} marked as {@link CommittableWritableStates}.
 * <p>
 * A listener is registered with a {@link State} instead of a single {@link com.swirlds.state.spi.WritableStates}
 * because a listening client will want to be notified of changes to all {@link com.swirlds.state.spi.WritableStates}
 * returned by the {@link State}.
 * <p>
 * All callbacks have default no-op implementations.
 */
public interface StateChangeListener {
    /**
     * The types of state that can change.
     */
    enum StateType {
        MAP,
        QUEUE,
        SINGLETON,
    }

    /**
     * The target state types that the listener is interested in.
     * @return the target state types
     */
    Set<StateType> stateTypes();

    /**
     * Returns the state ID the listener wants to use for the given service and state key.
     * @param serviceName the service name
     * @param stateKey the state key
     * @return the state ID
     * @throws IllegalArgumentException if the listener has no id for the given service and state key
     */
    int stateIdFor(@NonNull String serviceName, @NonNull String stateKey);

    /**
     * Save the state change when an entry is added in to a map.
     *
     * @param <K> The type of the key
     * @param <V> The type of the value
     * @param stateId The id of the map
     * @param key The key added to the map
     * @param value The value added to the map
     */
    default <K, V> void mapUpdateChange(int stateId, @NonNull K key, @NonNull V value) {}

    /**
     * Save the state change when an entry is removed from a map.
     *
     * @param <K> The type of the key
     * @param stateId The id of the map
     * @param key The key removed from the map
     */
    default <K> void mapDeleteChange(int stateId, @NonNull K key) {}

    /**
     * Save the state change when a value is added to a queue
     *
     * @param <V> The type of the value
     * @param stateId The id of the queue
     * @param value The value added to the queue
     */
    default <V> void queuePushChange(int stateId, @NonNull V value) {}

    /**
     * Save the state change when a value is removed from a queue
     *
     * @param stateId The label of the queue
     */
    default void queuePopChange(int stateId) {}

    /**
     * Save the state change when the value of a singleton is written.
     *
     * @param <V> The type of the value
     * @param stateId The id of the singleton
     * @param value The value of the singleton
     */
    default <V> void singletonUpdateChange(int stateId, @NonNull V value) {}
}
