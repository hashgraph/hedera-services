/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.fchashmap.internal;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * This object is capable of iterating over all keys that map to a given value
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class FCOneToManyRelationIterator<K, V> implements Iterator<V> {

    private final Map<KeyIndexPair<K>, V> associationMap;
    private final K key;
    private final int endIndex;

    private int nextIndex;

    /**
     * Create a new iterator.
     *
     * @param associationMap
     * 		the map containing all associations
     * @param key
     * 		the key whose values will be iterated
     * @param startIndex
     * 		the index where iteration starts (inclusive)
     * @param endIndex
     * 		the index where iteration ends (exclusive)
     */
    public FCOneToManyRelationIterator(
            final Map<KeyIndexPair<K>, V> associationMap, final K key, final int startIndex, final int endIndex) {

        this.associationMap = associationMap;
        this.key = key;
        this.endIndex = endIndex;
        this.nextIndex = startIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return nextIndex < endIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        final int index = nextIndex;
        nextIndex++;

        final V next = associationMap.get(new KeyIndexPair<>(key, index));
        if (next == null) {
            throw new IllegalStateException("end index exceeds number of available values");
        }
        return next;
    }
}
