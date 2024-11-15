/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Static utility {@link Byte[]} operations */
public class ByteArrayUtils {

    /**
     * private constructor to ensure static access
     */
    private ByteArrayUtils() {
        // private constructor to ensure static access
    }

    /**
     * Converts a BigInteger into a byte array of the given size in little-endian order.
     *
     * @param value the BigInteger to convert
     * @param size  the size of the output byte array
     * @return a byte array of the specified size representing the BigInteger in little-endian order
     * @throws NullPointerException if the BigInteger is null
     * @throws IllegalArgumentException if the BigInteger cannot be represented in the specified size
     */
    @NonNull
    public static byte[] toLittleEndianBytes(@NonNull final BigInteger value, final int size) {
        byte[] bigEndianBytes =
                Objects.requireNonNull(value, "value must not be null").toByteArray();
        if (bigEndianBytes.length > size) {
            throw new IllegalArgumentException("BigInteger cannot be represented in " + size + " bytes.");
        }

        return reverseBytes(bigEndianBytes, size);
    }
    /**
     * Converts a variable number of BigInteger arguments to their byte array representations,
     * reverses each byte array, and concatenates them into a single byte array.
     *
     * @param size the desired final length of the resulting byte array
     * @param args a variable number of BigInteger arguments
     * @return a concatenated byte array containing the reversed byte array representations of each BigInteger
     */
    @NonNull
    public static byte[] toLittleEndianBytes(final int size, @NonNull final BigInteger... args) {
        int totalSize = 0;
        ByteBuffer buffer = ByteBuffer.allocate(size);

        for (BigInteger arg : args) {
            final byte[] argByteArrays = arg.toByteArray();
            totalSize += argByteArrays.length;

            if (totalSize > size) {
                break;
            }

            buffer.put(reverseBytes(argByteArrays, argByteArrays.length));
        }
        if (totalSize > size) {
            throw new IllegalArgumentException("BigInteger cannot be represented in " + size + " bytes.");
        }

        return buffer.array();
    }

    /**
     * Splits a byte array into chunks of a given size, reverses each chunk, and converts each reversed chunk to a BigInteger.
     *
     * @param byteArray the byte array to be split and processed
     * @param chunkSize the size of each chunk
     * @return a list of BigIntegers created from the reversed chunks of the byte array
     * @throws IllegalArgumentException if the byte array length is not divisible by the chunk size
     */
    @NonNull
    public static List<BigInteger> toBigIntegers(final @NonNull byte[] byteArray, int chunkSize) {
        if (byteArray.length % chunkSize != 0) {
            throw new IllegalArgumentException("Byte array length must be divisible by the chunk size.");
        }

        List<BigInteger> bigIntegers = new ArrayList<>();

        for (int i = 0; i < byteArray.length; i += chunkSize) {
            byte[] chunk = Arrays.copyOfRange(byteArray, i, i + chunkSize);
            BigInteger bigInteger = new BigInteger(reverseBytes(chunk, chunkSize));
            bigIntegers.add(bigInteger);
        }

        return bigIntegers;
    }
    /**
     * Converts a little-endian byte array into a BigInteger.
     *
     * @param littleEndianBytes the byte array in little-endian order
     * @return the corresponding BigInteger
     */
    @NonNull
    public static BigInteger fromLittleEndianBytes(@NonNull final byte[] littleEndianBytes) {
        Objects.requireNonNull(littleEndianBytes, "littleEndianBytes must not be null");
        return new BigInteger(reverseBytes(littleEndianBytes, littleEndianBytes.length));
    }

    /**
     * Reverses the order of bytes in the array.
     *
     * @param input the byte array to reverse
     * @param size the end size of the array
     * @return the reversed byte array
     */
    @NonNull
    private static byte[] reverseBytes(@NonNull byte[] input, final int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(input);
        buffer.flip();

        final byte[] output = new byte[size];

        for (int i = 0; i < input.length; i++) {
            output[input.length - i - 1] = input[i];
        }
        return output;
    }

