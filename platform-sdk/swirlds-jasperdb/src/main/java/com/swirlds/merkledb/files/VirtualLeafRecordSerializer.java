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

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;
import static com.swirlds.jasperdb.utilities.HashTools.DEFAULT_DIGEST;
import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_VARINT;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.merkledb.utilities.ProtoUtils;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class VirtualLeafRecordSerializer<K extends VirtualKey, V extends VirtualValue>
        implements DataItemSerializer<VirtualLeafRecord<K, V>> {

    static final FieldDefinition FIELD_LEAFRECORD_PATH =
            new FieldDefinition("path", FieldType.UINT64, false, true, false, 1);
    static final FieldDefinition FIELD_LEAFRECORD_KEY =
            new FieldDefinition("key", FieldType.BYTES, false, true, false, 2);
    static final FieldDefinition FIELD_LEAFRECORD_VALUE =
            new FieldDefinition("value", FieldType.BYTES, false, true, false, 3);

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
    public int getSerializedSize(VirtualLeafRecord<K, V> data) {
        int size = 0;
        if (data.getPath() != 0) {
            size += ProtoUtils.sizeOfTag(FIELD_LEAFRECORD_PATH, WIRE_TYPE_VARINT) +
                    ProtoUtils.sizeOfUnsignedVarInt64(data.getPath());
        }
        size += ProtoUtils.sizeOfDelimited(FIELD_LEAFRECORD_KEY, keySerializer.getSerializedSize(data.getKey()));
        size += ProtoUtils.sizeOfDelimited(FIELD_LEAFRECORD_VALUE, valueSerializer.getSerializedSize(data.getValue()));
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
    public void serialize(VirtualLeafRecord<K, V> leafRecord, WritableSequentialData out) {
        if (leafRecord.getPath() != 0) {
            ProtoUtils.writeTag(out, FIELD_LEAFRECORD_PATH);
            out.writeVarLong(leafRecord.getPath(), false);
        }
        ProtoUtils.writeBytes(out, FIELD_LEAFRECORD_KEY, keySerializer.getSerializedSize(leafRecord.getKey()),
                o -> keySerializer.serialize(leafRecord.getKey(), o));
        ProtoUtils.writeBytes(out, FIELD_LEAFRECORD_VALUE, valueSerializer.getSerializedSize(leafRecord.getValue()),
                o -> valueSerializer.serialize(leafRecord.getValue(), o));
    }

    @Override
    @Deprecated(forRemoval = true)
    public int serialize(VirtualLeafRecord<K, V> leafRecord, ByteBuffer buffer) throws IOException {
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

        final int totalSize;
        if (isVariableSize()) {
            final int finalPos = buffer.position();
            buffer.position(initialPos + Long.BYTES);
            totalSize = finalPos - initialPos;
            buffer.putInt(totalSize);
            buffer.position(finalPos);
        } else {
            totalSize = dataItemSerializedSize;
        }

        return totalSize;
    }

    @Override
    public VirtualLeafRecord<K, V> deserialize(ReadableSequentialData in) {
        // default values
        long path = 0;
        K key = null;
        V value = null;

        // read fields, they may be missing or in any order
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int number = tag >> TAG_FIELD_OFFSET;
            if (number == FIELD_LEAFRECORD_PATH.number()) {
                path = readPath(in);
            } else if (number == FIELD_LEAFRECORD_KEY.number()) {
                key = readKey(in);
            } else if (number == FIELD_LEAFRECORD_VALUE.number()) {
                value = readValue(in);
            } else {
                throw new IllegalArgumentException("Unknown virtual leaf record field: " + number);
            }
        }

        return new VirtualLeafRecord<>(path, key, value);
    }

    private long readPath(final ReadableSequentialData in) {
        final long path = in.readVarLong(false);
        return path;
    }

    private K readKey(final ReadableSequentialData in) {
        final int keySize = in.readVarInt(false);
        final K key = keySerializer.deserialize(in);
        assert keySize == keySerializer.getSerializedSize(key);
        return key;
    }

    private V readValue(final ReadableSequentialData in) {
        final int valueSize = in.readVarInt(false);
        final V value = valueSerializer.deserialize(in);
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

    @Override
    public long extractKey(BufferedData dataItemData) {
        return dataItemData.getLong(0);
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
