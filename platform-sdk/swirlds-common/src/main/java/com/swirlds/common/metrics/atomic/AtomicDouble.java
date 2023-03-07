/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.atomic;

import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Double.longBitsToDouble;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * A (partial) implementation of an {@code AtomicDouble}, which allows atomic updates and
 * CAS-operations on a {@code double} value.
 * <p>
 * This implementation is inspired by the {@code AtomicDouble} implementations in JSR 166 extra and Guava.
 * <p>
 * Note: In CAS-operations, this class does not compare the {@code double}-values directly, but the
 * bits one gets by calling {@link Double#doubleToRawLongBits(double)}.
 */
public class AtomicDouble {

    private volatile long bits;

    private static final AtomicLongFieldUpdater<AtomicDouble> updater =
            AtomicLongFieldUpdater.newUpdater(AtomicDouble.class, "bits");

    /**
     * Creates a new {@code AtomicLong} with the given initial value.
     *
     * @param initialValue
     * 		the initial value
     */
    public AtomicDouble(double initialValue) {
        bits = doubleToRawLongBits(initialValue);
    }

    /**
     * Creates a new {@code AtomicDouble} with initial value {@code 0.0}.
     */
    public AtomicDouble() {}

    /**
     * Returns the current value, with memory effects as specified by
     * {@link java.lang.invoke.VarHandle#getVolatile(Object...)}.
     *
     * @return the current value
     */
    public final double get() {
        return longBitsToDouble(bits);
    }

    /**
     * Sets the value to newValue, with memory effects as specified by
     * {@link java.lang.invoke.VarHandle#setVolatile(Object...)}.
     *
     * @param newValue
     * 		the new value
     */
    public final void set(double newValue) {
        bits = doubleToRawLongBits(newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} and returns the old value.
     *
     * @param newValue
     * 		the new value
     * @return the previous value
     */
    public final double getAndSet(double newValue) {
        final long newBits = doubleToRawLongBits(newValue);
        return longBitsToDouble(updater.getAndSet(this, newBits));
    }

    /**
     * Atomically sets the value to the given updated value if the current value is bitwise equal, that is,
     * if {@link Double#doubleToRawLongBits(double)} returns the same value.
     *
     * @param expectedValue
     * 		the expected value
     * @param newValue
     * 		the new value
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public final boolean compareAndSet(double expectedValue, double newValue) {
        final long expectedBits = doubleToRawLongBits(expectedValue);
        final long newBits = doubleToRawLongBits(newValue);
        return updater.compareAndSet(this, expectedBits, newBits);
    }
}
