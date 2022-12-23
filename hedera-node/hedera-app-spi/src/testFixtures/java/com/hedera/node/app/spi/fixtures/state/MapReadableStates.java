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
package com.hedera.node.app.spi.fixtures.state;

import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;

/**
 * An implementation of {@link ReadableStates} that is useful for testing purposes and creates
 * {@link Map}-based states such as {@link MapReadableKVState}.
 *
 * <p>A convenient {@link Builder} is provided to define the set of states available.
 */
@SuppressWarnings("rawtypes")
public class MapReadableStates implements ReadableStates {
    private final Map<String, ReadableKVState> states;

    public MapReadableStates(@NonNull final Map<String, ReadableKVState> states) {
        this.states = states;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <K extends Comparable<K>, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
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
     * @return Gets a new {@link Builder} to use for creating a {@link MapReadableStates} instance.
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** A convenience builder */
    public static final class Builder {
        private final Map<String, MapReadableKVState> states = new HashMap<>();

        Builder() {}

        /**
         * Defines a new {@link MapReadableKVState} that should be available in the {@link
         * MapReadableStates} instance created by this builder.
         *
         * @param state The state to include
         * @return a reference to this builder
         */
        @NonNull
        public Builder state(@NonNull final MapReadableKVState state) {
            this.states.put(state.getStateKey(), state);
            return this;
        }

        /**
         * Creates and returns a new {@link MapReadableStates} instance based on the states defined
         * in this builder.
         *
         * @return The instance
         */
        @NonNull
        public MapReadableStates build() {
            return new MapReadableStates(new HashMap<>(states));
        }
    }
}
