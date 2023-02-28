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

import static com.swirlds.common.utility.NonCryptographicHashing.hash32;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeyIndexType;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ExampleLongLongKeyVariableSize implements VirtualLongKey {

    /** random so that for testing we are sure we are getting same version */
    private static final int CURRENT_SERIALIZATION_VERSION = 1235;

    private long value1;
    private long value2;

    public ExampleLongLongKeyVariableSize() {}

    public ExampleLongLongKeyVariableSize(final long value) {
        this.value1 = value;
        this.value2 = Long.MAX_VALUE - value;
    }

    public ExampleLongLongKeyVariableSize(final long value1, final long value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    public long getValue() {
        return value1;
    }

    public int hashCode() {
        return hash32(value1, value2);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer) throws IOException {
        final int numOfBytes1 = computeNonZeroBytes(value1);
        final int numOfBytes2 = computeNonZeroBytes(value2);
        byteBuffer.put((byte) numOfBytes1);
        byteBuffer.put((byte) numOfBytes2);
        for (int b = numOfBytes1 - 1; b >= 0; b--) {
            byteBuffer.put((byte) (value1 >> (b * 8)));
        }
        for (int b = numOfBytes2 - 1; b >= 0; b--) {
            byteBuffer.put((byte) (value2 >> (b * 8)));
        }
    }

    @Override
    public void deserialize(final ByteBuffer buffer, final int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        byte numOfBytes1 = buffer.get();
        byte numOfBytes2 = buffer.get();
        long value1 = 0;
        if (numOfBytes1 >= 8) value1 |= ((long) buffer.get() & 255) << 56;
        if (numOfBytes1 >= 7) value1 |= ((long) buffer.get() & 255) << 48;
        if (numOfBytes1 >= 6) value1 |= ((long) buffer.get() & 255) << 40;
        if (numOfBytes1 >= 5) value1 |= ((long) buffer.get() & 255) << 32;
        if (numOfBytes1 >= 4) value1 |= ((long) buffer.get() & 255) << 24;
        if (numOfBytes1 >= 3) value1 |= ((long) buffer.get() & 255) << 16;
        if (numOfBytes1 >= 2) value1 |= ((long) buffer.get() & 255) << 8;
        if (numOfBytes1 >= 1) value1 |= ((long) buffer.get() & 255);
        this.value1 = value1;
        long value2 = 0;
        if (numOfBytes2 >= 8) value2 |= ((long) buffer.get() & 255) << 56;
        if (numOfBytes2 >= 7) value2 |= ((long) buffer.get() & 255) << 48;
        if (numOfBytes2 >= 6) value2 |= ((long) buffer.get() & 255) << 40;
        if (numOfBytes2 >= 5) value2 |= ((long) buffer.get() & 255) << 32;
        if (numOfBytes2 >= 4) value2 |= ((long) buffer.get() & 255) << 24;
        if (numOfBytes2 >= 3) value2 |= ((long) buffer.get() & 255) << 16;
        if (numOfBytes2 >= 2) value2 |= ((long) buffer.get() & 255) << 8;
        if (numOfBytes2 >= 1) value2 |= ((long) buffer.get() & 255);
        this.value2 = value2;
    }

    public ExampleLongLongKeyVariableSize copy() {
        return new ExampleLongLongKeyVariableSize(this.value1, value2);
    }

    public void serialize(final SerializableDataOutputStream outputStream) throws IOException {
        final int numOfBytes1 = computeNonZeroBytes(value1);
        final int numOfBytes2 = computeNonZeroBytes(value2);
        outputStream.write(numOfBytes1);
        outputStream.write(numOfBytes2);
        for (int b = numOfBytes1 - 1; b >= 0; b--) {
            outputStream.write((byte) (value1 >> (b * 8)));
        }
        for (int b = numOfBytes2 - 1; b >= 0; b--) {
            outputStream.write((byte) (value2 >> (b * 8)));
        }
    }

    public void deserialize(final SerializableDataInputStream in, final int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        byte numOfBytes1 = in.readByte();
        byte numOfBytes2 = in.readByte();
        long value1 = 0;
        if (numOfBytes1 >= 8) value1 |= ((long) in.readByte() & 255) << 56;
        if (numOfBytes1 >= 7) value1 |= ((long) in.readByte() & 255) << 48;
        if (numOfBytes1 >= 6) value1 |= ((long) in.readByte() & 255) << 40;
        if (numOfBytes1 >= 5) value1 |= ((long) in.readByte() & 255) << 32;
        if (numOfBytes1 >= 4) value1 |= ((long) in.readByte() & 255) << 24;
        if (numOfBytes1 >= 3) value1 |= ((long) in.readByte() & 255) << 16;
        if (numOfBytes1 >= 2) value1 |= ((long) in.readByte() & 255) << 8;
        if (numOfBytes1 >= 1) value1 |= ((long) in.readByte() & 255);
        this.value1 = value1;
        long value2 = 0;
        if (numOfBytes2 >= 8) value2 |= ((long) in.readByte() & 255) << 56;
        if (numOfBytes2 >= 7) value2 |= ((long) in.readByte() & 255) << 48;
        if (numOfBytes2 >= 6) value2 |= ((long) in.readByte() & 255) << 40;
        if (numOfBytes2 >= 5) value2 |= ((long) in.readByte() & 255) << 32;
        if (numOfBytes2 >= 4) value2 |= ((long) in.readByte() & 255) << 24;
        if (numOfBytes2 >= 3) value2 |= ((long) in.readByte() & 255) << 16;
        if (numOfBytes2 >= 2) value2 |= ((long) in.readByte() & 255) << 8;
        if (numOfBytes2 >= 1) value2 |= ((long) in.readByte() & 255);
        this.value2 = value2;
    }

    public long getClassId() {
        return 6484134134848452L;
    }

    public int getVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ExampleLongLongKeyVariableSize)) {
            return false;
        } else {
            final ExampleLongLongKeyVariableSize that = (ExampleLongLongKeyVariableSize) o;
            return this.value1 == that.value1 && this.value2 == that.value2;
        }
    }

    public long getValue1() {
        return value1;
    }

    public long getValue2() {
        return value2;
    }

    @Override
    public String toString() {
        return "ExampleLongLongKeyVariableSize{" + "value1=" + value1 + ", value2=" + value2 + '}';
    }

    /**
     * Direct access to the value of this key in its raw long format
     *
     * @return the long value of this key
     */
    @Override
    public long getKeyAsLong() {
        return value1;
    }

    /**
     * Compute number of bytes of non-zero data are there from the least significant side of a long.
     *
     * @param num
     * 		the long to count non-zero bits for
     * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for value 0
     */
    static byte computeNonZeroBytes(final long num) {
        if (num == 0) {
            return (byte) 1;
        }
        return (byte) Math.ceil((double) (Long.SIZE - Long.numberOfLeadingZeros(num)) / 8D);
    }

    public static class Serializer implements KeySerializer<ExampleLongLongKeyVariableSize> {

        private static final long CLASS_ID = 0x6687e6bfe46404e9L;

        private static final class ClassVersion {
            public static final int ORIGINAL = 1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public KeyIndexType getIndexType() {
            return KeyIndexType.GENERIC;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getVersion() {
            return ClassVersion.ORIGINAL;
        }

        /**
         * For variable sized data get the typical  number of bytes a data item takes when serialized
         *
         * @return Either for fixed size same as getSerializedSize() or an estimated typical size for data items
         */
        @Override
        public int getTypicalSerializedSize() {
            return 2 + Long.BYTES;
        }

        /**
         * Deserialize key size from the given byte buffer
         *
         * @param buffer
         * 		Buffer to read from
         * @return The number of bytes used to store the key, including for storing the key size if needed.
         */
        @Override
        public int deserializeKeySize(final ByteBuffer buffer) {
            byte numOfBytes1 = buffer.get();
            byte numOfBytes2 = buffer.get();
            return 2 + numOfBytes1 + numOfBytes2;
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

        /**
         * Get the current data item serialization version
         */
        @Override
        public long getCurrentDataVersion() {
            return CURRENT_SERIALIZATION_VERSION;
        }

        /**
         * Deserialize a data item from a byte buffer, that was written with given data version
         *
         * @param buffer
         * 		The buffer to read from containing the data item including its header
         * @param dataVersion
         * 		The serialization version the data item was written with
         * @return Deserialized data item
         */
        @Override
        public ExampleLongLongKeyVariableSize deserialize(final ByteBuffer buffer, final long dataVersion)
                throws IOException {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            final ExampleLongLongKeyVariableSize key = new ExampleLongLongKeyVariableSize();
            key.deserialize(buffer, (int) dataVersion);
            return key;
        }

        /**
         * Serialize a data item including header to the output stream returning the size of the data written
         *
         * @param data
         * 		The data item to serialize
         * @param outputStream
         * 		Output stream to write to
         * @return
         * 		Number of bytes written
         */
        @Override
        public int serialize(final ExampleLongLongKeyVariableSize data, final SerializableDataOutputStream outputStream)
                throws IOException {
            data.serialize(outputStream);
            return 2 + computeNonZeroBytes(data.value1) + computeNonZeroBytes(data.value2);
        }

        /**
         * Serialize a data item including header to the byte buffer returning the size of the data written
         *
         * @param data
         * 		The data item to serialize
         * @param buffer
         * 		Byte buffer to write to
         * @return
         * 		Number of bytes written
         */
        @Override
        public int serialize(final ExampleLongLongKeyVariableSize data, final ByteBuffer buffer) throws IOException {
            data.serialize(buffer);
            return 2 + computeNonZeroBytes(data.value1) + computeNonZeroBytes(data.value2);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void serialize(final SerializableDataOutputStream out) throws IOException {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

        /**
         * Compare keyToCompare's data to that contained in the given ByteBuffer. The data in the buffer is assumed to
         * be
         * starting at the current buffer position and in the format written by this class's serialize() method. The
         * reason
         * for this rather than just deserializing then doing an object equals is performance. By doing the comparison
         * here
         * you can fail fast on the first byte that does not match. As this is used in a tight loop in searching a hash
         * map
         * bucket for a match performance is critical.
         *
         * @param buffer
         * 		The buffer to read from and compare to
         * @param dataVersion
         * 		The serialization dataVersion of the data in the buffer
         * @param keyToCompare
         * 		The key to compare with the data in the file.
         * @return true if the content of the buffer matches this class's data
         * @throws IOException
         * 		If there was a problem reading from the buffer
         */
        @Override
        public boolean equals(ByteBuffer buffer, int dataVersion, ExampleLongLongKeyVariableSize keyToCompare)
                throws IOException {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            byte numOfBytes1 = buffer.get();
            byte numOfBytes2 = buffer.get();
            long value1 = 0;
            if (numOfBytes1 >= 8) value1 |= ((long) buffer.get() & 255) << 56;
            if (numOfBytes1 >= 7) value1 |= ((long) buffer.get() & 255) << 48;
            if (numOfBytes1 >= 6) value1 |= ((long) buffer.get() & 255) << 40;
            if (numOfBytes1 >= 5) value1 |= ((long) buffer.get() & 255) << 32;
            if (numOfBytes1 >= 4) value1 |= ((long) buffer.get() & 255) << 24;
            if (numOfBytes1 >= 3) value1 |= ((long) buffer.get() & 255) << 16;
            if (numOfBytes1 >= 2) value1 |= ((long) buffer.get() & 255) << 8;
            if (numOfBytes1 >= 1) value1 |= ((long) buffer.get() & 255);
            long value2 = 0;
            if (numOfBytes2 >= 8) value2 |= ((long) buffer.get() & 255) << 56;
            if (numOfBytes2 >= 7) value2 |= ((long) buffer.get() & 255) << 48;
            if (numOfBytes2 >= 6) value2 |= ((long) buffer.get() & 255) << 40;
            if (numOfBytes2 >= 5) value2 |= ((long) buffer.get() & 255) << 32;
            if (numOfBytes2 >= 4) value2 |= ((long) buffer.get() & 255) << 24;
            if (numOfBytes2 >= 3) value2 |= ((long) buffer.get() & 255) << 16;
            if (numOfBytes2 >= 2) value2 |= ((long) buffer.get() & 255) << 8;
            if (numOfBytes2 >= 1) value2 |= ((long) buffer.get() & 255);
            return value1 == keyToCompare.getValue1() && value2 == keyToCompare.getValue2();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj) {
            // Since there is no class state, objects of the same type are considered to be equal
            return obj instanceof Serializer;
        }
    }
}
