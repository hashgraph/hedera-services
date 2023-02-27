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

package com.swirlds.demo.virtualmerkle.random;

import org.apache.commons.math3.random.RandomDataGenerator;

/**
 * Reasons why we are using a third-party generator:
 * The API from Random to generate and accept long values uses 48 bits.
 * The API from Random does not generate longs inside an interval.
 * The ThreadLocalRandom does not accept a seed.
 *
 * Reasons why this wrapper was created:
 * Methods that accept an interval like {@link RandomDataGenerator#nextLong(long, long)} throw if {@code lower == upper}.
 * Methods that accept an interval include the upper limit as a possible outcome.
 * There is no nextByte method inside the {@link RandomDataGenerator} class.
 */
public final class PTTRandom {

    private final RandomDataGenerator random;

    /**
     * Create a new {@link PTTRandom} instance.
     */
    public PTTRandom() {
        this.random = new RandomDataGenerator();
    }

    /**
     * Creates a new {@link PTTRandom} instance that will generate random values
     * taking into consideration the given {@code seed}.
     *
     * @param seed
     * 		The seed used to generate random values.
     */
    public PTTRandom(final long seed) {
        this.random = new RandomDataGenerator();
        random.reSeed(seed);
    }

    /**
     * Generate a random {@code double} value.
     *
     * @return a random {@code double} value from the open interval (0,1).
     */
    public double nextDouble() {
        return random.nextUniform(0, 1);
    }

    /**
     * Generate a random {@code int} value less than {@code upper}.
     *
     * @param upper
     * 		The limit of the interval [0,upper)
     * @return A random {@code int} from the interval [0,upper)
     */
    public int nextInt(final int upper) {
        return random.nextInt(0, upper - 1);
    }

    /**
     * Generate a random {@code long} value.
     *
     * @return A random {@code long} from the interval [0,Long.MAX_VALUE)
     */
    public long nextLong() {
        return random.nextLong(0, Long.MAX_VALUE);
    }

    /**
     * Generate a random {@code long} value from the interval [lower,upper).
     *
     * Note: When {@code lower == upper} is true, this method returns {@code lowexitr}:q
     * .
     *
     * @param lower
     * 		The lower boundary for the interval.
     * @param upper
     * 		The upper boundary for the interval.
     * @return A random {@code long} from the interval [lower,upper)
     */
    public long nextLong(final long lower, final long upper) {
        if (lower == upper || lower == upper - 1) {
            return lower;
        }
        return random.nextLong(lower, upper - 1);
    }

    /**
     * Generate a random {@code byte} value.
     *
     * @return A random {@code byte} value from the interval [0,Byte.MAX_VALUE).
     */
    public byte nextByte() {
        return (byte) random.nextInt(0, Byte.MAX_VALUE);
    }

    /**
     * Generate a random {@code int} value from the interval [lower,upper).
     *
     * @param lower
     * 		The lower boundary for the interval.
     * @param upper
     * 		The upper boundary for the interval.
     * @return A random {@code int} value from the interval [lower, upper)
     */
    public int nextInt(final int lower, final int upper) {
        if (lower == upper - 1) {
            return lower;
        }
        return random.nextInt(lower, upper - 1);
    }
}
