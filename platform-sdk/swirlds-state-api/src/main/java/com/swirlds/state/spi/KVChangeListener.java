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

package com.swirlds.state.spi;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A listener that is notified when a key-value pair is added to or removed from a map. Note that
 * {@link WritableKVState} implementations do not support null values, so neither does this listener.
 *
 * @param <K> The type of the key
 * @param <V> The type of the value
 */
public interface KVChangeListener<K, V> {
    /**
     * Called when an entry is added in to a map.
     *
     * @param key The key added to the map
     * @param value The value added to the map
     */
    void mapUpdateChange(@NonNull K key, @NonNull V value);

    /**
     * Called when an entry is removed from a map.
     *
     * @param key The key removed from the map
     */
    void mapDeleteChange(@NonNull K key);
}
