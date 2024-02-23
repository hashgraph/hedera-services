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

package com.swirlds.fchashmap.internal;

import java.util.Objects;

/**
 * A container for holding a key and a value. Used by {@link com.swirlds.fchashmap.FCOneToManyRelation}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class KeyValuePair<K, V> {

    private final K key;
    private final V value;

    /**
     * Create a new key value pair.
     *
     * @param key
     * 		the key, null is not supported
     * @param value
     * 		the value
     * @throws NullPointerException
     * 		if the key is null
     */
    public KeyValuePair(final K key, final V value) {
        if (key == null) {
            throw new NullPointerException("null keys are not supported");
        }
        this.key = key;
        this.value = value;
    }

    /**
     * Get the key.
     */
    public K getKey() {
        return key;
    }

    /**
     * Get the value.
     */
    public V getValue() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyValuePair<?, ?> that = (KeyValuePair<?, ?>) o;
        return key.equals(that.key) && value.equals(that.value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // Chosen to resemble Object.hashCode(Object...) but with a different prime number
        return key.hashCode() * 37 + Objects.hashCode(value.hashCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "(" + key + ", " + value + ")";
    }
}
