/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.test.fixtures;

import com.hedera.pbj.runtime.io.buffer.Bytes;

public class ExampleVariableKey {

    public static Bytes longToKey(final long k) {
        final int len = computeNonZeroBytes(k);
        final byte[] bytes = new byte[len];
        for (int b = len - 1, i = 0; b >= 0; b--, i++) {
            bytes[i] = (byte) (k >> (b * 8));
        }
        return Bytes.wrap(bytes);
    }

    public static long keyToLong(final Bytes key) {
        long k = 0;
        for (int i = 0; i < key.length(); i++) {
            k = (k << 8) + key.getByte(i);
        }
        return k;
    }

    /**
     * Compute number of bytes of non-zero data are there from the least significant side of a long.
     *
     * @param num the long to count non-zero bits for
     * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for
     *     value 0
     */
    static byte computeNonZeroBytes(final long num) {
        if (num == 0) {
            return (byte) 1;
        }
        return (byte) Math.ceil((double) (Long.SIZE - Long.numberOfLeadingZeros(num)) / 8D);
    }

    private ExampleVariableKey() {}
}
