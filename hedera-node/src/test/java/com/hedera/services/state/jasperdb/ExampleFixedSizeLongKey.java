package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualLongKey;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ExampleFixedSizeLongKey implements VirtualLongKey, FastCopyable {
    private long value;
    private int hashCode;

    public ExampleFixedSizeLongKey() {}

    public ExampleFixedSizeLongKey(long value) {
        setValue(value);
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
        this.hashCode = Long.hashCode(value);
    }

    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(value);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int version) throws IOException {
        setValue(byteBuffer.getLong());
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int version) throws IOException {
        return byteBuffer.getLong() == value;
    }

    public ExampleFixedSizeLongKey copy() {
        return new ExampleFixedSizeLongKey(this.value);
    }

    public void release() {
    }

    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(this.value);
    }

    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        this.value = in.readLong();
    }

    public long getClassId() {
        return 8133160492230511558L;
    }

    public int getVersion() {
        return 1;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ExampleFixedSizeLongKey)) {
            return false;
        } else {
            ExampleFixedSizeLongKey that = (ExampleFixedSizeLongKey) o;
            return this.value == that.value;
        }
    }

    @Override
    public String toString() {
        return "LongVirtualKey{" +
                "value=" + value +
                ", hashCode=" + hashCode +
                '}';
    }

    @Override
    public long getKeyAsLong() {
        return value;
    }


    public static class Serializer implements KeySerializer<ExampleFixedSizeLongKey> {

        /**
         * Deserialize key size from the given byte buffer
         *
         * @param buffer Buffer to read from
         * @return The number of bytes used to store the key, including for storing the key size if needed.
         */
        @Override
        public int deserializeKeySize(ByteBuffer buffer) {
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
            return 1;
        }

        /**
         * Deserialize a data item from a byte buffer, that was written with given data version
         *
         * @param buffer      The buffer to read from containing the data item including its header
         * @param dataVersion The serialization version the data item was written with
         * @return Deserialized data item
         */
        @Override
        public ExampleFixedSizeLongKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
            long value = buffer.getLong();
            return new ExampleFixedSizeLongKey(value);
        }

        /**
         * Serialize a data item including header to the output stream returning the size of the data written
         *
         * @param data         The data item to serialize
         * @param outputStream Output stream to write to
         */
        @Override
        public int serialize(ExampleFixedSizeLongKey data, SerializableDataOutputStream outputStream) throws IOException {
            outputStream.writeLong(data.getKeyAsLong());
            return Long.BYTES;
        }
    }
}
