/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.fixtures.state;

import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;

/**
 * An implementation of {@link WritableStates} that is useful for testing purposes and creates
 * {@link Map}-based states such as {@link MapWritableKVState}.
 *
 * <p>A convenient {@link Builder} is provided to define the set of states available.
 */
@SuppressWarnings("rawtypes")
public class MapWritableStates implements WritableStates {
    private final Map<String, WritableKVState> states;

    public MapWritableStates(@NonNull final Map<String, WritableKVState> states) {
        this.states = states;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <K extends Comparable<K>, V> WritableKVState<K, V> get(@NonNull String stateKey) {
        final var state = states.get(Objects.requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown state key " + stateKey);
        }

        return state;
    }

    @Override
    public boolean contains(@NonNull String stateKey) {
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
        private final Map<String, MapWritableKVState> states = new HashMap<>();

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
