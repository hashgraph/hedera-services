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

package com.swirlds.merkledb;

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * Virtual database table configuration. It describes how to store virtual keys and values
 * and a few other params like whether to prefer disk based indexes or not. These table
 * configs are stored in virtual database metadata and persisted across JVM runs.
 *
 * @param <K>
 *     Virtual key type
 * @param <V>
 *     Virtual value type
 */
// FUTURE WORK: remove K and V types
// FUTURE WORK: don't implement SelfSerializable
public final class MerkleDbTableConfig<K extends VirtualKey, V extends VirtualValue> implements SelfSerializable {

    private static final long CLASS_ID = 0xbb41e7eb9fcad23cL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_HASHVERSION =
            new FieldDefinition("hashVersion", FieldType.UINT32, false, true, false, 1);
    private static final FieldDefinition FIELD_TABLECONFIG_DIGESTTYPEID =
            new FieldDefinition("digestTypeId", FieldType.UINT32, false, false, false, 2);
    // Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_KEYVERSION =
            new FieldDefinition("keyVersion", FieldType.UINT32, false, true, false, 3);
    // Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_KEYSERIALIZERCLSID =
            new FieldDefinition("keySerializerClassId", FieldType.UINT64, false, false, false, 4);
    // Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_VALUEVERSION =
            new FieldDefinition("valueVersion", FieldType.UINT32, false, true, false, 5);
    // Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_VALUESERIALIZERCLSID =
            new FieldDefinition("valueSerializerClassId", FieldType.UINT64, false, false, false, 6);
    private static final FieldDefinition FIELD_TABLECONFIG_PREFERDISKINDICES =
            new FieldDefinition("preferDiskIndices", FieldType.UINT32, false, true, false, 7);
    private static final FieldDefinition FIELD_TABLECONFIG_MAXNUMBEROFKEYS =
            new FieldDefinition("maxNumberOfKeys", FieldType.UINT64, false, true, false, 8);
    private static final FieldDefinition FIELD_TABLECONFIG_HASHRAMTODISKTHRESHOLD =
            new FieldDefinition("hashesRamToDiskThreshold", FieldType.UINT64, false, true, false, 9);

    /**
     * Hash type. Only SHA_384 is supported yet.
     */
    private DigestType hashType;

    // This field is only used, when a legacy state (with SelfSerializable objects) is loaded from disk.
    // When such state is serialized again, it is written in protobuf format, and keySerializer and
    // valueSerializer fields aren't written.
    @Deprecated
    private KeySerializer<K> keySerializer;

    // This field is only used, when a legacy state (with SelfSerializable objects) is loaded from disk.
    // When such state is serialized again, it is written in protobuf format, and keySerializer and
    // valueSerializer fields aren't written.
    @Deprecated
    private ValueSerializer<V> valueSerializer;

    /**
     * Max number of keys that can be stored in a table.
     */
    private long maxNumberOfKeys = ConfigurationHolder.getConfigData(MerkleDbConfig.class).maxNumOfKeys();

    /**
     * Threshold where we switch from storing internal hashes in ram to storing them on disk. If it is 0 then everything
     * is on disk, if it is Long.MAX_VALUE then everything is in ram. Any value in the middle is the path value at which
     * we swap from ram to disk. This allows a tree where the lower levels of the tree nodes hashes are in ram and the
     * upper larger less changing layers are on disk.
     */
    private long hashesRamToDiskThreshold = ConfigurationHolder.getConfigData(MerkleDbConfig.class).hashesRamToDiskThreshold();

    /**
     * Indicates whether to store indexes on disk or in Java heap/off-heap memory.
     */
    private boolean preferDiskBasedIndices = false;

    /**
     * Creates a new virtual table config with default values.
     */
    public MerkleDbTableConfig() {
        this.hashType = DigestType.SHA_384;
    }

