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

import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_VARINT;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.merkledb.utilities.ProtoUtils;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
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
//        return ProtoWriterTools.sizeOfLong(FIELD_LEAFRECORD_PATH, data.getPath()) +
        return ProtoUtils.sizeOfTag(FIELD_LEAFRECORD_PATH, WIRE_TYPE_VARINT) +
                ProtoUtils.sizeOfUnsignedVarInt64(data.getPath()) +
                ProtoUtils.sizeOfBytes(FIELD_LEAFRECORD_KEY, keySerializer.getSerializedSize(data.getKey())) +
                ProtoUtils.sizeOfBytes(FIELD_LEAFRECORD_VALUE, valueSerializer.getSerializedSize(data.getValue()));
    }

    @Override
    public VirtualLeafRecord<K, V> deserialize(ReadableSequentialData in) throws IOException {
        final int pathTag = in.readVarInt(false);
        assert pathTag ==
                ((FIELD_LEAFRECORD_PATH.number() << ProtoParserTools.TAG_FIELD_OFFSET) | ProtoUtils.WIRE_TYPE_VARINT);
        final long path = in.readVarLong(false);
        final int keyTag = in.readVarInt(false);
        assert keyTag ==
                ((FIELD_LEAFRECORD_KEY.number() << ProtoParserTools.TAG_FIELD_OFFSET) | ProtoUtils.WIRE_TYPE_DELIMITED);
        final int keySize = in.readVarInt(false);
        final K key = keySerializer.deserialize(in);
        assert keySize == keySerializer.getSerializedSize(key);
        final int valueTag = in.readVarInt(false);
        assert valueTag ==
                ((FIELD_LEAFRECORD_VALUE.number() << ProtoParserTools.TAG_FIELD_OFFSET) | ProtoUtils.WIRE_TYPE_DELIMITED);
        final int valueSize = in.readVarInt(false);
        final V value = valueSerializer.deserialize(in);
        assert valueSize == valueSerializer.getSerializedSize(value);
        return new VirtualLeafRecord<>(path, key, value);
    }

    @Override
    public long deserializeKey(BufferedData dataItemData) {
        return dataItemData.getLong(0);
    }

    @Override
    public void serialize(VirtualLeafRecord<K, V> leafRecord, WritableSequentialData out) throws IOException {
//        ProtoWriterTools.writeLong(out, FIELD_LEAFRECORD_PATH, leafRecord.getPath());
        ProtoUtils.writeTag(out, FIELD_LEAFRECORD_PATH);
        out.writeVarLong(leafRecord.getPath(), false);
        ProtoUtils.writeBytes(out, FIELD_LEAFRECORD_KEY, keySerializer.getSerializedSize(leafRecord.getKey()),
                o -> keySerializer.serialize(leafRecord.getKey(), o));
        ProtoUtils.writeBytes(out, FIELD_LEAFRECORD_VALUE, valueSerializer.getSerializedSize(leafRecord.getValue()),
                o -> valueSerializer.serialize(leafRecord.getValue(), o));
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
