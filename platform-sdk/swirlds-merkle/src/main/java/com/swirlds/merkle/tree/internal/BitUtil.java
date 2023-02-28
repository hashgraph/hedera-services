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

package com.swirlds.merkle.tree.internal;

public final class BitUtil {

    private static final int BITS_LIMIT = 62;

    private BitUtil() {}

    /**
     * Finds b = leftmost 1 bit in size (assuming size &gt; 1)
     *
     * @param value
     * 		&gt; 1
     * @return leftmost 1 bit
     */
    public static long findLeftMostBit(final long value) {
        if (value == 0) {
            return 0;
        }

        long leftMostBit = 1L << BITS_LIMIT;
        while ((value & leftMostBit) == 0) {
            leftMostBit >>= 1;
        }

        return leftMostBit;
    }
}
