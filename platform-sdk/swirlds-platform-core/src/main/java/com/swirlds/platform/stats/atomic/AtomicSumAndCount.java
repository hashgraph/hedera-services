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

package com.swirlds.platform.stats.atomic;

import com.swirlds.common.metrics.atomic.AtomicIntPair;

/**
 * Atomically sums the values provided as well as counting the number of additions
 */
public class AtomicSumAndCount {
    final AtomicIntPair sumAndCount = new AtomicIntPair();

    private static double average(final int sum, final int count) {
        if (count == 0) {
            // avoid division by 0
            return 0;
        }
        return ((double) sum) / count;
    }

    /**
     * Add the value to the sum and increment the count
     *
     * @param value
     * 		the value to add
     */
    public void add(final int value) {
        sumAndCount.accumulate(value, 1);
    }

    /**
     * @return the current sum
     */
    public int getSum() {
        return sumAndCount.getLeft();
    }

    /**
     * @return the current count
     */
    public int getCount() {
        return sumAndCount.getRight();
    }

    /**
     * @return the sum divided by the count
     */
    public double average() {
        return sumAndCount.computeDouble(AtomicSumAndCount::average);
    }

    /**
     * Same as {@link #average()} but also resets the value atomically
     */
    public double averageAndReset() {
        return sumAndCount.computeDoubleAndReset(AtomicSumAndCount::average);
    }

    /**
     * Resets the value atomically
     */
    public void reset() {
        sumAndCount.reset();
    }
}
