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

import com.swirlds.common.metrics.extensions.IntPairUtils;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.function.ToDoubleBiFunction;

/**
 * Holds two integers that can be updated atomically
 */
public class AtomicIntPair {
    public static final int INT_BITS = 32;
    private static final int RESET_VALUE = 0;
    private final AtomicLong container;
    private final LongBinaryOperator operator;
    private final LongUnaryOperator reset;

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
        this(IntPairUtils.createAccumulator(leftAccumulator, rightAccumulator), current -> RESET_VALUE);
    }

    /**
     * @param accumulator the method that will be used to calculate the new value for the integers when being updated
     * @param reset      the method that will be used to calculate the new value for the integers when being reset
     */
    public AtomicIntPair(final LongBinaryOperator accumulator, final LongUnaryOperator reset) {
        operator = accumulator;
        this.container = new AtomicLong(RESET_VALUE);
        this.reset = reset;
        reset();
    }

    /**
     * Update the integers with the provided values. The update will be done by the accumulator method provided in the
     * constructor
     *
     * @param leftValue  the value provided to the left integer
     * @param rightValue the value provided to the left integer
     */
    public void accumulate(final int leftValue, final int rightValue) {
        container.accumulateAndGet(IntPairUtils.combine(leftValue, rightValue), operator);
    }

    /**
     * @return the current value of the left integer
     */
    public int getLeft() {
        return IntPairUtils.extractLeft(container.get());
    }

    /**
     * @return the current value of the right integer
     */
    public int getRight() {
        return IntPairUtils.extractRight(container.get());
    }

    /**
     * Compute a double value based on the input of the integer pair
     *
     * @param compute the method to compute the double
     * @return the double computed
     */
    public double computeDouble(final ToDoubleBiFunction<Integer, Integer> compute) {
        final long twoInts = container.get();
        return compute.applyAsDouble(IntPairUtils.extractLeft(twoInts), IntPairUtils.extractRight(twoInts));
    }

    /**
     * Same as {@link #computeDouble(ToDoubleBiFunction)} but also atomically resets the integers to the initial value
     */
    public double computeDoubleAndReset(final ToDoubleBiFunction<Integer, Integer> compute) {
        final long twoInts = container.getAndUpdate(reset);
        return compute.applyAsDouble(IntPairUtils.extractLeft(twoInts), IntPairUtils.extractRight(twoInts));
    }

    /**
     * Sets the values of the two ints atomically
     *
     * @param left  the left value to set
     * @param right the right value to set
     */
    public void set(final int left, final int right) {
        container.set(IntPairUtils.combine(left, right));
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
        return compute.apply(IntPairUtils.extractLeft(twoInts), IntPairUtils.extractRight(twoInts));
    }

    /**
     * Same as {@link #compute(BiFunction)} but also atomically resets the integers to {@code 0}
     */
    public <T> T computeAndReset(final BiFunction<Integer, Integer, T> compute) {
        final long twoInts = container.getAndUpdate(reset);
        return compute.apply(IntPairUtils.extractLeft(twoInts), IntPairUtils.extractRight(twoInts));
    }

    /**
     * Resets the integers to the initial value
     */
    public void reset() {
        container.getAndUpdate(reset);
    }
}
