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

import com.hedera.node.app.spi.state.WritableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class MapWritableState<K extends Comparable<K>, V> extends WritableKVStateBase<K, V> {
    private final Map<K, V> backingStore;

    public MapWritableState(@NonNull final String stateKey, @NonNull final Map<K, V> backingStore) {
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

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        return backingStore.get(key);
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        backingStore.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        backingStore.remove(key);
    }

    public static <K extends Comparable<K>, V> Builder<K, V> builder(
            @NonNull final String stateKey) {
        return new Builder<>(stateKey);
    }

    public static final class Builder<K extends Comparable<K>, V> {
        private final Map<K, V> backingStore = new HashMap<>();
        private final String stateKey;

        public Builder(@NonNull final String stateKey) {
            this.stateKey = stateKey;
        }

        public Builder<K, V> value(@NonNull K key, @Nullable V value) {
            backingStore.put(key, value);
            return this;
        }

        public MapWritableState<K, V> build() {
            return new MapWritableState<>(stateKey, new HashMap<>(backingStore));
        }
    }
}