    @Deprecated(forRemoval = true)
    public MerkleDbTableConfig(
            final short hashVersion,
            final DigestType hashType,
            final short keyVersion,
            @NonNull final KeySerializer<K> keySerializer,
            final short valueVersion,
            @NonNull final ValueSerializer<V> valueSerializer) {
        this.hashType = hashType;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    public MerkleDbTableConfig(
            final DigestType hashType,
            final long maxNumberOfKeys,
            final long hashesRamToDiskThreshold,
            final boolean preferDiskBasedIndices) {
        this.hashType = hashType;
        this.maxNumberOfKeys = maxNumberOfKeys;
        this.hashesRamToDiskThreshold = hashesRamToDiskThreshold;
        this.preferDiskBasedIndices = preferDiskBasedIndices;
    }

    public MerkleDbTableConfig(final ReadableSequentialData in) {
        // Defaults. If a field is missing in the input, a default protobuf value is used
        // (zero, false, null, etc.) rather than a default value from MerkleDb config. The
        // config is used for defaults when a new table config is created, but when an
        // existing config is loaded, only values from the input must be used (even if some
        // of them are protobuf default and aren't present)
        hashType = DigestType.SHA_384;
        maxNumberOfKeys = 0;
        hashesRamToDiskThreshold = 0;
        preferDiskBasedIndices = false;

        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            if (fieldNum == FIELD_TABLECONFIG_HASHVERSION.number()) {
                // Skip hash version
                in.readVarInt(false);
            } else if (fieldNum == FIELD_TABLECONFIG_DIGESTTYPEID.number()) {
                final int digestTypeId = in.readVarInt(false);
                hashType = DigestType.valueOf(digestTypeId);
            } else if (fieldNum == FIELD_TABLECONFIG_KEYVERSION.number()) {
                // Skip key version
                in.readVarInt(false);
            } else if (fieldNum == FIELD_TABLECONFIG_KEYSERIALIZERCLSID.number()) {
                // Skip key serializer class ID
                in.readVarLong(false);
            } else if (fieldNum == FIELD_TABLECONFIG_VALUEVERSION.number()) {
                // Skip value version
                in.readVarInt(false);
            } else if (fieldNum == FIELD_TABLECONFIG_VALUESERIALIZERCLSID.number()) {
                // Skip value serializer class ID
                in.readVarLong(false);
            } else if (fieldNum == FIELD_TABLECONFIG_PREFERDISKINDICES.number()) {
                preferDiskBasedIndices = in.readVarInt(false) != 0;
            } else if (fieldNum == FIELD_TABLECONFIG_MAXNUMBEROFKEYS.number()) {
                maxNumberOfKeys = in.readVarLong(false);
            } else if (fieldNum == FIELD_TABLECONFIG_HASHRAMTODISKTHRESHOLD.number()) {
                hashesRamToDiskThreshold = in.readVarLong(false);
            } else {
                throw new IllegalArgumentException("Unknown table config field: " + fieldNum);
            }
        }

        // Check that all mandatory fields have been loaded from the stream
        Objects.requireNonNull(hashType, "Null or wrong hash type");
        if (maxNumberOfKeys <= 0) {
            throw new IllegalArgumentException("Missing or wrong max number of keys");
        }
    }

    public int pbjSizeInBytes() {
        int size = 0;
        size += ProtoWriterTools.sizeOfTag(FIELD_TABLECONFIG_DIGESTTYPEID, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG);
        size += ProtoWriterTools.sizeOfVarInt32(hashType.id());
        if (preferDiskBasedIndices) {
            size += ProtoWriterTools.sizeOfTag(
                    FIELD_TABLECONFIG_PREFERDISKINDICES, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG);
            size += ProtoWriterTools.sizeOfVarInt32(1);
        }
        assert maxNumberOfKeys != 0;
        size += ProtoWriterTools.sizeOfTag(
                FIELD_TABLECONFIG_MAXNUMBEROFKEYS, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG);
        size += ProtoWriterTools.sizeOfVarInt64(maxNumberOfKeys);
        if (hashesRamToDiskThreshold != 0) {
            size += ProtoWriterTools.sizeOfTag(
                    FIELD_TABLECONFIG_HASHRAMTODISKTHRESHOLD, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG);
            size += ProtoWriterTools.sizeOfVarInt64(hashesRamToDiskThreshold);
        }
        return size;
    }

    public void writeTo(final WritableSequentialData out) {
        ProtoWriterTools.writeTag(out, FIELD_TABLECONFIG_DIGESTTYPEID);
        out.writeVarInt(hashType.id(), false);
        if (preferDiskBasedIndices) {
            ProtoWriterTools.writeTag(out, FIELD_TABLECONFIG_PREFERDISKINDICES);
            out.writeVarInt(1, false);
        }
        assert maxNumberOfKeys != 0;
        ProtoWriterTools.writeTag(out, FIELD_TABLECONFIG_MAXNUMBEROFKEYS);
        out.writeVarLong(maxNumberOfKeys, false);
        if (hashesRamToDiskThreshold != 0) {
            ProtoWriterTools.writeTag(out, FIELD_TABLECONFIG_HASHRAMTODISKTHRESHOLD);
            out.writeVarLong(hashesRamToDiskThreshold, false);
        }
    }

