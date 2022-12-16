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

import com.hedera.node.app.spi.state.ReadableState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("rawtypes")
public class MapReadableStates implements ReadableStates {
    private final Map<String, ReadableState> states;

    public MapReadableStates(ReadableState... states) {
        this.states = new HashMap<>();
        for (final var state : states) {
            this.states.put(state.getStateKey(), state);
        }
    }

    public MapReadableStates(@NonNull final Map<String, ReadableState> states) {
        this.states = states;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <K, V> ReadableState<K, V> get(@NonNull String stateKey) {
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

    @Override
    public int size() {
        return states.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, MapReadableState> states = new HashMap<>();

        public Builder state(@NonNull final MapReadableState state) {
            this.states.put(state.getStateKey(), state);
            return this;
        }

        public MapReadableStates build() {
            return new MapReadableStates(new HashMap<>(states));
        }
    }
}
