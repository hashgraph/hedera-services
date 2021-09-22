package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.files.DataFileCommon;
import com.hedera.services.state.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualLongKey;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ExampleVariableSizeLongKey implements VirtualLongKey {
    private long value;
    private int hashCode;

    public ExampleVariableSizeLongKey() {}

    public ExampleVariableSizeLongKey(long value) {
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
        int numOfBytes = computeNonZeroBytes(value);
        byteBuffer.put((byte)numOfBytes);
        for (int b = numOfBytes-1; b >= 0; b--) {
            byteBuffer.put((byte)(value >> (b*8)));
        }
    }
    // [0, 0, 0, 98, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 80, -69, 72, 20, 36, -106, -79, -64, -127, 4, 49, -84, -102, 77, -47, 70, -106, 113, -70, 8, -66, 43, 86, 116, -25, -12, -1, 75, 14, -122, -42, 65, 0, 0, +4,194,204 more]
    // [0, 0, 0, 98, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, -82, 25, 2, 43, -51, 119, -30, 62, -66, 52, -42, -36, 32, 11, 0, -36, 48, 117, 27, -16, -81, 9, 126, -25, -116, -124, 27, 32, -44, 3, -49, 100]
    @Override
    public void deserialize(ByteBuffer buffer, int version) throws IOException {
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
        setValue(value);
    }

    @Override
    public boolean equals(ByteBuffer buffer, int version) throws IOException {
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
        return value == this.value;
    }

    public ExampleFixedSizeLongKey copy() {
        return new ExampleFixedSizeLongKey(this.value);
    }

    public void release() {
    }

    public void serialize(SerializableDataOutputStream outputStream) throws IOException {
        int numOfBytes = computeNonZeroBytes(value);
        outputStream.write(numOfBytes);
        for (int b = numOfBytes-1; b >= 0; b--) {
            outputStream.write((byte)(value >> (b*8)));
        }
    }

    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        byte numOfBytes = in.readByte();
        long value = 0;
        if (numOfBytes >= 8) value |= ((long)in.readByte() & 255) << 56;
        if (numOfBytes >= 7) value |= ((long)in.readByte() & 255) << 48;
        if (numOfBytes >= 6) value |= ((long)in.readByte() & 255) << 40;
        if (numOfBytes >= 5) value |= ((long)in.readByte() & 255) << 32;
        if (numOfBytes >= 4) value |= ((long)in.readByte() & 255) << 24;
        if (numOfBytes >= 3) value |= ((long)in.readByte() & 255) << 16;
        if (numOfBytes >= 2) value |= ((long)in.readByte() & 255) << 8;
        if (numOfBytes >= 1) value |= ((long)in.readByte() & 255);
        setValue(value);
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
            ExampleVariableSizeLongKey key = new ExampleVariableSizeLongKey();
            key.deserialize(buffer,(int)dataVersion);
            return key;
        }

        /**
         * Serialize a data item including header to the output stream returning the size of the data written
         *
         * @param data         The data item to serialize
         * @param outputStream Output stream to write to
         */
        @Override
        public int serialize(ExampleVariableSizeLongKey data, SerializableDataOutputStream outputStream) throws IOException {
            data.serialize(outputStream);
            return 1 + computeNonZeroBytes(data.getKeyAsLong());
        }
    }
}
