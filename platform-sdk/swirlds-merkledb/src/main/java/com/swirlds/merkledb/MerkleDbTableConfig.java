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
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import java.io.IOException;
import java.util.Objects;

/**
 * Virtual database table configuration. It describes how to store virtual keys and values
 * and a few other params like whether to prefer disk based indexes or not. These table
 * configs are stored in virtual database metadata and persisted across JVM runs.
 */
public final class MerkleDbTableConfig implements SelfSerializable {

    private static final long CLASS_ID = 0xbb41e7eb9fcad23cL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final FieldDefinition FIELD_TABLECONFIG_HASHVERSION =
            new FieldDefinition("hashVersion", FieldType.UINT32, false, true, false, 1);
    private static final FieldDefinition FIELD_TABLECONFIG_DIGESTTYPEID =
            new FieldDefinition("digestTypeId", FieldType.UINT32, false, false, false, 2);

    @Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_KEYVERSION =
            new FieldDefinition("keyVersion", FieldType.UINT32, false, true, false, 3);

    @Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_KEYSERIALIZERCLSID =
            new FieldDefinition("keySerializerClassId", FieldType.UINT64, false, false, false, 4);

    @Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_VALUEVERSION =
            new FieldDefinition("valueVersion", FieldType.UINT32, false, true, false, 5);

    @Deprecated
    private static final FieldDefinition FIELD_TABLECONFIG_VALUESERIALIZERCLSID =
            new FieldDefinition("valueSerializerClassId", FieldType.UINT64, false, false, false, 6);

    private static final FieldDefinition FIELD_TABLECONFIG_PREFERDISKINDICES =
            new FieldDefinition("preferDiskIndices", FieldType.UINT32, false, true, false, 7);
    private static final FieldDefinition FIELD_TABLECONFIG_MAXNUMBEROFKEYS =
            new FieldDefinition("maxNumberOfKeys", FieldType.UINT64, false, true, false, 8);
    private static final FieldDefinition FIELD_TABLECONFIG_HASHRAMTODISKTHRESHOLD =
            new FieldDefinition("hashesRamToDiskThreshold", FieldType.UINT64, false, true, false, 9);

    /**
     * Hash version.
     */
    private short hashVersion;

    /**
     * Hash type. Only SHA_384 is supported yet.
     */
    private DigestType hashType;

    /**
     * Key serializer.
     *
     * <p>This field is only used to migrate MerkleDb to work with bytes rather than with strictly
     * typed objects. Previously, key and value serializers were the part of MerkleDbTableConfig.
     * The config objects were stored in MerkleDb metadata. Now these serializers are moved to the
     * VirtualMap level, but in the existing state snapshots they are still a part of MerkleDb.
     * This is why the serializers are still read in {@link #deserialize(SerializableDataInputStream, int)}
     * and later queried by VirtualMap / VirtualRootNode. When this object is serialized again, the
     * serializers are ignored, assuming they are saved at the VirtualMap level.
     */
    @Deprecated
    private KeySerializer keySerializer;

    /**
     * Value serializer.
     *
     * <p>This field is only used to migrate MerkleDb to work with bytes rather than with strictly
     * typed objects. Previously, key and value serializers were the part of MerkleDbTableConfig.
     * The config objects were stored in MerkleDb metadata. Now these serializers are moved to the
     * VirtualMap level, but in the existing state snapshots they are still a part of MerkleDb.
     * This is why the serializers are still read in {@link #deserialize(SerializableDataInputStream, int)}
     * and later queried by VirtualMap / VirtualRootNode. When this object is serialized again, the
     * serializers are ignored, assuming they are saved at the VirtualMap level.
     */
    @Deprecated
    private ValueSerializer valueSerializer;

    /**
     * Max number of keys that can be stored in a table.
     */
    private long maxNumberOfKeys = 0;

    /**
     * Threshold where we switch from storing internal hashes in ram to storing them on disk. If it is 0 then everything
     * is on disk, if it is Long.MAX_VALUE then everything is in ram. Any value in the middle is the path value at which
     * we swap from ram to disk. This allows a tree where the lower levels of the tree nodes hashes are in ram and the
     * upper larger less changing layers are on disk.
     */
    private long hashesRamToDiskThreshold = 0;

    /**
     * Indicates whether to store indexes on disk or in Java heap/off-heap memory.
     */
    private boolean preferDiskBasedIndices = false;

    /**
     * Creates a new virtual table config with default values. This constructor should only be used
     * for deserialization.
     */
    public MerkleDbTableConfig() {
        // required for deserialization
    }

