/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.simulated.config;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * A convenience class for building maps with repeating elements, keyed by {@link NodeId}s with monotonically increasing
 * longs, starting at zero.
 */
public class MapBuilder<T> {
    private final Map<NodeId, T> map = new HashMap<>();
    private T lastElement = null;
    private Long lastIndex = 0L;

    private MapBuilder() {}

    /**
     * Creates a new builder of the specified type.
     *
     * @param type the class of the type of map to build
     * @param <T>  the type of the map to build
     * @return the new builder
     */
    public static <T> @NonNull MapBuilder<T> builder(@NonNull final Class<T> type) {
        return new MapBuilder<>();
    }

    /**
     * Sets the element to put in the map
     *
     * @param e the element
     * @return {@code this}
     */
    public @NonNull MapBuilder<T> useElement(@NonNull T e) {
        lastElement = e;
        return this;
    }

    /**
     * The number of times to put the element in the map. Elements are added with node ids with monotonically increasing
     * ids.
     *
     * @param num the number of times to repeat the element
     * @return {@code this}
     */
    public @NonNull MapBuilder<T> times(final int num) {
        for (int i = 0; i < num; i++) {
            map.put(NodeId.of(lastIndex++), lastElement);
        }
        return this;
    }

    /**
     * Builds the map. If the map is empty at the time of call, the last element set is added to the map once.
     *
     * @return the map
     */
    public @NonNull Map<NodeId, T> build() {
        if (map.isEmpty()) {
            map.put(NodeId.of(lastIndex++), lastElement);
        }
        return map;
    }
}
