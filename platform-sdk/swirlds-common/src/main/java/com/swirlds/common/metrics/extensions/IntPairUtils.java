/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.extensions;

import com.swirlds.common.metrics.atomic.AtomicIntPair;

/**
 * Utility methods for working with {@link AtomicIntPair}
 */
public final class IntPairUtils {
    private IntPairUtils() {}

    /**
     * An implementation of a {@link com.swirlds.common.metrics.IntegerAccumulator} that does not change the value
     */
    public static int noChangeAccumulator(final int currentValue, final int ignored) {
        return currentValue;
    }

    /**
     * Extract the left integer from a long
     * @param pair the long to extract from
     * @return the left integer
     */
    public static int extractLeft(final long pair) {
        return (int) (pair >> AtomicIntPair.INT_BITS);
    }

    /**
     * Extract the right integer from a long
     * @param pair the long to extract from
     * @return the right integer
     */
    public static int extractRight(final long pair) {
        return (int) pair;
    }

    /**
     * Combine the two integers into a single long
     * @param left the left integer
     * @param right the right integer
     * @return the combined long
     */
    public static long combine(final int left, final int right) {
        return (((long) left) << AtomicIntPair.INT_BITS) | (right & 0xffffffffL);
    }
}
