/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.cryptography.utils;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Static utility hashing operations */
public final class HashUtils {

    /**
     * Sha256
     */
    public static final String SHA256 = "SHA-256";

    /**
     * private constructor to ensure static access
     */
    private HashUtils() {
        // private constructor to ensure static access
    }

    /**
     * Computes SHA 256 hash
     *
     * @param message message to hash
     * @return 256-bit hash
     */
    @NonNull
    public static byte[] computeSha256(final @NonNull byte[] message) {
        Objects.requireNonNull(message, "message must not be null");
        return getHashCalculator(SHA256).append(message).hash();
    }

    /**
     * Computes a requested hash of an array of messages of the same size
     *
     * @param algorithm requested algorithm
     * @param messages message to hash
     * @return the computed hash
     */
    @NonNull
    public static byte[] computeHash(final @NonNull String algorithm, @NonNull final byte[]... messages) {
        if (Objects.requireNonNull(messages, "messages must not be null").length == 0)
            throw new IllegalArgumentException("messages must not be empty");
        HashCalculator calculator = getHashCalculator(algorithm);
        for (final byte[] element : messages) {
            calculator.append(element);
        }
        return calculator.hash();
    }

    /**
     * returns an instance that allows to calculate the requested hash of appended values
     *
     * @return a hash calculator according to the requested algorithm
     * @param algorithm the algorithm for hashing. Needs to be available in the JVM
     */
    @NonNull
    public static HashCalculator getHashCalculator(final String algorithm) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            return new HashCalculator(digest);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Could not hash message", e);
        }
    }

    /**
     * Allows to create a hash using an initialized digest.
     */
    public static class HashCalculator {
        private final MessageDigest digest;

        /**
         * Constructs a HashCalculator
         * @param digest an already initialized digest
         */
        HashCalculator(final @NonNull MessageDigest digest) {
            this.digest = Objects.requireNonNull(digest);
        }

        /**
         * Append a value to the digest
         * @param value value to append to the digest
         * @return this instance
         */
        public HashCalculator append(final int value) {
            digest.update(ByteArrayUtils.toByteArray(value));
            return this;
        }

        /**
         * Append a value to the digest
         *
         * @param value value to append to the digest
         * @return this instance
         */
        public HashCalculator append(byte[] value) {
            digest.update(value);
            return this;
        }

        /**
         * Returns the calculated hash.
         *
         * @return the calculated hash.
         */
        public byte[] hash() {
            return digest.digest();
        }
    }
}
