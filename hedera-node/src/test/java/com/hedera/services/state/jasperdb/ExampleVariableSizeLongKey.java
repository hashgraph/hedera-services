package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.files.DataFileCommon;
import com.hedera.services.state.jasperdb.files.hashmap.KeySerializer;
import com.hedera.services.state.jasperdb.utilities.NonCryptographicHashing;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualLongKey;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ExampleVariableSizeLongKey implements VirtualLongKey {
    private long value;
    private int hashCode;

    public ExampleVariableSizeLongKey(long value) {
        setValue(value);
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
        this.hashCode = NonCryptographicHashing.hash32(value);
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
        return 5614541343884544468L;
    }

    public int getVersion() {
        return 1;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ExampleVariableSizeLongKey)) {
            return false;
        } else {
            ExampleVariableSizeLongKey that = (ExampleVariableSizeLongKey) o;
            return this.value == that.value;
        }
    }

    @Override
    public String toString() {
        return "ExampleVariableSizeLongKey{" +
                "value=" + value +
                ", hashCode=" + hashCode +
                '}';
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
     * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for value 0
     */
    static byte computeNonZeroBytes(long num) {
        if (num == 0) return (byte)1;
        return (byte)Math.ceil((double)(Long.SIZE-Long.numberOfLeadingZeros(num))/8D);
    }


    public static class Serializer implements KeySerializer<ExampleVariableSizeLongKey> {
        /**
         * For variable sized data get the typical  number of bytes a data item takes when serialized
         *
         * @return Either for fixed size same as getSerializedSize() or an estimated typical size for data items
         */
        @Override
        public int getTypicalSerializedSize() {
            return 1+Long.BYTES;
        }

        /**
         * Deserialize key size from the given byte buffer
         *
         * @param buffer Buffer to read from
         * @return The number of bytes used to store the key, including for storing the key size if needed.
         */
        @Override
        public int deserializeKeySize(ByteBuffer buffer) {
            byte numOfBytes = buffer.get();
            return 1+numOfBytes;
        }

        /**
         * Get the number of bytes a data item takes when serialized
         *
         * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
         */
        @Override
        public int getSerializedSize() {
            return DataFileCommon.VARIABLE_DATA_SIZE;
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
        public ExampleVariableSizeLongKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
            byte numOfBytes = buffer.get();
            long value = 0;
            if (numOfBytes >= 8) value |= ((long)buffer.get() & 255) << 56;
            if (numOfBytes >= 7) value |= ((long)buffer.get() & 255) << 48;
            if (numOfBytes >= 6) value |= ((long)buffer.get() & 255) << 40;
            if (numOfBytes >= 5) value |= ((long)buffer.get() & 255) << 32;
            if (numOfBytes >= 4) value |= ((long)buffer.get() & 255) << 24;
            if (numOfBytes >= 3) value |= ((long)buffer.get() & 255) << 16;
            if (numOfBytes >= 2) value |= ((long)buffer.get() & 255) << 8;
            if (numOfBytes >= 1) value |= ((long)buffer.get() & 255);
            return new ExampleVariableSizeLongKey(value);
        }

        /**
         * Serialize a data item including header to the output stream returning the size of the data written
         *
         * @param data         The data item to serialize
         * @param outputStream Output stream to write to
         */
        @Override
        public int serialize(ExampleVariableSizeLongKey data, SerializableDataOutputStream outputStream) throws IOException {
            final long key = data.getKeyAsLong();
            int numOfBytes = computeNonZeroBytes(key);
            outputStream.write(numOfBytes);
            for (int b = numOfBytes-1; b >= 0; b--) {
                outputStream.write((byte)(key >> (b*8)));
            }
            return 1+numOfBytes;
        }
    }
}
