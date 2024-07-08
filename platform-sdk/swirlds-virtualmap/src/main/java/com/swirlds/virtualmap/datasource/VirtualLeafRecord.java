/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.datasource;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VLEAFRECORD_KEY;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VLEAFRECORD_PATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VLEAFRECORD_VALUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VLEAFRECORD_KEY;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VLEAFRECORD_PATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VLEAFRECORD_VALUE;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.base.function.CheckedFunction;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.proto.MerkleNodeProtoFields;
import com.swirlds.common.merkle.proto.ProtoSerializable;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * An object for leaf data. The leaf record contains the path, key, and value.
 * This record is {@link SelfSerializable} to support reconnect and state saving, where it is necessary
 * to take leaf records from caches that are not yet flushed to disk and write them to the stream.
 * We never send hashes in the stream.
 */
public final class VirtualLeafRecord<K extends VirtualKey, V extends VirtualValue>
        implements ProtoSerializable, SelfSerializable {

    private static final long CLASS_ID = 0x410f45f0acd3264L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * The path for this record. The path can change over time as nodes are added or removed.
     */
    private volatile long path;

    // key and value are effectively immutable, as they are being set only in the constructor and deserialize method,
    // therefore they don't need to be volatile. They can't be final because of the deserialize method.
    private K key;
    private V value;

    /**
     * Create a new leaf record. This constructor is <strong>only</strong> used by the serialization engine.
     * It creates a leaf with a totally invalid leaf path.
     */
    public VirtualLeafRecord() {
        path = -1;
    }

    /**
     * Create a new leaf record, supplying all data required by the record.
     *
     * @param path
     * 		The path. Must be positive (since 0 represents a root node, which is never a leaf),
     * 		or {@link Path#INVALID_PATH}.
     * @param key
     * 		The key for this record. This should normally never be null, but may be for
     *        {@link VirtualNodeCache#DELETED_LEAF_RECORD}
     * 		or other uses where the leaf record is meant to represent some invalid state.
     * @param value
     * 		The value for this record, which can be null.
     */
    public VirtualLeafRecord(final long path, final K key, final V value) {
        this.path = path;
        this.key = key;
        this.value = value;
    }

    public VirtualLeafRecord(
            @NonNull final ReadableSequentialData in,
            @NonNull final CheckedFunction<ReadableSequentialData, K, Exception> keyReader,
            @NonNull final CheckedFunction<ReadableSequentialData, V, Exception> valueReader)
            throws MerkleSerializationException {
        // Defaults
        path = 0;
        key = null;
        value = null;

        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> ProtoParserTools.TAG_FIELD_OFFSET;
            if (fieldNum == NUM_VLEAFRECORD_PATH) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                path = in.readVarLong(false);
            } else if (fieldNum == NUM_VLEAFRECORD_KEY) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
                final int len = in.readVarInt(false);
                final long oldLimit = in.limit();
                in.limit(in.position() + len);
                try {
                    key = keyReader.apply(in);
                } catch (final Exception e) {
                    throw new MerkleSerializationException("Failed to parse a key", e);
                } finally {
                    in.limit(oldLimit);
                }
            } else if (fieldNum == NUM_VLEAFRECORD_VALUE) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
                final int len = in.readVarInt(false);
                final long oldLimit = in.limit();
                in.limit(in.position() + len);
                try {
                    value = valueReader.apply(in);
                } catch (final Exception e) {
                    throw new MerkleSerializationException("Failed to parse a value", e);
                } finally {
                    in.limit(oldLimit);
                }
            } else {
                throw new MerkleSerializationException("Unknown leaf record field: " + tag);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public VirtualLeafRecord<K, V> copy() {
        return new VirtualLeafRecord<>(path, key, (V) value.copy());
    }

    /**
     * Gets the key.
     * @return
     *        The key. This <strong>may</strong> be null in some cases, such as when the record is meant to
     *		represent an invalid state, or when it is in the middle of serialization. No leaf that represnts
     *		an actual leaf will ever return null here.
     */
    public K getKey() {
        return key;
    }

    /**
     * Gets the value.
     * @return
     *        The value. May be null.
     */
    public V getValue() {
        return value;
    }

    public void setPath(long path) {
        this.path = path;
    }

    public long getPath() {
        return path;
    }

    /**
     * Sets the value. May be null. Must set the hash to null if the value has changed.
     *
     * @param value
     * 		The value.
     */
    public void setValue(final V value) {
        if (this.value != value) {
            this.value = value;
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
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(path);
        out.writeSerializable(key, true);
        out.writeSerializable(value, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.path = in.readLong();
        this.key = in.readSerializable();
        this.value = in.readSerializable();
    }

    @Override
    public int getProtoSizeInBytes() {
        int size = 0;
        if (path != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VLEAFRECORD_PATH);
            size += ProtoWriterTools.sizeOfVarInt64(path);
        }
        size += ProtoWriterTools.sizeOfDelimited(FIELD_VLEAFRECORD_KEY, key.getProtoSizeInBytes());
        size += ProtoWriterTools.sizeOfDelimited(FIELD_VLEAFRECORD_VALUE, value.getProtoSizeInBytes());
        return size;
    }

    @Override
    public void protoSerialize(final WritableSequentialData out) throws MerkleSerializationException {
        if (path != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VLEAFRECORD_PATH);
            out.writeVarLong(path, false);
        }
        final AtomicReference<MerkleSerializationException> ex = new AtomicReference<>();
        ProtoWriterTools.writeDelimited(out, FIELD_VLEAFRECORD_KEY, key.getProtoSizeInBytes(), o -> {
            try {
                key.protoSerialize(o);
            } catch (MerkleSerializationException e) {
                ex.set(e);
            }
        });
        ProtoWriterTools.writeDelimited(out, FIELD_VLEAFRECORD_KEY, value.getProtoSizeInBytes(), o -> {
            try {
                value.protoSerialize(o);
            } catch (MerkleSerializationException e) {
                ex.set(e);
            }
        });
        if (ex.get() != null) {
            throw ex.get();
        }
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

        final VirtualLeafRecord<?, ?> that = (VirtualLeafRecord<?, ?>) o;
        return path == that.path && Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(path, key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("key", key)
                .append("value", value)
                .append("path", path)
                .toString();
    }
}
