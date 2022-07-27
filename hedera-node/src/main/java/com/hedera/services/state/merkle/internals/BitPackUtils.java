/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle.internals;

/**
 * Minimal helper class that "encodes" {@code int} values as non-negative {@code long}s in the range
 * 0 to 4_294_967_295.
 */
public class BitPackUtils {
    private static final int MAX_AUTOMATIC_ASSOCIATIONS_MASK = (1 << 16) - 1;
    private static final int ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK =
            MAX_AUTOMATIC_ASSOCIATIONS_MASK << 16;

    private static final long MASK_INT_AS_UNSIGNED_LONG = (1L << 32) - 1;
    private static final long MASK_HI_ORDER_32 = MASK_INT_AS_UNSIGNED_LONG << 32;

    public static final long MAX_NUM_ALLOWED = 0xFFFFFFFFL;

    /**
     * Returns a {@code long} whose high-order 32 bits "encode" an unsigned integer value, and whose
     * low-order 32 bits are a signed integer. For use with the {@link
     * BitPackUtils#unsignedHighOrder32From(long)} and {@link
     * BitPackUtils#signedLowOrder32From(long)} helpers below. This format can represent timestamps
     * through January 2106.
     *
     * @param seconds some number of seconds since the epoch
     * @param nanos some number of nanos after the above second
     * @return a "packed" version of these values
     */
    public static long packedTime(long seconds, int nanos) {
        assertValid(seconds);
        return seconds << 32 | (nanos & MASK_INT_AS_UNSIGNED_LONG);
    }

    /**
     * Returns a {@code long} whose high-order and low-order 32 bits both "encode" an unsigned
     * integer value. For use with the {@link BitPackUtils#unsignedHighOrder32From(long)} and {@link
     * BitPackUtils#unsignedLowOrder32From(long)}.
     *
     * @param a some number in the range [0, MAX_NUM_ALLOWED]
     * @param b some other number in the range [0, MAX_NUM_ALLOWED]
     * @return a "packed" version of these values
     * @throws IllegalArgumentException if either argument is less than 0 or greater than
     *     4_294_967_295
     */
    public static long packedNums(long a, long b) {
        assertValid(a);
        assertValid(b);
        return a << 32 | b;
    }

    /**
     * Returns the high-order 32 bits of the given {@code long}, interpreted as an unsigned integer.
     *
     * @param l any long
     * @return the high-order 32 bits as an unsigned integer
     */
    public static long unsignedHighOrder32From(long l) {
        return (l & MASK_HI_ORDER_32) >>> 32;
    }

    /**
     * Returns the low-order 32 bits of the given {@code long}, interpreted as an unsigned integer.
     *
     * @param l any long
     * @return the low-order 32 bits as an unsigned integer
     */
    public static long unsignedLowOrder32From(long l) {
        return l & MASK_INT_AS_UNSIGNED_LONG;
    }

    /**
     * Returns the low-order 32 bits of the given {@code long} as a signed integer.
     *
     * @param packedTime any long
     * @return the low-order 32 bits as a signed integer
     */
    public static int signedLowOrder32From(long packedTime) {
        return (int) (packedTime & MASK_INT_AS_UNSIGNED_LONG);
    }

    /**
     * Returns the positive long represented by the given integer code.
     *
     * @param code an int to interpret as unsigned
     * @return the corresponding positive long
     */
    public static long numFromCode(int code) {
        return code & MASK_INT_AS_UNSIGNED_LONG;
    }

    /**
     * Returns the int representing the given positive long.
     *
     * @param num a positive long
     * @return the corresponding integer code
     */
    public static int codeFromNum(long num) {
        assertValid(num);
        return (int) num;
    }

    /**
     * Throws an exception if the given long is not a number in the allowed range.
     *
     * @param num the long to check
     * @throws IllegalArgumentException if the argument is less than 0 or greater than 4_294_967_295
     */
    public static void assertValid(long num) {
        if (num < 0 || num > MAX_NUM_ALLOWED) {
            throw new IllegalArgumentException("Serial number " + num + " out of range!");
        }
    }

    /**
     * Returns a {@code int} whose higher-order 16 bits "encode" an unsigned int value which
     * represent the already used AutomaticAssociations of an account and lower-order 16 bits
     * "encode" an unsigned int value which represent the maximum allowed AutomaticAssociations for
     * that account.
     *
     * @param maxAutoAssociations maximum allowed automatic associations of an account.
     * @param alreadyUsedAutoAssociations already used automatic associations of an account.
     * @return metadata of
     */
    public static int buildAutomaticAssociationMetaData(
            int maxAutoAssociations, int alreadyUsedAutoAssociations) {
        return (alreadyUsedAutoAssociations << 16) | maxAutoAssociations;
    }

    /**
     * Returns the lower-order 16 bits of a given {@code int}
     *
     * @param autoAssociationMetadata any int
     * @return the low-order 16-bits
     */
    public static int getMaxAutomaticAssociationsFrom(int autoAssociationMetadata) {
        return autoAssociationMetadata & MAX_AUTOMATIC_ASSOCIATIONS_MASK;
    }

    /**
     * Returns the higher-order 16 bits of a given {@code int}
     *
     * @param autoAssociationMetadata any int
     * @return the higher-order 16-bits
     */
    public static int getAlreadyUsedAutomaticAssociationsFrom(int autoAssociationMetadata) {
        return (autoAssociationMetadata & ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK) >>> 16;
    }

    /**
     * Set the lower-order 16 bits of automatic association Metadata
     *
     * @param autoAssociationMetadata metadata of already used automatic associations and max
     *     allowed automatic associations
     * @param maxAutomaticAssociations new max allowed automatic associations to set
     * @return metadata after changing the new max.
     */
    public static int setMaxAutomaticAssociationsTo(
            int autoAssociationMetadata, int maxAutomaticAssociations) {
        return (autoAssociationMetadata & ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK)
                | maxAutomaticAssociations;
    }

    /**
     * Set the higher-order 16 bits of automatic association Metadata
     *
     * @param autoAssociationMetadata metadata of already used automatic associations and max
     *     allowed automatic associations
     * @param alreadyUsedAutoAssociations new already used automatic associations to set
     * @return metadata after changing the already used associations count.
     */
    public static int setAlreadyUsedAutomaticAssociationsTo(
            int autoAssociationMetadata, int alreadyUsedAutoAssociations) {
        return (alreadyUsedAutoAssociations << 16)
                | getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
    }

    /**
     * Checks if the given long is not a number in the allowed range
     *
     * @param num given long number to check
     * @return true if valid, else returns false
     */
    public static boolean isValidNum(long num) {
        return num >= 0 && num <= MAX_NUM_ALLOWED;
    }

    BitPackUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
