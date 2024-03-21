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

package com.swirlds.platform.test.fixtures.state;

import static java.util.Objects.requireNonNull;

import com.swirlds.platform.state.spi.CommittableWritableStates;
import com.swirlds.platform.state.spi.WritableKVState;
import com.swirlds.platform.state.spi.WritableKVStateBase;
import com.swirlds.platform.state.spi.WritableQueueState;
import com.swirlds.platform.state.spi.WritableQueueStateBase;
import com.swirlds.platform.state.spi.WritableSingletonState;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.platform.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of {@link WritableStates} that is useful for testing purposes and creates
 * {@link Map}-based states such as {@link MapWritableKVState}.
 *
 * <p>A convenient {@link Builder} is provided to define the set of states available.
 */
@SuppressWarnings("rawtypes")
public class MapWritableStates implements WritableStates, CommittableWritableStates {
    private final Map<String, ?> states;

    public MapWritableStates(@NonNull final Map<String, ?> states) {
        this.states = requireNonNull(states);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <K, V> WritableKVState<K, V> get(@NonNull final String stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown k/v state key " + stateKey);
        }

        return (WritableKVState<K, V>) state;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T> WritableSingletonState<T> getSingleton(@NonNull final String stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown singleton state key " + stateKey);
        }

        return (WritableSingletonState<T>) state;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <E> WritableQueueState<E> getQueue(@NonNull final String stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown queue state key " + stateKey);
        }

        return (WritableQueueState<E>) state;
    }

    @Override
    public boolean contains(@NonNull final String stateKey) {
        return states.containsKey(stateKey);
    }

    @NonNull
    @Override
    public Set<String> stateKeys() {
        return Collections.unmodifiableSet(states.keySet());
    }

    @Override
    public int size() {
        return states.size();
    }

    @Override
    public void commit() {
        states.values().forEach(state -> {
            if (state instanceof WritableKVStateBase kv) {
                kv.commit();
            } else if (state instanceof WritableSingletonStateBase singleton) {
                singleton.commit();
            } else if (state instanceof WritableQueueStateBase queue) {
                queue.commit();
            } else {
                throw new IllegalStateException(
                        "Unknown state type " + state.getClass().getName());
            }
        });
    }

    @Override
    public String toString() {
        return "MapWritableStates{" + "states=" + states + '}';
    }

    /**
     * Creates a new {@link Builder}.
     *
     * @return Gets a new {@link Builder} to use for creating a {@link MapWritableStates} instance.
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** A convenience builder */
    public static final class Builder {
        private final Map<String, Object> states = new HashMap<>();

        /**
         * Defines a new {@link MapWritableKVState} that should be available in the {@link
         * MapWritableStates} instance created by this builder.
         *
         * @param state The state to include
         * @return a reference to this builder
         */
        @NonNull
        public Builder state(@NonNull final MapWritableKVState state) {
            this.states.put(state.getStateKey(), state);
            return this;
        }

        /**
         * Defines a new {@link WritableSingletonState} that should be available in the {@link
         * MapReadableStates} instance created by this builder.
         *
         * @param state The state to include
         * @return a reference to this builder
         */
        @NonNull
        public Builder state(@NonNull final WritableSingletonState<?> state) {
            this.states.put(state.getStateKey(), state);
            return this;
        }

        /**
         * Defines a new {@link WritableQueueState} that should be available in the {@link
         * MapReadableStates} instance created by this builder.
         *
         * @param state The state to include
         * @return a reference to this builder
         */
        @NonNull
        public Builder state(@NonNull final WritableQueueState<?> state) {
            this.states.put(state.getStateKey(), state);
            return this;
        }

        /**
         * Creates and returns a new {@link MapWritableStates} instance based on the states defined
         * in this builder.
         *
         * @return The instance
         */
        @NonNull
        public MapWritableStates build() {
            return new MapWritableStates(new HashMap<>(states));
        }
    }
}
