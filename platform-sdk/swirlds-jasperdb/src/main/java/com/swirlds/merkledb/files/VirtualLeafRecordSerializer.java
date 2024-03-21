/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Serializer to store and read virtual leaf records in MerkleDb data files.
 *
 * <p>Protobuf schema:
 *
 * <pre>
 * message LeafRecord {
 *
 *     // Virtual node path
 *     optional uint64 path = 1;
 *
 *     // Virtual key
 *     bytes key = 2;
 *
 *     // Virtual value
 *     bytes value = 3;
 * }
 * </pre>
 */
public class VirtualLeafRecordSerializer<K extends VirtualKey, V extends VirtualValue>
        implements DataItemSerializer<VirtualLeafRecord<K, V>> {

    static final FieldDefinition FIELD_LEAFRECORD_PATH =
            new FieldDefinition("path", FieldType.FIXED64, false, true, false, 1);
    static final FieldDefinition FIELD_LEAFRECORD_KEY =
            new FieldDefinition("key", FieldType.BYTES, false, true, false, 2);
    static final FieldDefinition FIELD_LEAFRECORD_VALUE =
            new FieldDefinition("value", FieldType.BYTES, false, true, false, 3);

    /**
     * The digest type to use for Virtual hashes, if this is changed then serialized version need
     * to change
     */
    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;

    private final long currentVersion;

    private final KeySerializer<K> keySerializer;

    private final ValueSerializer<V> valueSerializer;

    @Deprecated(forRemoval = true)
    private final int dataItemSerializedSize;

    private final int hashSize;

    public VirtualLeafRecordSerializer(MerkleDbTableConfig<K, V> tableConfig) {
        currentVersion = ((0x000000000000FFFFL & tableConfig.getKeyVersion()) << 16)
                | ((0x000000000000FFFFL & tableConfig.getValueVersion()) << 32);
        keySerializer = tableConfig.getKeySerializer();
        valueSerializer = tableConfig.getValueSerializer();
        final boolean variableSize = keySerializer.isVariableSize() || valueSerializer.isVariableSize();
        dataItemSerializedSize = variableSize
                ? VARIABLE_DATA_SIZE
                : (Long.BYTES // path
                        + keySerializer.getSerializedSize() // key
                        + valueSerializer.getSerializedSize()); // value
        // for backwards compatibility
        hashSize = tableConfig.getHashType().digestLength();
    }

    @Override
    public long getCurrentDataVersion() {
        return currentVersion;
    }

    @Override
    @Deprecated(forRemoval = true)
    public int getSerializedSize() {
        return dataItemSerializedSize;
    }

    @Override
    public int getSerializedSizeForVersion(long version) {
        final int hashSerializationVersion = (int) (0x000000000000FFFFL & version);
        if (hashSerializationVersion != 0 && !isVariableSize()) {
            return getSerializedSize() + hashSize;
        }
        return getSerializedSize();
    }

    @Override
    public int getSerializedSize(@NonNull final VirtualLeafRecord<K, V> data) {
        int size = 0;
        if (data.getPath() != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_LEAFRECORD_PATH, ProtoConstants.WIRE_TYPE_FIXED_64_BIT)
                    + Long.BYTES;
        }
        size += ProtoWriterTools.sizeOfDelimited(FIELD_LEAFRECORD_KEY, keySerializer.getSerializedSize(data.getKey()));
        size += ProtoWriterTools.sizeOfDelimited(
                FIELD_LEAFRECORD_VALUE, valueSerializer.getSerializedSize(data.getValue()));
        return size;
    }

    @Override
    @Deprecated(forRemoval = true)
    public int getHeaderSize() {
        final boolean variableSize = keySerializer.isVariableSize() || valueSerializer.isVariableSize();
        return Long.BYTES + (variableSize ? Integer.BYTES : 0);
    }

    @Override
    @Deprecated(forRemoval = true)
    public DataItemHeader deserializeHeader(final ByteBuffer buffer) {
        // path is used as data item key
        final long path = buffer.getLong();
        final int size = isVariableSize() ? buffer.getInt() : getSerializedSize();
        return new DataItemHeader(size, path);
    }

    @Override
    public void serialize(
            @NonNull final VirtualLeafRecord<K, V> leafRecord, @NonNull final WritableSequentialData out) {
        if (leafRecord.getPath() != 0) {
            ProtoWriterTools.writeTag(out, FIELD_LEAFRECORD_PATH);
            out.writeLong(leafRecord.getPath());
        }
        ProtoWriterTools.writeDelimited(
                out,
                FIELD_LEAFRECORD_KEY,
                keySerializer.getSerializedSize(leafRecord.getKey()),
                o -> keySerializer.serialize(leafRecord.getKey(), o));
        ProtoWriterTools.writeDelimited(
                out,
                FIELD_LEAFRECORD_VALUE,
                valueSerializer.getSerializedSize(leafRecord.getValue()),
                o -> valueSerializer.serialize(leafRecord.getValue(), o));
    }

    @Override
    @Deprecated(forRemoval = true)
    public void serialize(VirtualLeafRecord<K, V> leafRecord, ByteBuffer buffer) throws IOException {
        final int initialPos = buffer.position();
        // path
        buffer.putLong(leafRecord.getPath());
        // total length, if variable size
        if (isVariableSize()) {
            buffer.putInt(0); // will be updated below
        }
        // key
        keySerializer.serialize(leafRecord.getKey(), buffer);
        valueSerializer.serialize(leafRecord.getValue(), buffer);

        if (isVariableSize()) {
            final int finalPos = buffer.position();
            final int totalSize = finalPos - initialPos;
            buffer.putInt(initialPos + Long.BYTES, totalSize);
        }
    }

    @Override
    public VirtualLeafRecord<K, V> deserialize(@NonNull final ReadableSequentialData in) {
        // default values
        long path = 0;
        K key = null;
        V value = null;

        // read fields, they may be missing or in any order
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            if (fieldNum == FIELD_LEAFRECORD_PATH.number()) {
                path = readPath(in);
            } else if (fieldNum == FIELD_LEAFRECORD_KEY.number()) {
                key = readKey(in);
            } else if (fieldNum == FIELD_LEAFRECORD_VALUE.number()) {
                value = readValue(in);
            } else {
                throw new IllegalArgumentException("Unknown virtual leaf record field: " + fieldNum);
            }
        }

        return new VirtualLeafRecord<>(path, key, value);
    }

    private long readPath(final ReadableSequentialData in) {
        final long path = in.readLong();
        return path;
    }

    private K readKey(final ReadableSequentialData in) {
        final int keySize = in.readVarInt(false);
        final long limit = in.limit();
        in.limit(in.position() + keySize);
        final K key = keySerializer.deserialize(in);
        in.limit(limit);
        assert keySize == keySerializer.getSerializedSize(key);
        return key;
    }

    private V readValue(final ReadableSequentialData in) {
        final int valueSize = in.readVarInt(false);
        final long limit = in.limit();
        in.limit(in.position() + valueSize);
        final V value = valueSerializer.deserialize(in);
        in.limit(limit);
        assert valueSize == valueSerializer.getSerializedSize(value);
        return value;
    }

    @Override
    @Deprecated(forRemoval = true)
    public VirtualLeafRecord<K, V> deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        final int hashSerializationVersion = (int) (0x000000000000FFFFL & dataVersion);
        final int keySerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 16));
        final int valueSerializationVersion = (int) (0x000000000000FFFFL & (dataVersion >>> 32));
        final DataItemHeader dataItemHeader = deserializeHeader(buffer);
        // deserialize path from the header
        final long path = dataItemHeader.getKey();

        if (hashSerializationVersion != 0) {
            // compatibility: read hash
            buffer.position(buffer.position() + DEFAULT_DIGEST.digestLength());
        }
        // deserialize key
        final K key = keySerializer.deserialize(buffer, keySerializationVersion);
        // deserialize value
        final V value = valueSerializer.deserialize(buffer, valueSerializationVersion);
        // return new VirtualLeafRecord
        return new VirtualLeafRecord<>(path, key, value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof VirtualLeafRecordSerializer<?, ?> that)) {
            return false;
        }
        return Objects.equals(keySerializer, that.keySerializer)
                && Objects.equals(valueSerializer, that.valueSerializer);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(keySerializer, valueSerializer);
    }
}
