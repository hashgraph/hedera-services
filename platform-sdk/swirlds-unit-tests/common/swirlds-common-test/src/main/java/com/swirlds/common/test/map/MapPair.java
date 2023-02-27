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

package com.swirlds.common.test.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.FastCopyable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Holds a pair of maps, one that is being tested and another to use for checks
 *
 * @param <M>
 * 		map type
 * @param <K>
 * 		key type
 * @param <V>
 * 		value type
 */
public class MapPair<M extends Map<K, V> & FastCopyable, K, V> {
    private final M fc;
    private final Map<K, V> control;

    public MapPair(M fc, Map<K, V> control) {
        this.fc = fc;
        this.control = control;
    }

    /**
     * @return a copy of this map pair
     */
    public MapPair<M, K, V> copy() {
        return new MapPair<>((M) fc.copy(), new HashMap<>(control));
    }

    /**
     * Puts the values into both maps
     *
     * @param key
     * 		the key
     * @param value
     * 		the value
     */
    public void put(K key, V value) {
        fc.put(key, value);
        control.put(key, value);
    }

    public void remove(K key) {
        fc.remove(key);
        control.remove(key);
    }

    public M getFc() {
        return fc;
    }

    public Map<K, V> getControl() {
        return control;
    }

    public void assertValuesEqual() {
        HashSet<V> set = new HashSet<>(fc.values());
        assertEquals(control.size(), set.size());
        control.values().forEach(v -> assertTrue(set.contains(v)));
    }

    public void assertKeySetEquals() {
        HashSet<K> set = new HashSet<>(fc.keySet());
        assertEquals(control.size(), set.size());
        control.keySet().forEach(v -> assertTrue(set.contains(v)));
    }

    public void assertEntrySetEquals() {
        HashMap<K, V> map = new HashMap<>();
        fc.entrySet().forEach(e -> map.put(e.getKey(), e.getValue()));
        assertEquals(control.size(), map.size());
        control.forEach((k, v) -> {
            assertTrue(map.containsKey(k));
            assertEquals(v, map.get(k));
        });
    }

    public void assertContainsValueConsistent() {
        control.values().forEach(v -> assertTrue(fc.containsValue(v)));
    }
}
