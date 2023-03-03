/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ExampleLongKeyVariableSize implements VirtualLongKey {

    /** random so that for testing we are sure we are getting same version */
    private static final int CURRENT_SERIALIZATION_VERSION = 8483;

    private long value;
    private int hashCode;

    public ExampleLongKeyVariableSize() {}

    public ExampleLongKeyVariableSize(final long value) {
        setValue(value);
    }

    public long getValue() {
        return value;
    }

    public void setValue(final long value) {
        this.value = value;
        this.hashCode = Long.hashCode(value);
    }

    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer) throws IOException {
        final int numOfBytes = computeNonZeroBytes(value);
        byteBuffer.put((byte) numOfBytes);
        for (int b = numOfBytes - 1; b >= 0; b--) {
            byteBuffer.put((byte) (value >> (b * 8)));
        }
    }

    // [0, 0, 0, 98, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    // 0, 0, 0, 0, 0, 0, 0,
    // 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 80,
    // -69, 72, 20, 36,
    // -106, -79, -64, -127, 4, 49, -84, -102, 77, -47, 70, -106, 113, -70, 8, -66, 43, 86, 116,
    // -25, -12, -1, 75, 14,
    // -122, -42, 65, 0, 0, +4,194,204 more]
    // [0, 0, 0, 98, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    // 0, 0, 0, 0, 0, 0, 0,
    // 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, -82,
    // 25, 2, 43, -51,
    // 119, -30, 62, -66, 52, -42, -36, 32, 11, 0, -36, 48, 117, 27, -16, -81, 9, 126, -25, -116,
    // -124, 27, 32, -44,
    // 3, -49, 100]
    @Override
    public void deserialize(final ByteBuffer buffer, final int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        byte numOfBytes = buffer.get();
        long value = 0;
        if (numOfBytes >= 8) value |= ((long) buffer.get() & 255) << 56;
        if (numOfBytes >= 7) value |= ((long) buffer.get() & 255) << 48;
        if (numOfBytes >= 6) value |= ((long) buffer.get() & 255) << 40;
        if (numOfBytes >= 5) value |= ((long) buffer.get() & 255) << 32;
        if (numOfBytes >= 4) value |= ((long) buffer.get() & 255) << 24;
        if (numOfBytes >= 3) value |= ((long) buffer.get() & 255) << 16;
        if (numOfBytes >= 2) value |= ((long) buffer.get() & 255) << 8;
        if (numOfBytes >= 1) value |= ((long) buffer.get() & 255);
        setValue(value);
    }

    public ExampleLongKeyFixedSize copy() {
        return new ExampleLongKeyFixedSize(this.value);
    }

    public void serialize(final SerializableDataOutputStream outputStream) throws IOException {
        final int numOfBytes = computeNonZeroBytes(value);
        outputStream.write(numOfBytes);
        for (int b = numOfBytes - 1; b >= 0; b--) {
            outputStream.write((byte) (value >> (b * 8)));
        }
    }

    public void deserialize(final SerializableDataInputStream in, final int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        final byte numOfBytes = in.readByte();
        long value = 0;
        if (numOfBytes >= 8) value |= ((long) in.readByte() & 255) << 56;
        if (numOfBytes >= 7) value |= ((long) in.readByte() & 255) << 48;
        if (numOfBytes >= 6) value |= ((long) in.readByte() & 255) << 40;
        if (numOfBytes >= 5) value |= ((long) in.readByte() & 255) << 32;
        if (numOfBytes >= 4) value |= ((long) in.readByte() & 255) << 24;
        if (numOfBytes >= 3) value |= ((long) in.readByte() & 255) << 16;
        if (numOfBytes >= 2) value |= ((long) in.readByte() & 255) << 8;
        if (numOfBytes >= 1) value |= ((long) in.readByte() & 255);
        setValue(value);
    }

    public long getClassId() {
        return 5614541343884544469L;
    }

    public int getVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof final ExampleLongKeyVariableSize that)) {
            return false;
        } else {
            return this.value == that.value;
        }
    }

    @Override
    public String toString() {
        return "ExampleVariableSizeLongKey{" + "value=" + value + ", hashCode=" + hashCode + '}';
    }

    /**
     * Direct access to the value of this key in its raw long format
     *
     * @return the long value of this key
     */
    @Override
    public long getKeyAsLong() {
        return value;
    }

    /**
     * Compute number of bytes of non-zero data are there from the least significant side of a long.
     *
     * @param num the long to count non-zero bits for
     * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for
     *     value 0
     */
    static byte computeNonZeroBytes(final long num) {
        if (num == 0) {
            return (byte) 1;
        }
        return (byte) Math.ceil((double) (Long.SIZE - Long.numberOfLeadingZeros(num)) / 8D);
    }

    public static class Serializer implements KeySerializer<ExampleLongKeyVariableSize> {

        private static final long CLASS_ID = 0x8cc51b9601c706e7L;

        private static final class ClassVersion {
            public static final int ORIGINAL = 1;
        }

        /** {@inheritDoc} */
        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        /** {@inheritDoc} */
        @Override
        public int getVersion() {
            return ClassVersion.ORIGINAL;
        }

        /**
         * For variable sized data get the typical number of bytes a data item takes when serialized
         *
         * @return Either for fixed size same as getSerializedSize() or an estimated typical size
         *     for data items
         */
        @Override
        public int getTypicalSerializedSize() {
            return 1 + Long.BYTES;
        }

        /**
         * Deserialize key size from the given byte buffer
         *
         * @param buffer Buffer to read from
         * @return The number of bytes used to store the key, including for storing the key size if
         *     needed.
         */
        @Override
        public int deserializeKeySize(final ByteBuffer buffer) {
            byte numOfBytes = buffer.get();
            return 1 + numOfBytes;
        }

        /**
         * Get the number of bytes a data item takes when serialized
         *
         * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
         */
        @Override
        public int getSerializedSize() {
            return VARIABLE_DATA_SIZE;
        }

        /** Get the current data item serialization version */
        @Override
        public long getCurrentDataVersion() {
            return CURRENT_SERIALIZATION_VERSION; // random so that for testing we are sure we are
            // getting same version
        }

        /**
         * Deserialize a data item from a byte buffer, that was written with given data version
         *
         * @param buffer The buffer to read from containing the data item including its header
         * @param dataVersion The serialization version the data item was written with
         * @return Deserialized data item
         */
        @Override
        public ExampleLongKeyVariableSize deserialize(final ByteBuffer buffer, final long dataVersion)
                throws IOException {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            final ExampleLongKeyVariableSize key = new ExampleLongKeyVariableSize();
            key.deserialize(buffer, (int) dataVersion);
            return key;
        }

        @Override
        public int serialize(final ExampleLongKeyVariableSize data, final ByteBuffer buffer) throws IOException {
            data.serialize(buffer);
            return 1 + computeNonZeroBytes(data.getKeyAsLong());
        }

        /**
         * Compare keyToCompare's data to that contained in the given ByteBuffer. The data in the
         * buffer is assumed to be starting at the current buffer position and in the format written
         * by this class's serialize() method. The reason for this rather than just deserializing
         * then doing an object equals is performance. By doing the comparison here you can fail
         * fast on the first byte that does not match. As this is used in a tight loop in searching
         * a hash map bucket for a match performance is critical.
         *
         * @param buffer The buffer to read from and compare to
         * @param dataVersion The serialization dataVersion of the data in the buffer
         * @param keyToCompare The key to compare with the data in the file.
         * @return true if the content of the buffer matches this class's data
         * @throws IOException If there was a problem reading from the buffer
         */
        @Override
        public boolean equals(ByteBuffer buffer, int dataVersion, ExampleLongKeyVariableSize keyToCompare)
                throws IOException {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            byte numOfBytes = buffer.get();
            long value = 0;
            if (numOfBytes >= 8) value |= ((long) buffer.get() & 255) << 56;
            if (numOfBytes >= 7) value |= ((long) buffer.get() & 255) << 48;
            if (numOfBytes >= 6) value |= ((long) buffer.get() & 255) << 40;
            if (numOfBytes >= 5) value |= ((long) buffer.get() & 255) << 32;
            if (numOfBytes >= 4) value |= ((long) buffer.get() & 255) << 24;
            if (numOfBytes >= 3) value |= ((long) buffer.get() & 255) << 16;
            if (numOfBytes >= 2) value |= ((long) buffer.get() & 255) << 8;
            if (numOfBytes >= 1) value |= ((long) buffer.get() & 255);
            return value == keyToCompare.getKeyAsLong();
        }

        /** {@inheritDoc} */
        @Override
        public void serialize(final SerializableDataOutputStream out) throws IOException {}

        /** {@inheritDoc} */
        @Override
        public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(final Object obj) {
            // Since there is no class state, objects of the same type are considered to be equal
            return obj instanceof Serializer;
        }
    }
}