    /**
     * Creates a new virtual table config with the specified params.
     *
     * @param hashVersion
     *      Hash version
     * @param hashType
     *      Hash type
     */
    public MerkleDbTableConfig(final short hashVersion, final DigestType hashType) {
        // Mandatory fields
        this.hashVersion = hashVersion;
        this.hashType = hashType;

        // Optional hints, may be set explicitly using setters later. Defaults are loaded from
        // MerkleDb configuration
        final MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);
        maxNumberOfKeys = dbConfig.maxNumOfKeys();
        hashesRamToDiskThreshold = dbConfig.hashesRamToDiskThreshold();
    }

    public MerkleDbTableConfig(final ReadableSequentialData in) {
        // Defaults. If a field is missing in the input, a default protobuf value is used
        // (zero, false, null, etc.) rather than a default value from MerkleDb config. The
        // config is used for defaults when a new table config is created, but when an
        // existing config is loaded, only values from the input must be used (even if some
        // of them are protobuf default and aren't present)
        hashVersion = 0;
        hashType = DigestType.SHA_384;
        preferDiskBasedIndices = false;
        maxNumberOfKeys = 0;
        hashesRamToDiskThreshold = 0;

        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            if (fieldNum == FIELD_TABLECONFIG_HASHVERSION.number()) {
                hashVersion = (short) in.readVarInt(false);
            } else if (fieldNum == FIELD_TABLECONFIG_DIGESTTYPEID.number()) {
                final int digestTypeId = in.readVarInt(false);
                hashType = DigestType.valueOf(digestTypeId);
            } else if (fieldNum == FIELD_TABLECONFIG_KEYVERSION.number()) {
                // Skip key version
                in.readVarInt(false);
            } else if (fieldNum == FIELD_TABLECONFIG_KEYSERIALIZERCLSID.number()) {
                final long classId = in.readVarLong(false);
                keySerializer = ConstructableRegistry.getInstance().createObject(classId);
            } else if (fieldNum == FIELD_TABLECONFIG_VALUEVERSION.number()) {
                // Skip value version
                in.readVarInt(false);
            } else if (fieldNum == FIELD_TABLECONFIG_VALUESERIALIZERCLSID.number()) {
                final long classId = in.readVarLong(false);
                valueSerializer = ConstructableRegistry.getInstance().createObject(classId);
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
        if (hashVersion != 0) {
            size += ProtoWriterTools.sizeOfTag(
                    FIELD_TABLECONFIG_HASHVERSION, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG);
            size += ProtoWriterTools.sizeOfVarInt32(hashVersion);
        }
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
        if (hashVersion != 0) {
            ProtoWriterTools.writeTag(out, FIELD_TABLECONFIG_HASHVERSION);
            out.writeVarInt(hashVersion, false);
        }
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
     * Hash version.
     *
     * @return
     *      Hash version
     */
    public short getHashVersion() {
        return hashVersion;
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
    public KeySerializer getKeySerializer() {
        return keySerializer;
    }

    /**
     * Value serializer
     *
     * @return
     *      Value serializer
     */
    @Deprecated
    public ValueSerializer getValueSerializer() {
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
    public MerkleDbTableConfig maxNumberOfKeys(final long maxNumberOfKeys) {
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
    public MerkleDbTableConfig hashesRamToDiskThreshold(final long hashesRamToDiskThreshold) {
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
    public MerkleDbTableConfig preferDiskIndices(final boolean preferDiskBasedIndices) {
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
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeBoolean(preferDiskBasedIndices);
        out.writeLong(maxNumberOfKeys);
        out.writeLong(hashesRamToDiskThreshold);
        out.writeShort(hashVersion);
        out.writeInt(hashType.id());
        out.writeShort(0); // key version
        out.writeSerializable(null, true); // key serializer
        out.writeShort(0); // value version
        out.writeSerializable(null, true); // value serializer
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        preferDiskBasedIndices = in.readBoolean();
        maxNumberOfKeys = in.readLong();
        hashesRamToDiskThreshold = in.readLong();
        hashVersion = in.readShort();
        hashType = DigestType.valueOf(in.readInt());
        in.readShort(); // key version
        in.readSerializable(); // key serializer
        in.readShort(); // value version
        in.readSerializable(); // value serializer
    }

    /**
     * Creates a copy of this table config.
     *
     * @return Table config copy
     */
    public MerkleDbTableConfig copy() {
        final MerkleDbTableConfig copy = new MerkleDbTableConfig(hashVersion, hashType);
        copy.preferDiskIndices(preferDiskBasedIndices);
        copy.hashesRamToDiskThreshold(hashesRamToDiskThreshold);
        copy.maxNumberOfKeys(maxNumberOfKeys);
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(hashVersion, hashType, preferDiskBasedIndices, maxNumberOfKeys, hashesRamToDiskThreshold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof MerkleDbTableConfig other)) {
            return false;
        }
        return (preferDiskBasedIndices == other.preferDiskBasedIndices)
                && (maxNumberOfKeys == other.maxNumberOfKeys)
                && (hashesRamToDiskThreshold == other.hashesRamToDiskThreshold)
                && (hashVersion == other.hashVersion)
                && Objects.equals(hashType, other.hashType);
    }
}
