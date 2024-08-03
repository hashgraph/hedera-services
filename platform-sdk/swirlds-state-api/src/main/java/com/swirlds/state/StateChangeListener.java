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
     * Save the state change when an entry is added in to a map.
     *
     * @param label The label of the map
     * @param key The key added to the map
     * @param value The value added to the map
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    default <K, V> void mapUpdateChange(@NonNull String label, @NonNull K key, @NonNull V value) {}

    /**
     * Save the state change when an entry is removed from a map.
     *
     * @param label The label of the map
     * @param key The key removed from the map
     * @param <K> The type of the key
     */
    default <K> void mapDeleteChange(@NonNull String label, @NonNull K key) {}

    /**
     * Save the state change when a value is added to a queue
     *
     * @param label The label of the queue
     * @param value The value added to the queue
     * @param <V> The type of the value
     */
    default <V> void queuePushChange(@NonNull String label, @NonNull V value) {}

    /**
     * Save the state change when a value is removed from a queue
     *
     * @param label The label of the queue
     */
    default void queuePopChange(@NonNull String label) {}

    /**
     * Save the state change when the value of a singleton is written.
     *
     * @param label The label of the singleton
     * @param value The value of the singleton
     * @param <V> The type of the value
     */
    default <V> void singletonUpdateChange(@NonNull String label, @NonNull V value) {}
}
