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

package com.hedera.node.app.service.contract.impl.utils;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

public class ConversionUtils {
    public static final long EVM_ADDRESS_LENGTH = 20L;

    /**
     * Converts a number to a long zero address.
     *
     * @param number the number to convert
     * @return the long zero address
     */
    public static Address asLongZeroAddress(final long number) {
        return Address.wrap(Bytes.wrap(asEvmAddress(number)));
    }

    /**
     * Converts a Tuweni bytes to a PBJ bytes.
     *
     * @param bytes the Tuweni bytes
     * @return the PBJ bytes
     */
    public static @NonNull com.hedera.pbj.runtime.io.buffer.Bytes tuweniToPbjBytes(@NonNull final Bytes bytes) {
        return com.hedera.pbj.runtime.io.buffer.Bytes.wrap(requireNonNull(bytes).toArrayUnsafe());
    }

    /**
     * Converts a PBJ bytes to Tuweni bytes.
     *
     * @param bytes the PBJ bytes
     * @return the Tuweni bytes
     */
    public static @NonNull Bytes pbjToTuweniBytes(@NonNull com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        if (bytes.length() == 0) {
            return Bytes.EMPTY;
        }
        return Bytes.wrap(clampedBytes(bytes, 0, Integer.MAX_VALUE));
    }

    /**
     * Converts a PBJ bytes to a Besu address.
     *
     * @param bytes the PBJ bytes
     * @return the Besu address
     * @throws IllegalArgumentException if the bytes are not 20 bytes long
     */
    public static @NonNull Address pbjToBesuAddress(@NonNull com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        return Address.wrap(Bytes.wrap(clampedBytes(bytes, 20, 20)));
    }

    /**
     * Converts a PBJ bytes to a Besu hash.
     *
     * @param bytes the PBJ bytes
     * @return the Besu hash
     * @throws IllegalArgumentException if the bytes are not 32 bytes long
     */
    public static @NonNull Hash pbjToBesuHash(@NonNull com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        return Hash.wrap(Bytes32.wrap(clampedBytes(bytes, 32, 32)));
    }

    /**
     * Converts a PBJ bytes to a Tuweni UInt256.
     *
     * @param bytes the PBJ bytes
     * @return the Tuweni bytes
     * @throws IllegalArgumentException if the bytes are more than 32 bytes long
     */
    public static @NonNull UInt256 pbjToTuweniUInt256(@NonNull com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        return (bytes.length() == 0) ? UInt256.ZERO : UInt256.fromBytes(Bytes32.wrap(clampedBytes(bytes, 0, 32)));
    }

    private static byte[] clampedBytes(
            @NonNull final com.hedera.pbj.runtime.io.buffer.Bytes bytes, final int minLength, final int maxLength) {
        final var length = Math.toIntExact(requireNonNull(bytes).length());
        if (length < minLength) {
            throw new IllegalArgumentException("Expected at least " + minLength + " bytes, got " + bytes);
        }
        if (length > maxLength) {
            throw new IllegalArgumentException("Expected at most " + maxLength + " bytes, got " + bytes);
        }
        final byte[] data = new byte[length];
        bytes.getBytes(0, data);
        return data;
    }

    private static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        copyToLeftPaddedByteArray(num, evmAddress);
        return evmAddress;
    }

    private static void copyToLeftPaddedByteArray(long value, final byte[] dest) {
        for (int i = 7, j = dest.length - 1; i >= 0; i--, j--) {
            dest[j] = (byte) (value & 0xffL);
            value >>= 8;
        }
    }
}
