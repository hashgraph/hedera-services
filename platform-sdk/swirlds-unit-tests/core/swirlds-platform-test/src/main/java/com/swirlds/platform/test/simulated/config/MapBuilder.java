/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import java.util.HashMap;
import java.util.Map;

/**
 * A convenience class for building maps with repeating elements, keyed by monotonically increasing longs, starting at
 * zero.
 */
public class MapBuilder<T> {
    private final Map<Long, T> map = new HashMap<>();
    private T lastElement = null;
    private Long lastIndex = 0L;

    private MapBuilder() {}

    public static <T> MapBuilder<T> builder(final Class<T> type) {
        return new MapBuilder<>();
    }

    public MapBuilder<T> useElement(T e) {
        lastElement = e;
        return this;
    }

    public MapBuilder<T> times(final int num) {
        for (int i = 0; i < num; i++) {
            map.put(lastIndex++, lastElement);
        }
        return this;
    }

    public Map<Long, T> build() {
        return map;
    }
}
