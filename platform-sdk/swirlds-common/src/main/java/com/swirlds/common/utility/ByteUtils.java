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

package com.swirlds.common.utility;

import static com.swirlds.common.utility.Units.BYTES_PER_INT;
import static com.swirlds.common.utility.Units.BYTES_PER_LONG;
import static com.swirlds.common.utility.Units.BYTES_PER_SHORT;

/**
 * Utility class for byte operations
 */
public final class ByteUtils {

    private ByteUtils() {}

    /**
     * Return a long derived from the 8 bytes data[position]...data[position+7], big endian. If the byte array
     * is not long enough, zeros are substituted for the missing bytes.
     *
     * @param data
     * 		an array of bytes
     * @param position
     * 		the first byte in the array to use
     * @return the 8 bytes starting at position, converted to a long, big endian
     */
    public static long byteArrayToLong(final byte[] data, final int position) {
        if (data.length > position + 8) {
            // Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
            return ((data[position] & 0xffL) << (8 * 7))
                    + ((data[position + 1] & 0xffL) << (8 * 6))
                    + ((data[position + 2] & 0xffL) << (8 * 5))
                    + ((data[position + 3] & 0xffL) << (8 * 4))
                    + ((data[position + 4] & 0xffL) << (8 * 3))
                    + ((data[position + 5] & 0xffL) << (8 * 2))
                    + ((data[position + 6] & 0xffL) << (8))
                    + (data[position + 7] & 0xffL);
        } else {
            // There isn't enough data to fill the long, so pad with zeros.
            long result = 0;
            for (int offset = 0; offset < 8; offset++) {
                final int index = position + offset;
                if (index >= data.length) {
                    break;
                }
                result += (data[index] & 0xffL) << (8 * (7 - offset));
            }
            return result;
        }
    }

    /**
     * Write the long value into 8 bytes of an array, big endian.
     *
     * @param value
     * 		the value to write
     * @return a byte array
     */
    public static byte[] longToByteArray(final long value) {
        final byte[] data = new byte[BYTES_PER_LONG];
        longToByteArray(value, data, 0);
        return data;
    }

    /**
     * Write the long value into 8 bytes of the array, starting at a given position pos, big endian.
     *
     * @param value
     * 		the value to write
     * @param data
     * 		the array to write to
     * @param position
     * 		write to 8 bytes starting with the byte with this index
     */
    public static void longToByteArray(final long value, final byte[] data, final int position) {
        // Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
        data[position] = (byte) (value >> (8 * 7));
        data[position + 1] = (byte) (value >> (8 * 6));
        data[position + 2] = (byte) (value >> (8 * 5));
        data[position + 3] = (byte) (value >> (8 * 4));
        data[position + 4] = (byte) (value >> (8 * 3));
        data[position + 5] = (byte) (value >> (8 * 2));
        data[position + 6] = (byte) (value >> (8));
        data[position + 7] = (byte) value;
    }

    /**
     * Return an int derived from the 4 bytes data[position]...data[position+3], big endian.
     *
     * @param data
     * 		an array of bytes
     * @param position
     * 		the first byte in the array to use
     * @return the 4 bytes starting at position, converted to an int, big endian
     */
    public static int byteArrayToInt(final byte[] data, final int position) {
        if (data.length > position + 4) {
            // Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
            return ((data[position] & 0xff) << (8 * 3))
                    + ((data[position + 1] & 0xff) << (8 * 2))
                    + ((data[position + 2] & 0xff) << (8))
                    + (data[position + 3] & 0xff);
        } else {
            // There isn't enough data to fill the int, so pad with zeros.
            int result = 0;
            for (int offset = 0; offset < 4; offset++) {
                final int index = position + offset;
                if (index >= data.length) {
                    break;
                }
                result += (data[index] & 0xff) << (8 * (3 - offset));
            }
            return result;
        }
    }

    /**
     * Write the int value into 4 bytes of an array, big endian.
     *
     * @param value
     * 		the value to write
     */
    public static byte[] intToByteArray(final int value) {
        final byte[] data = new byte[BYTES_PER_INT];
        intToByteArray(value, data, 0);
        return data;
    }

    /**
     * Write the int value into 4 bytes of the array, starting at a given position pos, big endian.
     *
     * @param value
     * 		the value to write
     * @param data
     * 		the array to write to
     * @param position
     * 		write to 4 bytes starting with the byte with this index
     */
    public static void intToByteArray(final int value, final byte[] data, final int position) {
        // Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
        data[position] = (byte) (value >> (8 * 3));
        data[position + 1] = (byte) (value >> (8 * 2));
        data[position + 2] = (byte) (value >> (8));
        data[position + 3] = (byte) value;
    }

    /**
     * Return a short derived from the 2 bytes data[position] and data[position+1], big endian.
     *
     * @param data
     * 		an array of bytes
     * @param position
     * 		the first byte in the array to use
     * @return the 2 bytes starting at position, converted to a short, big endian
     */
    public static short byteArrayToShort(final byte[] data, final int position) {
        if (data.length > position + 2) {
            // Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
            return (short) (((data[position] & 0xff) << 8) + (data[position + 1] & 0xff));
        } else {
            // There isn't enough data to fill the short, so pad with zeros.
            short result = 0;
            for (int offset = 0; offset < 2; offset++) {
                final int index = position + offset;
                if (index >= data.length) {
                    break;
                }
                result += (data[index] & 0xff) << (8 * (1 - offset));
            }
            return result;
        }
    }

    /**
     * Write the short value into 2 bytes of an array, big endian.
     *
     * @param value
     * 		the value to write
     */
    public static byte[] shortToByteArray(final short value) {
        final byte[] data = new byte[BYTES_PER_SHORT];
        shortToByteArray(value, data, 0);
        return data;
    }

    /**
     * Write the short value into 2 bytes of the array, starting at a given position pos, big endian.
     *
     * @param value
     * 		the value to write
     * @param data
     * 		the array to write to
     * @param position
     * 		write to 2 bytes starting with the byte with this index
     */
    public static void shortToByteArray(final short value, final byte[] data, final int position) {
        // Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
        data[position] = (byte) (value >> 8);
        data[position + 1] = (byte) value;
    }
}