    /**
     * Transforms an integer value into a byte array
     * @param value  the integer to transform
     * @return the resulting byte array
     */
    @NonNull
    public static byte[] toByteArray(final int value) {
        return new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    /**
     * A utility class for serializing various data types into a byte array.
     */
    public static class Serializer {
        private final ByteArrayOutputStream bo;
        private final DataOutputStream os;

        /**
         * Constructs a utility class for serializing various data types into a byte array.
         */
        public Serializer() {
            this.bo = new ByteArrayOutputStream();
            this.os = new DataOutputStream(bo);
        }

        /**
         * Adds the content of a supplied byte array to the serializer.
         *
         * @param byteProvider A supplier that provides the byte array to be added.
         * @return The Serializer instance for method chaining.
         */
        @NonNull
        public Serializer put(@NonNull final Supplier<byte[]> byteProvider) {
            var z = byteProvider.get();

            try {
                os.write(z);
            } catch (IOException e) {
                throw new IllegalStateException("Could not write", e);
            }
            return this;
        }

        /**
         * Serializes a list of elements using the provided serializer function for each element.
         *It assumes that the serialized version of each element in the list have the same size.
         *
         * @param <T> The type of elements in the list.
         * @param list The list of elements to serialize.
         * @param serializer A function that serializes each element into a byte array.
         * @return The Serializer instance for method chaining.
         */
        @NonNull
        public <T> Serializer putListSameSize(
                @NonNull final List<T> list, @NonNull final Function<T, byte[]> serializer) {
            Objects.requireNonNull(serializer);
            for (var entry : list) {
                try {
                    os.write(serializer.apply(entry));
                } catch (IOException e) {
                    throw new IllegalStateException("Could not write", e);
                }
            }
            return this;
        }

        /**
         * Adds a byte value to the serializer.
         *
         * @param value The byte value to serialize.
         * @return The Serializer instance for method chaining.
         */
        @NonNull
        public Serializer put(final byte value) {
            try {
                os.write(value);
            } catch (IOException e) {
                throw new IllegalStateException("Could not write", e);
            }
            return this;
        }

        /**
         * Adds an integer value to the serializer.
         *
         * @param value The integer value to serialize.
         * @return The Serializer instance for method chaining.
         */
        @NonNull
        public Serializer put(final int value) {
            try {
                os.write(toByteArray(value));
            } catch (IOException e) {
                throw new IllegalStateException("Could not write", e);
            }
            return this;
        }

        /**
         * Serializes all added entries into a byte array.
         *
         * @return The byte array containing the serialized data.
         */
        @NonNull
        public byte[] toBytes() {
            return bo.toByteArray();
        }
    }

    /**
     * A utility class for deserializing data from a byte array.
     */
    public static class Deserializer {
        private final DataInputStream is;

        /**
         * Constructs a Deserializer with the given byte array.
         *
         * @param message The byte array containing serialized data.
         */
        public Deserializer(@NonNull byte[] message) {
            Objects.requireNonNull(message, "message must not be null");
            final ByteArrayInputStream buffer = new ByteArrayInputStream(message);
            this.is = new DataInputStream(buffer);
        }

        /**
         * Deserializes the specified size in the byte array using the provided function if there is enough information in the buffer.
         *
         * @param <T> The type of the deserialized object.
         * @param f The function to convert a byte array into an object of type T.
         * @param size The number of bytes to read.
         * @return The deserialized object.
         * @throws IllegalStateException if there are not enough bytes remaining to read.
         */
        @NonNull
        public <T> T read(@NonNull final Function<byte[], T> f, int size) {
            var bytes = new byte[size];
            try {
                if (is.read(bytes) != size) {
                    throw new IllegalStateException("Not enough bytes to read");
                }
                return Objects.requireNonNull(f).apply(bytes);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read", e);
            }
        }

        /**
         * Reads and deserializes a byte from the buffer if there is enough information in the buffer.
         *
         * @return The deserialized byte value.
         * @throws IllegalStateException if there are not enough bytes to read.
         */
        public byte readByte() {
            try {
                return is.readByte();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read", e);
            }
        }

        /**
         * Reads and deserializes an int from the buffer if there is enough information in the buffer.
         *
         * @return The deserialized integer value.
         * @throws IllegalStateException if there are not enough bytes to read.
         */
        public int readInt() {
            try {
                return is.readInt();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot Read", e);
            }
        }

        /**
         * Deserializes a list of elements using the provided function and specified elementSize for each element.
         *
         * @param <T> The type of elements in the list.
         * @param f The function that converts a byte array into an object of type T.
         * @param listSize The number of elements in the list.
         * @param elementSize The size in bytes of each element.
         * @return The list of deserialized objects.
         * @throws IllegalStateException if there are not enough bytes to read.
         */
        @NonNull
        public <T> List<T> readListSameSize(
                @NonNull final Function<byte[], T> f, final int listSize, final int elementSize) {
            var elems = listSize;
            var list = new ArrayList<T>(elems);
            while (elems > 0) {
                list.add(read(f, elementSize));
                elems--;
            }
            return list;
        }
    }
}
