package com.swirlds.virtualmap.internal.cache;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCKEYTOLEAF_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCKEYTOLEAF_RECORD;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCKEYTOLEAF_VERSION;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCKEYTOLEAF_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCKEYTOLEAF_RECORD;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCKEYTOLEAF_VERSION;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.proto.ProtoSerializable;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class CacheKeyToLeafEntry<K extends VirtualKey, V extends VirtualValue> implements ProtoSerializable {

    private final long version;
    private final VirtualLeafRecord<K, V> record;
    private final boolean deleted;

    public CacheKeyToLeafEntry(final long version, final VirtualLeafRecord<K, V> record, final boolean deleted) {
        this.version = version;
        this.record = record;
        this.deleted = deleted;
    }

    public CacheKeyToLeafEntry(
            final ReadableSequentialData in,
            final Function<ReadableSequentialData, K> keyReader,
            final Function<ReadableSequentialData, V> valueReader)
            throws MerkleSerializationException {
        long defaultVersion = 0;
        VirtualLeafRecord<K, V> defaultRecord = null;
        boolean defaultDeleted = false;

        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> ProtoConstants.TAG_WIRE_TYPE_MASK;
            if (fieldNum == NUM_VNCKEYTOLEAF_VERSION) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultVersion = in.readVarLong(false);
            } else if (fieldNum == NUM_VNCKEYTOLEAF_RECORD) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
                final int len = in.readVarInt(false);
                final long oldLimit = in.limit();
                in.limit(in.position() + len);
                try {
                    defaultRecord = new VirtualLeafRecord<>(in, keyReader, valueReader);
                } finally {
                    in.limit(oldLimit);
                }
            } else if (fieldNum == NUM_VNCKEYTOLEAF_DELETED) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultDeleted = in.readVarInt(false) != 0;
            } else {
                throw new MerkleSerializationException("Unknown key/leaf cache entry field: " + tag);
            }
        }

        version = defaultVersion;
        if (defaultRecord == null) {
            throw new MerkleSerializationException("Failed to read leaf record");
        }
        record = defaultRecord;
        deleted = defaultDeleted;
    }

    public long getVersion() {
        return version;
    }

    public VirtualLeafRecord<K, V> getRecord() {
        return record;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public int getProtoSizeInBytes() {
        int size = 0;
        if (version != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCKEYTOLEAF_VERSION);
            size += ProtoWriterTools.sizeOfVarInt64(version);
        }
        size += ProtoWriterTools.sizeOfDelimited(FIELD_VNCKEYTOLEAF_RECORD, record.getProtoSizeInBytes());
        if (deleted) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCKEYTOLEAF_DELETED);
            size += ProtoWriterTools.sizeOfVarInt32(1);
        }
        return size;
    }

    @Override
    public void protoSerialize(final WritableSequentialData out) throws MerkleSerializationException {
        if (version != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VNCKEYTOLEAF_VERSION);
            out.writeVarLong(version, false);
        }
        final AtomicReference<MerkleSerializationException> ex = new AtomicReference<>();
        ProtoWriterTools.writeDelimited(out, FIELD_VNCKEYTOLEAF_RECORD, record.getProtoSizeInBytes(), o -> {
            try {
                record.protoSerialize(o);
            } catch (final MerkleSerializationException e) {
                ex.set(e);
            }
        });
        if (ex.get() != null) {
            throw ex.get();
        }
        if (deleted) {
            ProtoWriterTools.writeTag(out, FIELD_VNCKEYTOLEAF_DELETED);
            out.writeVarInt(1, false);
        }
    }
}
