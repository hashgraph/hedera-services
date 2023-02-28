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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;

@SuppressWarnings("unused")
public class ExampleLongLongKeyFixedSize implements VirtualLongKey, FastCopyable {

    /** random so that for testing we are sure we are getting same version */
    private static final int CURRENT_SERIALIZATION_VERSION = 4685;

    private long value1;
    private long value2;

    public ExampleLongLongKeyFixedSize() {}

    public ExampleLongLongKeyFixedSize(final long value) {
        this.value1 = value;
        this.value2 = Long.MAX_VALUE - value;
    }

    public ExampleLongLongKeyFixedSize(final long value1, final long value2) {
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
    public void serialize(final ByteBuffer byteBuffer) {
        byteBuffer.putLong(value1);
        byteBuffer.putLong(value2);
    }

    @Override
    public void deserialize(final ByteBuffer byteBuffer, final int dataVersion) {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        this.value1 = byteBuffer.getLong();
        this.value2 = byteBuffer.getLong();
    }

    @SuppressWarnings("unchecked")
    public ExampleLongLongKeyFixedSize copy() {
        return new ExampleLongLongKeyFixedSize(this.value1, this.value2);
    }

    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(this.value1);
        out.writeLong(this.value2);
    }

    public void deserialize(final SerializableDataInputStream in, final int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        this.value1 = in.readLong();
        this.value2 = in.readLong();
    }

    public long getClassId() {
        return 654838434445546360L;
    }

    public int getVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ExampleLongLongKeyFixedSize)) {
            return false;
        } else {
            final ExampleLongLongKeyFixedSize that = (ExampleLongLongKeyFixedSize) o;
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
        return "ExampleLongLongKeyFixedSize{" + "value1=" + value1 + ", value2=" + value2 + '}';
    }

    @Override
    public long getKeyAsLong() {
        return value1;
    }

    public static class Serializer implements KeySerializer<ExampleLongLongKeyFixedSize> {

        private static final long CLASS_ID = 0xee6fa2534c50d634L;

        private static final class ClassVersion {
            public static final int ORIGINAL = 1;
        }

        public Serializer() {}

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
         * Deserialize key size from the given byte buffer
         *
         * @param buffer
         * 		Buffer to read from
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
            return Long.BYTES + Long.BYTES;
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
        public ExampleLongLongKeyFixedSize deserialize(final ByteBuffer buffer, final long dataVersion) {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            final long value1 = buffer.getLong();
            final long value2 = buffer.getLong();
            return new ExampleLongLongKeyFixedSize(value1, value2);
        }

        /**
         * Serialize a data item including header to the output stream returning the size of the data written
         *
         * @param data
         * 		The data item to serialize
         * @param outputStream
         * 		Output stream to write to
         * @return
         * 		The number of bytes written
         */
        @Override
        public int serialize(final ExampleLongLongKeyFixedSize data, final SerializableDataOutputStream outputStream)
                throws IOException {
            outputStream.writeLong(data.getValue1());
            outputStream.writeLong(data.getValue2());
            return getSerializedSize();
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
        public int serialize(final ExampleLongLongKeyFixedSize data, final ByteBuffer buffer) throws IOException {
            buffer.putLong(data.getValue1());
            buffer.putLong(data.getValue2());
            return getSerializedSize();
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
         * 		The serialization version of the data in the buffer
         * @param keyToCompare
         * 		The key to compare with the data in the file.
         * @return true if the content of the buffer matches this class's data
         * @throws IOException
         * 		If there was a problem reading from the buffer
         */
        @Override
        public boolean equals(ByteBuffer buffer, int dataVersion, ExampleLongLongKeyFixedSize keyToCompare)
                throws IOException {
            assert dataVersion == getCurrentDataVersion()
                    : "dataVersion=" + dataVersion + " != getCurrentDataVersion()=" + getCurrentDataVersion();
            final long readValue1 = buffer.getLong();
            final long readValue2 = buffer.getLong();
            return readValue1 == keyToCompare.getValue1() && readValue2 == keyToCompare.getValue2();
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
