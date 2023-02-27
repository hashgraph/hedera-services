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

package com.swirlds.common.threading.utility;

/**
 * <p>
 * Similar semantics to {@link java.util.concurrent.atomic.AtomicInteger AtomicInteger}, but for a double.
 * </p>
 *
 * <p>
 * This implementation uses locks to provide thread safety, and as a result may not be as performant
 * as the java built-in family of atomic objects.
 * </p>
 */
public class AtomicDouble {

    private double value;

    /**
     * Create a new AtomicDouble with an initial value of 0.0.
     */
    public AtomicDouble() {}

    /**
     * Create a new AtomicDouble with an initial value.
     *
     * @param initialValue
     * 		the initial value held by the double
     */
    public AtomicDouble(final double initialValue) {
        value = initialValue;
    }

    /**
     * Get the value.
     */
    public synchronized double get() {
        return value;
    }

    /**
     * Set the value.
     *
     * @param value
     * 		the value to set
     */
    public synchronized void set(final double value) {
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized String toString() {
        return Double.toString(value);
    }
}
