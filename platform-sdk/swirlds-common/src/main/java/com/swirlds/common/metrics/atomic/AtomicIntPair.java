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

import static com.swirlds.common.metrics.extensions.IntPairUtils.*;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.ToDoubleBiFunction;

/**
 * Holds two integers that can be updated atomically
 */
public class AtomicIntPair {
    public static final int INT_BITS = 32;
    private static final int RESET_VALUE = 0;
    private final AtomicLong container;
    private final LongBinaryOperator operator;

    /**
     * Uses default accumulator method {@link Integer#sum(int, int)}
     */
    public AtomicIntPair() {
        this(Integer::sum, Integer::sum);
    }

    /**
     * @param leftAccumulator  the method that will be used to calculate the new value for the left integer when
     *                         {@link #accumulate(int, int)} is called
     * @param rightAccumulator the method that will be used to calculate the new value for the right integer when
     *                         {@link #accumulate(int, int)} is called
     */
    public AtomicIntPair(final IntBinaryOperator leftAccumulator, final IntBinaryOperator rightAccumulator) {
        operator = (current, supplied) -> {
            final int left = leftAccumulator.applyAsInt(extractLeft(current), extractLeft(supplied));
            final int right = rightAccumulator.applyAsInt(extractRight(current), extractRight(supplied));
            return combine(left, right);
        };
        this.container = new AtomicLong(RESET_VALUE);
    }

    /**
     * Update the integers with the provided values. The update will be done by the accumulator method provided in the
     * constructor
     *
     * @param leftValue  the value provided to the left integer
     * @param rightValue the value provided to the left integer
     */
    public void accumulate(final int leftValue, final int rightValue) {
        container.accumulateAndGet(combine(leftValue, rightValue), operator);
    }

    /**
     * @return the current value of the left integer
     */
    public int getLeft() {
        return extractLeft(container.get());
    }

    /**
     * @return the current value of the right integer
     */
    public int getRight() {
        return extractRight(container.get());
    }

    /**
     * Compute a double value based on the input of the integer pair
     *
     * @param compute the method to compute the double
     * @return the double computed
     */
    public double computeDouble(final ToDoubleBiFunction<Integer, Integer> compute) {
        final long twoInts = container.get();
        return compute.applyAsDouble(extractLeft(twoInts), extractRight(twoInts));
    }

    /**
     * Same as {@link #computeDouble(ToDoubleBiFunction)} but also atomically resets the integers to the initial value
     */
    public double computeDoubleAndReset(final ToDoubleBiFunction<Integer, Integer> compute) {
        return computeDoubleAndSet(compute, RESET_VALUE, RESET_VALUE);
    }

    /**
     * Atomically computes a double using the provided function and sets the values to the ones provided
     *
     * @param compute the compute function
     * @param left    the left value to set
     * @param right   the right value to set
     * @return the double computed
     */
    public double computeDoubleAndSet(
            final ToDoubleBiFunction<Integer, Integer> compute, final int left, final int right) {
        final long twoInts = container.getAndSet(combine(left, right));
        return compute.applyAsDouble(extractLeft(twoInts), extractRight(twoInts));
    }

    /**
     * Sets the values of the two ints atomically
     *
     * @param left  the left value to set
     * @param right the right value to set
     */
    public void set(final int left, final int right) {
        container.set(combine(left, right));
    }

    /**
     * Compute an arbitrary value based on the input of the integer pair
     *
     * @param compute the method to compute the result
     * @param <T>     the type of the result
     * @return the result
     */
    public <T> T compute(final BiFunction<Integer, Integer, T> compute) {
        final long twoInts = container.get();
        return compute.apply(extractLeft(twoInts), extractRight(twoInts));
    }

    /**
     * Same as {@link #compute(BiFunction)} but also atomically resets the integers to {@code 0}
     */
    public <T> T computeAndReset(final BiFunction<Integer, Integer, T> compute) {
        final long twoInts = container.getAndSet(RESET_VALUE);
        return compute.apply(extractLeft(twoInts), extractRight(twoInts));
    }

    /**
     * Same as {@link #compute(BiFunction)} but also atomically sets the integers to the provided values.
     */
    public <T> T computeAndSet(final BiFunction<Integer, Integer, T> compute, final int left, final int right) {
        final long twoInts = container.getAndSet(combine(left, right));
        return compute.apply(extractLeft(twoInts), extractRight(twoInts));
    }

    /**
     * Resets the integers to the initial value
     */
    public void reset() {
        container.getAndSet(RESET_VALUE);
    }
}
