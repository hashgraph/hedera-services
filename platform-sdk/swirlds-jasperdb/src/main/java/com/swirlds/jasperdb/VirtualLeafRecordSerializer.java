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

package com.swirlds.jasperdb;

import static com.swirlds.jasperdb.files.DataFileCommon.VARIABLE_DATA_SIZE;
import static com.swirlds.jasperdb.utilities.HashTools.byteBufferToHash;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileOutputStream;
import com.swirlds.jasperdb.files.DataItemHeader;
import com.swirlds.jasperdb.files.DataItemSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * VirtualLeafRecordSerializer serializer responsible for serializing and deserializing virtual leaf records. It depends
 * on the serialization implementations of the VirtualKey and VirtualValue.
 *
 * @param <K>
 * 		VirtualKey type
 * @param <V>
 * 		VirtualValue type
 */
@SuppressWarnings("DuplicatedCode")
public class VirtualLeafRecordSerializer<K extends VirtualKey<? super K>, V extends VirtualValue>
        implements DataItemSerializer<VirtualLeafRecord<K, V>>, SelfSerializable {

    private static final long CLASS_ID = 0x39f4704ad17104fL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int NO_HASHES = 2;
    }

    private static final int DEFAULT_TYPICAL_VARIABLE_SIZE = 1024;
    /** DataFileOutputStream needed when we are writing variable sized data */
    private static final ThreadLocal<DataFileOutputStream> DATA_FILE_OUTPUT_STREAM_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new DataFileOutputStream(DEFAULT_TYPICAL_VARIABLE_SIZE));

    /** The current serialization version for hash, key and value */
    private long currentVersion;
    /** Constructor for creating new key objects during de-serialization */
    private SelfSerializableSupplier<K> keyConstructor;
    /** Constructor for creating new value objects during de-serialization */
    private SelfSerializableSupplier<V> valueConstructor;
    /** computed based on keySizeBytes or valueSizeBytes == DataFileCommon.VARIABLE_DATA_SIZE */
    private boolean hasVariableDataSize;
    /** Total size for serialized data for hash, key and value */
    private int totalSerializedSize;
    /** The size of the header in bytes */
    private int headerSize;
    /** True for when max serialized size can fit in one byte */
    private boolean byteMaxSize;

    public VirtualLeafRecordSerializer() {}

    /**
     * Construct a new VirtualLeafRecordSerializer
     *
     * @param keySerializationVersion
     * 		The serialization version for key, less than 65,536
     * @param keySizeBytes
     * 		The number of bytes used by a serialized keu, can be DataFileCommon.VARIABLE_DATA_SIZE
     * @param keyConstructor
     * 		Constructor for creating new key instances during deserialization
     * @param valueSerializationVersion
     * 		The serialization version for value, less than 65,536
     * @param valueSizeBytes
     * 		The number of bytes used by a serialized value, can be DataFileCommon.VARIABLE_DATA_SIZE
     * @param valueConstructor
     * 		Constructor for creating new value instances during deserialization
     * @param maxKeyValueSizeLessThan198
     * 		Is max size of serialized key and value is less than (255-(1+8+48)) = 198
     */
    public VirtualLeafRecordSerializer(
            final short keySerializationVersion,
            final int keySizeBytes,
            final SelfSerializableSupplier<K> keyConstructor,
            final short valueSerializationVersion,
            final int valueSizeBytes,
            final SelfSerializableSupplier<V> valueConstructor,
            final boolean maxKeyValueSizeLessThan198) {
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3941 */
        this.currentVersion = ((0x000000000000FFFFL & keySerializationVersion) << 16)
                | ((0x000000000000FFFFL & valueSerializationVersion) << 32);
        this.keyConstructor = keyConstructor;
        this.valueConstructor = valueConstructor;
        this.byteMaxSize = maxKeyValueSizeLessThan198;
        this.hasVariableDataSize = keySizeBytes == VARIABLE_DATA_SIZE || valueSizeBytes == VARIABLE_DATA_SIZE;
        this.totalSerializedSize =
                hasVariableDataSize ? VARIABLE_DATA_SIZE : (Long.BYTES + keySizeBytes + valueSizeBytes);
        this.headerSize = Long.BYTES; // key
        if (hasVariableDataSize) {
            this.headerSize += (byteMaxSize ? 1 : Integer.BYTES); // size
        }
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
        return ClassVersion.NO_HASHES;
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
     * @param buffer
     * 		Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(final ByteBuffer buffer) {
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
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3938 */
        return hasVariableDataSize ? DEFAULT_TYPICAL_VARIABLE_SIZE : totalSerializedSize;
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
     * @param buffer
     * 		The buffer to read from
     * @param dataVersion
     * 		The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public VirtualLeafRecord<K, V> deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        final int hashSerializationVersion = (int) (0x000000000000FFFFL & dataVersion);
        final int keySerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 16));
        final int valueSerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 32));
        final DataItemHeader dataItemHeader = deserializeHeader(buffer);
        // deserialize path
        final long path = dataItemHeader.getKey();
        if (hashSerializationVersion != 0) {
            byteBufferToHash(buffer, hashSerializationVersion);
        }
        // deserialize key
        final K key = keyConstructor.get();
        key.deserialize(buffer, keySerializationVersion);
        // deserialize value
        final V value = valueConstructor.get();
        value.deserialize(buffer, valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, key, value);
    }

    /**
     * Serialize a data item including header to the output stream returning the size of the data written
     *
     * @param leafRecord
     * 		The virtual record data item to serialize
     * @param outputStream
     * 		Output stream to write to
     */
    @Override
    public int serialize(final VirtualLeafRecord<K, V> leafRecord, final SerializableDataOutputStream outputStream)
            throws IOException {
        final SerializableDataOutputStream serializableDataOutputStream =
                hasVariableDataSize ? DATA_FILE_OUTPUT_STREAM_THREAD_LOCAL.get().reset() : outputStream;
        // put path (data item key)
        serializableDataOutputStream.writeLong(leafRecord.getPath());
        // put key
        leafRecord.getKey().serialize(serializableDataOutputStream);
        // put value
        leafRecord.getValue().serialize(serializableDataOutputStream);
        // get bytes written and write buffer if needed
        int bytesWritten;
        if (hasVariableDataSize) {
            serializableDataOutputStream.flush();
            bytesWritten = ((DataFileOutputStream) serializableDataOutputStream).bytesWritten();
            // write size to stream
            if (byteMaxSize) {
                bytesWritten += 1;
                outputStream.write((byte) bytesWritten);
            } else {
                bytesWritten += Integer.BYTES;
                outputStream.writeInt(bytesWritten);
            }
            // write buffered serialized data to stream
            ((DataFileOutputStream) serializableDataOutputStream).writeTo(outputStream);
        } else {
            bytesWritten = totalSerializedSize;
        }
        return bytesWritten;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(currentVersion);
        out.writeSerializable(keyConstructor, true);
        out.writeSerializable(valueConstructor, true);
        out.writeBoolean(hasVariableDataSize);
        out.writeInt(totalSerializedSize);
        out.writeInt(headerSize);
        out.writeBoolean(byteMaxSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        currentVersion = in.readLong();
        keyConstructor = in.readSerializable();
        valueConstructor = in.readSerializable();
        hasVariableDataSize = in.readBoolean();
        totalSerializedSize = in.readInt();
        headerSize = in.readInt();
        byteMaxSize = in.readBoolean();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VirtualLeafRecordSerializer<?, ?> that = (VirtualLeafRecordSerializer<?, ?>) o;
        return currentVersion == that.currentVersion
                && hasVariableDataSize == that.hasVariableDataSize
                && totalSerializedSize == that.totalSerializedSize
                && headerSize == that.headerSize
                && byteMaxSize == that.byteMaxSize
                && Objects.equals(keyConstructor, that.keyConstructor)
                && Objects.equals(valueConstructor, that.valueConstructor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                currentVersion,
                keyConstructor,
                valueConstructor,
                hasVariableDataSize,
                totalSerializedSize,
                headerSize,
                byteMaxSize);
    }
}
