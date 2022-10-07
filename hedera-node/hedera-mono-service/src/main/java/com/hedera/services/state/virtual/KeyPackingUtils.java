/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class KeyPackingUtils {
    public static final byte MISSING_KEY_SENTINEL = (byte) -1;

    private KeyPackingUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Compute number of bytes of non-zero data are there from the least significant side of an int.
     *
     * @param num the int to count non-zero bits for
     * @return the number of non-zero bytes. Minimum 1, we always write at least 1 byte even for
     *     value 0
     */
    public static byte computeNonZeroBytes(final int[] num) {
        int count = 0;
        while (count < 8 && num[count] == 0) {
            count++;
        }
        if (count == num.length) {
            return 1; // it is all zeros
        }
        final int mostSignificantNonZeroInt = num[count];
        final byte bytes = computeNonZeroBytes(mostSignificantNonZeroInt);
        return (byte) (((num.length - count - 1) * Integer.BYTES) + bytes);
    }

    /**
     * Compute number of bytes of non-zero data are there from the least significant side of an int.
     *
     * @param num the int to count non-zero bits for
     * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for
     *     value 0
     */
    static byte computeNonZeroBytes(int num) {
        if (num == 0) {
            return (byte) 1;
        }
        return (byte) Math.ceil((Integer.SIZE - Integer.numberOfLeadingZeros(num)) / 8D);
    }

    /**
     * Compute number of bytes of non-zero data are there from the least significant side of a long.
     *
     * @param num the long to count non-zero bits for
     * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for
     *     value 0
     */
    static byte computeNonZeroBytes(long num) {
        if (num == 0) {
            return (byte) 1;
        }
        return (byte) Math.ceil((Long.SIZE - Long.numberOfLeadingZeros(num)) / 8D);
    }

    static void serializePackedBytes(
            final int[] packed, final byte numNonZero, final SerializableDataOutputStream out)
            throws IOException {
        for (int b = numNonZero - 1; b >= 0; b--) {
            out.write(extractByte(packed, b));
        }
    }

    static void serializePackedBytesToBuffer(
            final int[] packed, final byte numNonZero, final ByteBuffer out) {
        for (int b = numNonZero - 1; b >= 0; b--) {
            out.put(extractByte(packed, b));
        }
    }

    static byte extractByte(final int[] packed, final int i) {
        final var j = i / Integer.BYTES;
        return (byte) (packed[packed.length - 1 - j] >> ((i - (j * Integer.BYTES)) * 8));
    }

    public static int[] asPackedInts(final byte[] data) {
        if (data == null || data.length != 32) {
            throw new IllegalArgumentException("Key data must be non-null and 32 bytes long");
        }
        return new int[] {
            data[0] << 24 | (data[1] & 255) << 16 | (data[2] & 255) << 8 | (data[3] & 255),
            data[4] << 24 | (data[5] & 255) << 16 | (data[6] & 255) << 8 | (data[7] & 255),
            data[8] << 24 | (data[9] & 255) << 16 | (data[10] & 255) << 8 | (data[11] & 255),
            data[12] << 24 | (data[13] & 255) << 16 | (data[14] & 255) << 8 | (data[15] & 255),
            data[16] << 24 | (data[17] & 255) << 16 | (data[18] & 255) << 8 | (data[19] & 255),
            data[20] << 24 | (data[21] & 255) << 16 | (data[22] & 255) << 8 | (data[23] & 255),
            data[24] << 24 | (data[25] & 255) << 16 | (data[26] & 255) << 8 | (data[27] & 255),
            data[28] << 24 | (data[29] & 255) << 16 | (data[30] & 255) << 8 | (data[31] & 255),
        };
    }

    /**
     * Deserialize packed uint256 from data source.
     *
     * @param uint256KeyNonZeroBytes the number of non-zero bytes stored for the uint
     * @param dataSource The data source to read from
     * @param reader function to read a byte from the data source
     * @param <D> type for data source, e.g. ByteBuffer or InputStream
     * @return unit256 read as an int[8]
     * @throws IOException If there was a problem reading
     */
    public static <D> int[] deserializeUint256Key(
            final byte uint256KeyNonZeroBytes,
            final D dataSource,
            final ByteReaderFunction<D> reader)
            throws IOException {
        final int[] uint256 = new int[8];
        for (int i = 7; i >= 0; i--) {
            int integer = 0;
            if (uint256KeyNonZeroBytes >= (4 + (i * Integer.BYTES))) {
                integer |= ((long) reader.read(dataSource) & 255) << 24;
            }
            if (uint256KeyNonZeroBytes >= (3 + (i * Integer.BYTES))) {
                integer |= ((long) reader.read(dataSource) & 255) << 16;
            }
            if (uint256KeyNonZeroBytes >= (2 + (i * Integer.BYTES))) {
                integer |= ((long) reader.read(dataSource) & 255) << 8;
            }
            if (uint256KeyNonZeroBytes >= (1 + (i * Integer.BYTES))) {
                integer |= ((long) reader.read(dataSource) & 255);
            }
            uint256[7 - i] = integer;
        }
        return uint256;
    }

    public static void serializePossiblyMissingKey(
            final @Nullable int[] key,
            final byte nonZeroBytes,
            final SerializableDataOutputStream out)
            throws IOException {
        if (key == null) {
            out.write(MISSING_KEY_SENTINEL);
        } else {
            out.write(nonZeroBytes);
            serializePackedBytes(key, nonZeroBytes, out);
        }
    }

    public static String readableContractStorageKey(final int[] packedEvmKey) {
        return Optional.ofNullable(packedEvmKey)
                .map(
                        k ->
                                Arrays.stream(k)
                                        .mapToObj(Integer::toHexString)
                                        .collect(Collectors.joining("")))
                .orElse("<N/A>");
    }

    /** Simple interface for a function that takes a object and returns a byte */
    @FunctionalInterface
    public interface ByteReaderFunction<T> {
        /**
         * Applies this function to the given argument.
         *
         * @param dataSource the function argument
         * @return the function result
         */
        byte read(T dataSource) throws IOException;
    }
}
