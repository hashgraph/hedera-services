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

package com.swirlds.common.utility;

/**
 * A utility class for passing a reference to an object.
 * Cheaper than using an atomic reference when atomicity is not needed.
 *
 * @param <V>
 * 		the type of the value being passed
 */
public class ValueReference<V> {

    private V value;

    /**
     * Create a new ValueReference with an initial value of null.
     */
    public ValueReference() {}

    /**
     * Create a new ValueReference with an initial value.
     *
     * @param value
     * 		the initial value
     */
    public ValueReference(final V value) {
        this.value = value;
    }

    /**
     * Get the value.
     */
    public V getValue() {
        return value;
    }

    /**
     * Set the value.
     */
    public void setValue(final V value) {
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[" + (value == null ? null : value) + "]";
    }
}
