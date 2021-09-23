package com.hedera.services.state.jasperdb.files;

import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Very simple DataItem that is variable size and has a long key and number of long values. Designed for testing.
 *
 * Stores bytes size as a long so all data is longs, makes it easier to manually read file in tests
 */
public class ExampleVariableSizeDataSerializer implements DataItemSerializer<long[]> {

    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return Long.BYTES + Long.BYTES; // size bytes and key
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        return new DataItemHeader((int)buffer.getLong(),buffer.getLong());
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
     * For variable sized data get the typical  number of bytes a data item takes when serialized
     *
     * @return Either for fixed size same as getSerializedSize() or an estimated typical size for data items
     */
    @Override
    public int getTypicalSerializedSize() {
        return Long.BYTES*12;
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
    public long[] deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        int dataSize = (int)buffer.getLong(); // int stored as long
        int repeats = (dataSize - Long.BYTES) / Long.BYTES;
        long[] dataItem = new long[repeats];
        // read key and data longs
        for (int i = 0; i < dataItem.length; i++) {
            dataItem[i] = buffer.getLong();
        }
        return dataItem;
    }

    /**
     * Serialize a data item including header to the output stream returning the size of the data written
     *
     * @param data         The data item to serialize
     * @param outputStream Output stream to write to
     */
    @Override
    public int serialize(long[] data, SerializableDataOutputStream outputStream) throws IOException {
        Objects.requireNonNull(data);
        Objects.requireNonNull(outputStream);
        int dataSizeBytes = Long.BYTES + (Long.BYTES * data.length); // Size + data
        // write size
        outputStream.writeLong(dataSizeBytes);
        // write key and data
        for (long d : data) {
            outputStream.writeLong(d);
        }
        return dataSizeBytes;
    }
}
