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

package com.swirlds.common.internal;

import java.security.MessageDigest;
import java.time.Instant;

public abstract class HashUtils {
    /**
     * hash the given long
     *
     * @param digest
     * 		the message digest to update
     * @param n
     * 		the long value to be hashed
     */
    public static void update(MessageDigest digest, long n) {
        for (int i = 0; i < Long.BYTES; i++) {
            digest.update((byte) (n & 0xFF));
            n >>= Byte.SIZE;
        }
    }

    /**
     * hash the given int
     *
     * @param digest
     * 		the message digest to update
     * @param n
     * 		the int value to be hashed
     */
    public static void update(MessageDigest digest, int n) {
        for (int i = 0; i < Integer.BYTES; i++) {
            digest.update((byte) (n & 0xFF));
            n >>= Byte.SIZE;
        }
    }

    /**
     * hash the given array of bytes
     *
     * @param digest
     * 		the message digest to update
     * @param t
     * 		the byte array to be hashed
     * @param offset
     * 		the offset to start from in the array of bytes
     * @param length
     * 		the number of bytes to use, starting at {@code offset}
     */
    public static void update(MessageDigest digest, byte[] t, int offset, int length) {
        if (t == null) {
            update(digest, 0);
        } else {
            update(digest, t.length);
            digest.update(t, offset, length);
        }
    }

    /**
     * hash the given Instant
     *
     * @param digest
     * 		the message digest to update
     * @param i
     * 		the Instant to be hashed
     */
    public static void update(MessageDigest digest, Instant i) {
        // the instant class consists of only 2 parts, the seconds and the nanoseconds
        update(digest, i.getEpochSecond());
        update(digest, i.getNano());
    }

    /**
     * hash the given array of byte arrays
     * @param digest
     * 		the message digest to update
     * @param t
     * 		the array to be hashed
     */
    public static void update(MessageDigest digest, byte[][] t) {
        if (t == null) {
            update(digest, 0);
        } else {
            update(digest, t.length);
            for (byte[] a : t) {
                if (a == null) {
                    update(digest, 0);
                } else {
                    update(digest, a.length);
                    digest.update(a);
                }
            }
        }
    }

    /**
     * hash the given array of bytes
     * @param digest
     * 		the message digest to update
     * @param t
     * 		the array of bytes to be hashed
     */
    public static void update(MessageDigest digest, byte[] t) {
        if (t == null) {
            update(digest, 0);
        } else {
            update(digest, t.length);
            digest.update(t);
        }
    }
}
