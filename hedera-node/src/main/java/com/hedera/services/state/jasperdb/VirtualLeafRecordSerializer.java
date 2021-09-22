package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.files.DataFileOutputStream;
import com.hedera.services.state.jasperdb.files.DataItemHeader;
import com.hedera.services.state.jasperdb.files.DataItemSerializer;
import com.hedera.services.state.jasperdb.utilities.HashTools;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.VARIABLE_DATA_SIZE;
import static com.hedera.services.state.jasperdb.utilities.HashTools.byteBufferToHash;

/**
 *
 * @param <K>
 * @param <V>
 */
@SuppressWarnings("DuplicatedCode")
class VirtualLeafRecordSerializer<K extends VirtualKey, V extends VirtualValue> implements DataItemSerializer<VirtualLeafRecord<K, V>> {
    /** The current serialization version for hash, key and value */
    private final long currentVersion;
    /** Constructor for creating new key objects during de-serialization */
    private final Supplier<K> keyConstructor;
    /** Constructor for creating new value objects during de-serialization */
    private final Supplier<V> valueConstructor;
    /** The size in bytes for serialized key objects */
    private final int keySizeBytes;
    /** The size in bytes for serialized value objects */
    private final int valueSizeBytes;
    /** computed based on keySizeBytes or valueSizeBytes == DataFileCommon.VARIABLE_DATA_SIZE */
    private final boolean hasVariableDataSize;
    /** Total size for serialized data for hash, key and value */
    private final int totalSerializedSize;
    /** The size of the header in bytes */
    private final int headerSize;
    /** DataFileOutputStream needed when we are writing variable sized data */
    private final ThreadLocal<DataFileOutputStream> dataOutputStream;
    /** True for when max serialized size can fit in one byte */
    private boolean byteMaxSize;

    /**
     * Contruct a new VirtualLeafRecordSerializer
     *
     * @param hashSerializationVersion The serialization version for hash, less than 65,536 // TODO accounting for digest as well
     * @param hashDigest The digest uses for hashes
     * @param keySerializationVersion The serialization version for key, less than 65,536
     * @param keySizeBytes The number of bytes used by a serialized keu, can be DataFileCommon.VARIABLE_DATA_SIZE
     * @param keyConstructor Constructor for creating new key instances during deserialization
     * @param valueSerializationVersion The serialization version for value, less than 65,536
     * @param valueSizeBytes The number of bytes used by a serialized value, can be DataFileCommon.VARIABLE_DATA_SIZE
     * @param valueConstructor Constructor for creating new value instances during deserialization
     * @param maxKeyValueSizeLessThan198 Is max size of serialized key and value is less than (255-(1+8+48)) = 198
     */
    public VirtualLeafRecordSerializer(int hashSerializationVersion, DigestType hashDigest,
                                       int keySerializationVersion, int keySizeBytes, Supplier<K> keyConstructor,
                                       int valueSerializationVersion, int valueSizeBytes, Supplier<V> valueConstructor,
                                       boolean maxKeyValueSizeLessThan198) {
        this.currentVersion = (0x000000000000FFFFL & hashSerializationVersion) |
                ((0x000000000000FFFFL & keySerializationVersion) << 16) |
                ((0x000000000000FFFFL & valueSerializationVersion) << 32);
        this.keyConstructor = keyConstructor;
        this.valueConstructor = valueConstructor;
        this.keySizeBytes = keySizeBytes;
        this.valueSizeBytes = valueSizeBytes;
        this.byteMaxSize = maxKeyValueSizeLessThan198;
        this.hasVariableDataSize =  keySizeBytes == VARIABLE_DATA_SIZE || valueSizeBytes == VARIABLE_DATA_SIZE;
        this.totalSerializedSize = hasVariableDataSize ? VARIABLE_DATA_SIZE :
                (Long.BYTES + HashTools.DEFAULT_DIGEST.digestLength() + keySizeBytes + valueSizeBytes);
        this.dataOutputStream = ThreadLocal.withInitial(
                () -> new DataFileOutputStream(this.getTypicalSerializedSize())
        );
        this.headerSize = Long.BYTES + (byteMaxSize ? 1 : Integer.BYTES);
    }

    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return headerSize;
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        final int size;
        if (isVariableSize()) {
            if (byteMaxSize) {
                size = buffer.get();
            } else {
                size = buffer.getInt();
            }
        } else {
            size = totalSerializedSize;
        }
        final long key = buffer.getLong();
        return new DataItemHeader(size, key);
    }

    /**
     * Get if the number of bytes a data item takes when serialized is variable or fixed
     *
     * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
     */
    @Override
    public boolean isVariableSize() {
        return hasVariableDataSize;
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return totalSerializedSize;
    }

    /**
     * For variable sized data get the typical  number of bytes a data item takes when serialized
     *
     * @return Either for fixed size same as getSerializedSize() or a estimated typical size for data items
     */
    @Override
    public int getTypicalSerializedSize() {
        return hasVariableDataSize ? 1024 : totalSerializedSize; // TODO something better
    }

    /**
     * Get the current data item serialization version
     */
    @Override
    public long getCurrentDataVersion() {
        return currentVersion;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer      The buffer to read from
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public VirtualLeafRecord<K, V> deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        final int hashSerializationVersion = (int) (0x000000000000FFFFL & dataVersion);
        final int keySerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 16));
        final int valueSerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 32));
        DataItemHeader dataItemHeader = deserializeHeader(buffer);
        // deserialize path
        long path = dataItemHeader.getKey();
        // deserialize hash
        final Hash hash = byteBufferToHash(buffer, hashSerializationVersion);
        // deserialize key
        final K key = keyConstructor.get();
        key.deserialize(buffer, keySerializationVersion);
        // deserialize value
        final V value = valueConstructor.get();
        value.deserialize(buffer, valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, hash, key, value);
    }

    /**
     * Serialize a data item including header to the output stream returning the size of the data written
     *
     * @param leafRecord   The virtual record data item to serialize
     * @param outputStream Output stream to write to
     */
    @Override
    public int serialize(VirtualLeafRecord<K, V> leafRecord, SerializableDataOutputStream outputStream) throws IOException {
        final SerializableDataOutputStream dataOutputStream = hasVariableDataSize ? this.dataOutputStream.get().reset() : outputStream;
        // put path (data item key)
        dataOutputStream.writeLong(leafRecord.getPath());
        // put hash
        dataOutputStream.write(leafRecord.getHash().getValue());
        // put key
        leafRecord.getKey().serialize(dataOutputStream);
        // put value
        leafRecord.getValue().serialize(dataOutputStream);
        // get bytes written and write buffer if needed
        int bytesWritten;
        if (hasVariableDataSize) {
            dataOutputStream.flush();
            bytesWritten = + ((DataFileOutputStream)dataOutputStream).bytesWritten();
            // write size to stream
            if (byteMaxSize) {
                bytesWritten += 1;
                outputStream.write((byte)bytesWritten);
            } else {
                bytesWritten += Integer.BYTES;
                outputStream.writeInt(bytesWritten);
            }
            // write buffered serialized data to stream
            ((DataFileOutputStream)dataOutputStream).writeTo(outputStream);
        } else {
            bytesWritten = totalSerializedSize;
        }
        return bytesWritten;
    }
}
