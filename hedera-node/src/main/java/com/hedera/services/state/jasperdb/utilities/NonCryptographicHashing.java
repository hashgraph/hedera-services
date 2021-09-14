package com.hedera.services.state.jasperdb.utilities;


/**
 * This class contains a collection of methods for hashing basic data types.
 * Hashes are not cryptographically secure, and are intended to be used when
 * implementing {@link Object#hashCode()}.
 */
@SuppressWarnings("unused")
public final class NonCryptographicHashing {

    /**
     * Generates a non-cryptographic 32-bit hash for a 1 long.
     *
     * @param x0
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0) {

        return (int) perm64(x0);
    }

    /**
     * Generates a non-cryptographic 32-bit hash for 2 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1) {
        return (int) perm64(perm64(
                x0) ^ x1);
    }

    /**
     * Generates a non-cryptographic 32-bit hash for 3 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2) {
        return (int) perm64(perm64(perm64(
                x0) ^ x1) ^ x2);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 4 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @param x3
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2,
            final long x3) {
        return (int) perm64(perm64(perm64(perm64(
                x0) ^ x1) ^ x2) ^ x3);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 5 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @param x3
     * 		a long
     * @param x4
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2,
            final long x3,
            final long x4) {
        return(int)perm64(perm64(perm64(perm64(perm64(x0) ^ x1) ^ x2) ^ x3) ^ x4);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 6 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @param x3
     * 		a long
     * @param x4
     * 		a long
     * @param x5
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2,
            final long x3,
            final long x4,
            final long x5) {
        return (int) perm64(perm64(perm64(perm64(perm64(perm64(
                perm64(1) ^ x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 7 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @param x3
     * 		a long
     * @param x4
     * 		a long
     * @param x5
     * 		a long
     * @param x6
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2,
            final long x3,
            final long x4,
            final long x5,
            final long x6) {
        return (int) perm64(perm64(perm64(perm64(perm64(perm64(perm64(
                x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 8 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @param x3
     * 		a long
     * @param x4
     * 		a long
     * @param x5
     * 		a long
     * @param x6
     * 		a long
     * @param x7
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2,
            final long x3,
            final long x4,
            final long x5,
            final long x6,
            final long x7) {
        return (int) perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(
                x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6) ^ x7);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 9 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @param x3
     * 		a long
     * @param x4
     * 		a long
     * @param x5
     * 		a long
     * @param x6
     * 		a long
     * @param x7
     * 		a long
     * @param x8
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2,
            final long x3,
            final long x4,
            final long x5,
            final long x6,
            final long x7,
            final long x8) {
        return (int) perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(
                x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6) ^ x7) ^ x8);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 10 longs.
     *
     * @param x0
     * 		a long
     * @param x1
     * 		a long
     * @param x2
     * 		a long
     * @param x3
     * 		a long
     * @param x4
     * 		a long
     * @param x5
     * 		a long
     * @param x6
     * 		a long
     * @param x7
     * 		a long
     * @param x8
     * 		a long
     * @param x9
     * 		a long
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final long x0,
            final long x1,
            final long x2,
            final long x3,
            final long x4,
            final long x5,
            final long x6,
            final long x7,
            final long x8,
            final long x9) {
        return (int) perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(
                x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6) ^ x7) ^ x8) ^ x9);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for an array of longs.
     *
     * @param x
     * 		an array of longs
     * @return a non-cryptographic integer hash
     */
    public static int hash32(final long... x) {
        long t = 0;
        for (final long l : x) {
            t = perm64(t ^ l);
        }
        return (int) t;
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 1 long.
     *
     * @param x0
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0) {
        return hash32((long) x0);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 2 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1) {
        return hash32((long) x0, x1);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 3 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2) {
        return hash32((long) x0, x1, x2);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 4 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @param x3
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2,
            final int x3) {
        return hash32((long) x0, x1, x2, x3);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 5 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @param x3
     * 		an int
     * @param x4
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2,
            final int x3,
            final int x4) {
        return hash32((long) x0, x1, x2, x3, x4);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 6 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @param x3
     * 		an int
     * @param x4
     * 		an int
     * @param x5
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2,
            final int x3,
            final int x4,
            final int x5) {
        return hash32((long) x0, x1, x2, x3, x4, x5);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 7 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @param x3
     * 		an int
     * @param x4
     * 		an int
     * @param x5
     * 		an int
     * @param x6
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2,
            final int x3,
            final int x4,
            final int x5,
            final int x6) {
        return hash32((long) x0, x1, x2, x3, x4, x5, x6);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 8 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @param x3
     * 		an int
     * @param x4
     * 		an int
     * @param x5
     * 		an int
     * @param x6
     * 		an int
     * @param x7
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2,
            final int x3,
            final int x4,
            final int x5,
            final int x6,
            final int x7) {
        return hash32((long) x0, x1, x2, x3, x4, x5, x6, x7);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 9 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @param x3
     * 		an int
     * @param x4
     * 		an int
     * @param x5
     * 		an int
     * @param x6
     * 		an int
     * @param x7
     * 		an int
     * @param x8
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2,
            final int x3,
            final int x4,
            final int x5,
            final int x6,
            final int x7,
            final int x8) {
        return hash32((long) x0, x1, x2, x3, x4, x5, x6, x7, x8);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for a 10 longs.
     *
     * @param x0
     * 		an int
     * @param x1
     * 		an int
     * @param x2
     * 		an int
     * @param x3
     * 		an int
     * @param x4
     * 		an int
     * @param x5
     * 		an int
     * @param x6
     * 		an int
     * @param x7
     * 		an int
     * @param x8
     * 		an int
     * @param x9
     * 		an int
     * @return a non-cryptographic integer hash
     */
    public static int hash32(
            final int x0,
            final int x1,
            final int x2,
            final int x3,
            final int x4,
            final int x5,
            final int x6,
            final int x7,
            final int x8,
            final int x9) {
        return hash32((long) x0, x1, x2, x3, x4, x5, x6, x7, x8, x9);
    }

    /**
     * Generates a non-cryptographic 32 bit hash for an array of integers.
     *
     * @param x
     * 		an array of integers
     * @return a non-cryptographic integer hash
     */
    public static int hash32(final int... x) {
        long t = 0;
        for (final int j : x) {
            t = perm64(t ^ j);
        }
        return (int) t;
    }

    /**
     * A permutation (invertible function) on 64 bits.
     * The constants were found by automated search, to optimize avalanche.
     * This means that for a random number x, flipping bit i of x has about
     * a 50% chance of flipping bit j in perm64(x). And this is close to 50%
     * for every choice of (i,j).
     */
    public static long perm64(long x) {
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }
}