    /**
     * Hash type.
     *
     * @return
     *      Hash type
     */
    public DigestType getHashType() {
        return hashType;
    }

    /**
     * Key serializer.
     *
     * @return
     *      Key serializer
     */
    @Deprecated
    KeySerializer<K> getKeySerializer() {
        return keySerializer;
    }

    /**
     * Value serializer
     *
     * @return
     *      Value serializer
     */
    @Deprecated
    ValueSerializer<V> getValueSerializer() {
        return valueSerializer;
    }

    /**
     * Max number of keys that can be stored in the table.
     *
     * @return
     *      Max number of keys
     */
    public long getMaxNumberOfKeys() {
        return maxNumberOfKeys;
    }

    /**
     * Specifies the max number of keys that can be stored in the table. Must be greater than zero.
     *
     * @param maxNumberOfKeys
     *      Max number of keys
     * @return
     *      This table config object
     */
    public MerkleDbTableConfig<K, V> maxNumberOfKeys(final long maxNumberOfKeys) {
        if (maxNumberOfKeys <= 0) {
            throw new IllegalArgumentException("Max number of keys must be greater than 0");
        }
        this.maxNumberOfKeys = maxNumberOfKeys;
        return this;
    }

    /**
     * Internal hashes RAM/disk threshold. Value {@code 0} means all hashes are to be stored on disk.
     * Value {@link Integer#MAX_VALUE} indicates that all hashes are to be stored in memory.
     *
     * @return
     *      Internal hashes RAM/disk threshold
     */
    public long getHashesRamToDiskThreshold() {
        return hashesRamToDiskThreshold;
    }

    /**
     * Specifies internal hashes RAM/disk threshold. Must be greater or equal to zero.
     *
     * @param hashesRamToDiskThreshold
     *      Internal hashes RAM/disk threshold
     * @return
     *      This table config object
     */
    public MerkleDbTableConfig<K, V> hashesRamToDiskThreshold(final long hashesRamToDiskThreshold) {
        if (hashesRamToDiskThreshold < 0) {
            throw new IllegalArgumentException("Hashes RAM/disk threshold must be greater or equal to 0");
        }
        this.hashesRamToDiskThreshold = hashesRamToDiskThreshold;
        return this;
    }

    /**
     * Whether indexes are stored on disk or in Java heap/off-heap memory.
     *
     * @return
     *      Whether disk based indexes are preferred
     */
    public boolean isPreferDiskBasedIndices() {
        return preferDiskBasedIndices;
    }

    /**
     * Specifies whether indexes are to be stored on disk or in Java heap/off-heap memory.
     *
     * @param preferDiskBasedIndices
     *      Whether disk based indexes are preferred
     * @return
     *      This table config object
     */
    public MerkleDbTableConfig<K, V> preferDiskIndices(final boolean preferDiskBasedIndices) {
        this.preferDiskBasedIndices = preferDiskBasedIndices;
        return this;
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
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        throw new UnsupportedOperationException("This method should no longer be used");
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        preferDiskBasedIndices = in.readBoolean();
        maxNumberOfKeys = in.readLong();
        hashesRamToDiskThreshold = in.readLong();
        in.readShort(); // skip hash version
        hashType = DigestType.valueOf(in.readInt());
        in.readShort(); // skip key version
        keySerializer = in.readSerializable();
        in.readShort(); // skip value version
        valueSerializer = in.readSerializable();
    }

    /**
     * Creates a copy of this table config.
     *
     * @return Table config copy
     */
    public MerkleDbTableConfig<K, V> copy() {
        return new MerkleDbTableConfig<>(hashType, maxNumberOfKeys, hashesRamToDiskThreshold, preferDiskBasedIndices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                hashType,
                preferDiskBasedIndices,
                maxNumberOfKeys,
                hashesRamToDiskThreshold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof MerkleDbTableConfig<?, ?> other)) {
            return false;
        }
        return (preferDiskBasedIndices == other.preferDiskBasedIndices)
                && (maxNumberOfKeys == other.maxNumberOfKeys)
                && (hashesRamToDiskThreshold == other.hashesRamToDiskThreshold)
                && Objects.equals(hashType, other.hashType);
    }
}
