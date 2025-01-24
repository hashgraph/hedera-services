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

package com.swirlds.state.test.fixtures;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * A simple implementation of {@link ReadableKVState} backed by a
 * {@link Map}. Test code has the option of creating an instance disregarding the backing map, or by
 * supplying the backing map to use. This latter option is useful if you want to use Mockito to spy
 * on it, or if you want to pre-populate it, or use Mockito to make the map throw an exception in
 * some strange case, or in some other way work with the backing map directly.
 *
 * <p>A convenient {@link Builder} is provided to create the map (since there are no map literals in
 * Java). The {@link #builder(String)} method can be used to create the builder.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class MapReadableKVState<K, V> extends ReadableKVStateBase<K, V> {
    /** Represents the backing storage for this state */
    private final Map<K, V> backingStore;

    /**
     * Create an instance using the given map as the backing store. This is useful when you want to
     * pre-populate the map, or if you want to use Mockito to mock it or cause it to throw
     * exceptions when certain keys are accessed, etc.
     *
     * @param stateKey The state key for this state
     * @param backingStore The backing store to use
     */
    public MapReadableKVState(@NonNull final String stateKey, @NonNull final Map<K, V> backingStore) {
        super(stateKey);
        this.backingStore = Objects.requireNonNull(backingStore);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        return backingStore.get(key);
    }

    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return backingStore.keySet().iterator();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public long size() {
        return backingStore.size();
    }

    /**
     * Create a new {@link Builder} for building a {@link MapReadableKVState}. The builder has
     * convenience methods for pre-populating the map.
     *
     * @param stateKey The state key
     * @return A {@link Builder} to be used for creating a {@link MapReadableKVState}.
     * @param <K> The key type
     * @param <V> The value type
     */
    @NonNull
    public static <K, V> Builder<K, V> builder(@NonNull final String stateKey) {
        return new Builder<>(stateKey);
    }

    /**
     * A convenient builder for creating instances of {@link
     * MapReadableKVState}.
     */
    public static final class Builder<K, V> {
        private final Map<K, V> backingStore = new HashMap<>();
        private final String stateKey;

        Builder(@NonNull final String stateKey) {
            this.stateKey = stateKey;
        }

        /**
         * Add a key/value pair to the state's backing map. This is used to pre-initialize the
         * backing map. The created state will be "clean" with no modifications.
         *
         * @param key The key
         * @param value The value
         * @return a reference to this builder
         */
        @NonNull
        public Builder<K, V> value(@NonNull K key, @Nullable V value) {
            backingStore.put(key, value);
            return this;
        }

        /**
         * Builds the state.
         *
         * @return an instance of the state, preloaded with whatever key-value pairs were defined.
         */
        @NonNull
        public MapReadableKVState<K, V> build() {
            return new MapReadableKVState<>(stateKey, new HashMap<>(backingStore));
        }
    }
}
