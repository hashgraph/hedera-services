/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.utilities.HashTools.byteBufferToHash;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class VirtualLeafRecordSerializer<K extends VirtualKey<? super K>, V extends VirtualValue>
        implements DataItemSerializer<VirtualLeafRecord<K, V>> {

    private final long currentVersion;

    private final KeySerializer<K> keySerializer;

    private final ValueSerializer<V> valueSerializer;

    private final int dataItemSerializedSize;
    private final int headerSize;

    /** DataFileOutputStream needed when we are writing variable sized data */
    private static final ThreadLocal<DataFileOutputStream> DATA_FILE_OUTPUT_STREAM_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new DataFileOutputStream(1024));

    public VirtualLeafRecordSerializer(MerkleDbTableConfig<K, V> tableConfig) {
        currentVersion = (0x000000000000FFFFL & tableConfig.getHashVersion())
                | ((0x000000000000FFFFL & tableConfig.getKeyVersion()) << 16)
                | ((0x000000000000FFFFL & tableConfig.getValueVersion()) << 32);
        keySerializer = tableConfig.getKeySerializer();
        valueSerializer = tableConfig.getValueSerializer();
        final boolean variableSize = keySerializer.isVariableSize() || valueSerializer.isVariableSize();
        dataItemSerializedSize = variableSize
                ? VARIABLE_DATA_SIZE
                : (Long.BYTES // path
                        + tableConfig.getHashType().digestLength() // hash
                        + keySerializer.getSerializedSize() // key
                        + valueSerializer.getSerializedSize()); // value
        headerSize = Long.BYTES + (variableSize ? Integer.BYTES : 0);
    }

    @Override
    public long getCurrentDataVersion() {
        return currentVersion;
    }

    @Override
    public int getHeaderSize() {
        return headerSize;
    }

    @Override
    public DataItemHeader deserializeHeader(final ByteBuffer buffer) {
        // path is used as data item key
        final long path = buffer.getLong();
        final int size = isVariableSize() ? buffer.getInt() : getSerializedSize();
        return new DataItemHeader(size, path);
    }

    @Override
    public int getSerializedSize() {
        return dataItemSerializedSize;
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
        // deserialize path from the header
        final long path = dataItemHeader.getKey();
        // deserialize hash
        final Hash hash = byteBufferToHash(buffer, hashSerializationVersion);
        // deserialize key
        final K key = keySerializer.deserialize(buffer, keySerializationVersion);
        // deserialize value
        final V value = valueSerializer.deserialize(buffer, valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, hash, key, value);
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
        if (isVariableSize()) {
            final DataFileOutputStream variableStream =
                    DATA_FILE_OUTPUT_STREAM_THREAD_LOCAL.get().reset();
            variableStream.write(leafRecord.getHash().getValue());
            keySerializer.serialize(leafRecord.getKey(), variableStream);
            valueSerializer.serialize(leafRecord.getValue(), variableStream);
            variableStream.flush();

            int bytesWritten = variableStream.bytesWritten();
            bytesWritten += Long.BYTES; // path
            bytesWritten += Integer.BYTES; // size

            // header
            outputStream.writeLong(leafRecord.getPath());
            outputStream.writeInt(bytesWritten);
            // data
            variableStream.writeTo(outputStream);

            return bytesWritten;
        } else {
            outputStream.writeLong(leafRecord.getPath());
            outputStream.write(leafRecord.getHash().getValue());
            keySerializer.serialize(leafRecord.getKey(), outputStream);
            valueSerializer.serialize(leafRecord.getValue(), outputStream);
            return dataItemSerializedSize;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof VirtualLeafRecordSerializer<?, ?> that)) {
            return false;
        }
        return Objects.equals(keySerializer, that.keySerializer)
                && Objects.equals(valueSerializer, that.valueSerializer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(keySerializer, valueSerializer);
    }
}
