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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeyIndexType;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ExampleLongKeyFixedSize implements VirtualLongKey, FastCopyable {

    private static final long CLASS_ID = 0x6ec21ff5ab56811fL;

    /** random so that for testing we are sure we are getting same version */
    private static final int CURRENT_SERIALIZATION_VERSION = 3054;

    private long value;
    private int hashCode;

    public ExampleLongKeyFixedSize() {}

    public ExampleLongKeyFixedSize(final long value) {
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
    public void serialize(final ByteBuffer byteBuffer) {
        byteBuffer.putLong(value);
    }

    @Override
    public void deserialize(final ByteBuffer byteBuffer, final int dataVersion) {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        setValue(byteBuffer.getLong());
    }

    @SuppressWarnings("unchecked")
    public ExampleLongKeyFixedSize copy() {
        return new ExampleLongKeyFixedSize(this.value);
    }

    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(this.value);
    }

    public void deserialize(final SerializableDataInputStream in, final int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        setValue(in.readLong());
    }

    public long getClassId() {
        return CLASS_ID;
    }

    public int getVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ExampleLongKeyFixedSize)) {
            return false;
        } else {
            final ExampleLongKeyFixedSize that = (ExampleLongKeyFixedSize) o;
            return this.value == that.value;
        }
    }

    @Override
    public String toString() {
        return "LongVirtualKey{" + "value=" + value + ", hashCode=" + hashCode + '}';
    }

    @Override
    public long getKeyAsLong() {
        return value;
    }

    public static class Serializer implements KeySerializer<ExampleLongKeyFixedSize> {

        private static final long CLASS_ID = 0x58a0db3356d8ec69L;

        private static final class ClassVersion {
            public static final int ORIGINAL = 1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public KeyIndexType getIndexType() {
            return KeyIndexType.SEQUENTIAL_INCREMENTING_LONGS;
        }

        /**
         * Deserialize key size from the given byte buffer
         *
         * @param buffer
         *         Buffer to read from
         * @return The number of bytes used to store the key, including for storing the key size if needed.
         */
        @Override
        public int deserializeKeySize(final ByteBuffer buffer) {
            return getSerializedSize();
        }

        /**
         * Get the number of bytes a data item takes when serialized
         *
         * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
         */
        @Override
        public int getSerializedSize() {
            return Long.BYTES;
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
         *         The buffer to read from containing the data item including its header
         * @param dataVersion
         *         The serialization version the data item was written with
         * @return Deserialized data item
         */
        @Override
        public ExampleLongKeyFixedSize deserialize(final ByteBuffer buffer, final long dataVersion) {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            final long value = buffer.getLong();
            return new ExampleLongKeyFixedSize(value);
        }

        /**
         * Serialize a data item including header to the output stream returning the size of the data written
         *
         * @param data
         *         The data item to serialize
         * @param outputStream
         *         Output stream to write to
         * @return
         *         Number of bytes written
         */
        @Override
        public int serialize(final ExampleLongKeyFixedSize data, final SerializableDataOutputStream outputStream)
                throws IOException {
            outputStream.writeLong(data.getKeyAsLong());
            return Long.BYTES;
        }

        /**
         * Serialize a data item including header to the byte buffer returning the size of the data written
         *
         * @param data
         *         The data item to serialize
         * @param buffer
         *         Byte buffer to write to
         * @return
         *         Number of bytes written
         */
        @Override
        public int serialize(final ExampleLongKeyFixedSize data, final ByteBuffer buffer) throws IOException {
            buffer.putLong(data.getKeyAsLong());
            return Long.BYTES;
        }

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
         *         The buffer to read from and compare to
         * @param dataVersion
         *         The serialization version of the data in the buffer
         * @param keyToCompare
         *         The key to compare with the data in the file.
         * @return true if the content of the buffer matches this class's data
         * @throws IOException
         *         If there was a problem reading from the buffer
         */
        @Override
        public boolean equals(ByteBuffer buffer, int dataVersion, ExampleLongKeyFixedSize keyToCompare)
                throws IOException {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            final long readKey = buffer.getLong();
            return readKey == keyToCompare.getKeyAsLong();
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
        public void serialize(final SerializableDataOutputStream out) throws IOException {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

        /**
         * {@inheritDoc}
         */
        @Override
        public int getVersion() {
            return ClassVersion.ORIGINAL;
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
