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

package com.swirlds.common.test.fixtures;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Random;

public class RandomUtils {

    private static final Random RANDOM = new SecureRandom();

    public static String randomString(final Random random, final int length) {
        final int LEFT_LIMIT = 48; // numeral '0'
        final int RIGHT_LIMIT = 122; // letter 'z'

        return random.ints(LEFT_LIMIT, RIGHT_LIMIT + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public static String randomIp(final Random r) {
        return r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256);
    }

    public static long randomPositiveLong(final Random random, final long maxValue) {
        return random.longs(1, 1, maxValue).findFirst().orElseThrow();
    }

    public static long randomPositiveLong(final Random random) {
        return randomPositiveLong(random, Long.MAX_VALUE);
    }

    public static Hash randomHash() {
        return randomHash(new Random());
    }

    public static Hash randomHash(final Random random) {
        return new Hash(randomHashBytes(random), DigestType.SHA_384);
    }

    public static byte[] randomHashBytes(final Random random) {
        return randomByteArray(random, DigestType.SHA_384.digestLength());
    }

    /**
     * Get a random signature (doesn't actually sign anything, just random bytes.
     */
    public static Signature randomSignature(final Random random) {
        return new Signature(SignatureType.RSA, randomByteArray(random, SignatureType.RSA.signatureLength()));
    }

    public static byte[] randomByteArray(final Random random, final int size) {
        final byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    public static Instant randomInstant(final Random random) {
        return Instant.ofEpochMilli(randomPositiveLong(random, 2000000000000L));
    }

    public static boolean randomBooleanWithProbability(final Random random, final double trueProbability) {
        return random.nextDouble() < trueProbability;
    }

    public static ResettableRandom getRandomPrintSeed() {
        return getRandom(true);
    }

    /**
     * A variation of {@link #getRandomPrintSeed()} that takes a hard coded seed. Handy for
     * debugging a test, simply copy a seed into the {@link #getRandomPrintSeed()} method.
     *
     * @param seed
     * 		the seed to use
     * @return a new random object
     */
    public static ResettableRandom getRandomPrintSeed(final long seed) {
        System.out.println("Random seed: " + seed + "L");
        return new ResettableRandom(seed);
    }

    public static ResettableRandom getRandom() {
        return getRandom(false);
    }

    public static ResettableRandom getRandom(final boolean printSeed) {
        final long seed = RANDOM.nextLong();
        if (printSeed) {
            System.out.println("Random seed: " + seed + "L");
        }
        return new ResettableRandom(seed);
    }

    public static ResettableRandom initRandom(final Long seed) {
        if (seed == null) {
            return getRandomPrintSeed();
        } else {
            return new ResettableRandom(seed);
        }
    }

    /**
     * Returns a random int within 0 - Integer.MAX_VALUE
     * <p>
     * Extracted from {@code org.apache.commons.lang3.RandomUtils#nextInt()}
     *
     * @return the random integer
     * @see #nextInt(int, int)
     */
    public static int nextInt() {
        return nextInt(0, Integer.MAX_VALUE);
    }

    /**
     * Returns a random integer within the specified range.
     * <p>
     * Extracted from {@code org.apache.commons.lang3.RandomUtils#nextInt(int, int)}
     *
     * @param startInclusive
     *            the smallest value that can be returned, must be non-negative
     * @param endExclusive
     *            the upper bound (not included)
     * @throws IllegalArgumentException
     *             if {@code startInclusive > endExclusive} or if
     *             {@code startInclusive} is negative
     * @return the random integer
     */
    public static int nextInt(final int startInclusive, final int endExclusive) {
        if (endExclusive < startInclusive) {
            throw new IllegalArgumentException("Start value must be smaller or equal to end value.");
        }
        if (startInclusive < 0) {
            throw new IllegalArgumentException("Both range values must be non-negative.");
        }

        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + RANDOM.nextInt(endExclusive - startInclusive);
    }

    /**
     * Returns a random long within the specified range.
     * <p>
     * Extracted from {@code org.apache.commons.lang3.RandomUtils#nextLong(long, long)}
     *
     * @param startInclusive
     *            the smallest value that can be returned, must be non-negative
     * @param endExclusive
     *            the upper bound (not included)
     * @throws IllegalArgumentException
     *             if {@code startInclusive > endExclusive} or if
     *             {@code startInclusive} is negative
     * @return the random long
     */
    public static long nextLong(final long startInclusive, final long endExclusive) {
        if (endExclusive < startInclusive) {
            throw new IllegalArgumentException("Start value must be smaller or equal to end value.");
        }
        if (startInclusive < 0) {
            throw new IllegalArgumentException("Both range values must be non-negative.");
        }

        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + nextLong(endExclusive - startInclusive);
    }

    /**
     * Generates a {@code long} value between 0 (inclusive) and the specified
     * value (exclusive).
     * <p>
     * Extracted from {@code org.apache.commons.lang3.RandomUtils#nextLong(long)}
     *
     * @param n Bound on the random number to be returned.  Must be positive.
     * @return a random {@code long} value between 0 (inclusive) and {@code n}
     * (exclusive).
     */
    private static long nextLong(final long n) {
        long bits;
        long val;
        do {
            bits = RANDOM.nextLong() >>> 1;
            val = bits % n;
        } while (bits - val + (n - 1) < 0);

        return val;
    }

    /**
     * Returns a random long within 0 - Long.MAX_VALUE
     * <p>
     * Extracted from {@code org.apache.commons.lang3.RandomUtils#nextLong()}
     *
     * @return the random long
     * @see #nextLong(long, long)
     */
    public static long nextLong() {
        return nextLong(Long.MAX_VALUE);
    }
}
