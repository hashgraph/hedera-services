package com.swirlds.virtualmap.internal.cache;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCKEYTOLEAF_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCKEYTOLEAF_RECORD;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCKEYTOLEAF_VERSION;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCPATHTOKEY_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCPATHTOKEY_KEY;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCPATHTOKEY_PATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCPATHTOKEY_VERSION;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCKEYTOLEAF_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCKEYTOLEAF_RECORD;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCKEYTOLEAF_VERSION;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOKEY_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOKEY_KEY;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOKEY_PATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOKEY_VERSION;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.proto.ProtoSerializable;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class CachePathToKeyEntry<K extends VirtualKey> implements ProtoSerializable {

    private final long version;
    private final long path;
    private final K key;
    private final boolean deleted;

    public CachePathToKeyEntry(final long version, final long path, final K key, final boolean deleted) {
        this.version = version;
        this.path = path;
        this.key = key;
        this.deleted = deleted;
    }

    public CachePathToKeyEntry(final ReadableSequentialData in, final Function<ReadableSequentialData, K> keyReader)
            throws MerkleSerializationException {
        long defaultVersion = 0;
        long defaultPath = 0;
        K defaultKey = null;
        boolean defaultDeleted = false;

        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> ProtoConstants.TAG_WIRE_TYPE_MASK;
            if (fieldNum == NUM_VNCPATHTOKEY_VERSION) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultVersion = in.readVarLong(false);
            } else if (fieldNum == NUM_VNCPATHTOKEY_PATH) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultPath = in.readVarLong(false);
            } else if (fieldNum == NUM_VNCPATHTOKEY_KEY) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
                final int len = in.readVarInt(false);
                final long oldLimit = in.limit();
                in.limit(in.position() + len);
                try {
                    defaultKey = keyReader.apply(in);
                } finally {
                    in.limit(oldLimit);
                }
            } else if (fieldNum == NUM_VNCPATHTOKEY_DELETED) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultDeleted = in.readVarInt(false) != 0;
            } else {
                throw new MerkleSerializationException("Unknown path/key cache entry field: " + tag);
            }
        }

        version = defaultVersion;
        path = defaultPath;
        if (defaultKey == null) {
            throw new MerkleSerializationException("Failed to read key");
        }
        key = defaultKey;
        deleted = defaultDeleted;
    }

    public long getVersion() {
        return version;
    }

    public long getPath() {
        return path;
    }

    public K getKey() {
        return key;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public int getProtoSizeInBytes() {
        int size = 0;
        if (version != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCPATHTOKEY_VERSION);
            size += ProtoWriterTools.sizeOfVarInt64(version);
        }
        if (path != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCPATHTOKEY_PATH);
            size += ProtoWriterTools.sizeOfVarInt64(path);
        }
        size += ProtoWriterTools.sizeOfDelimited(FIELD_VNCPATHTOKEY_KEY, key.getProtoSizeInBytes());
        if (deleted) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCPATHTOKEY_DELETED);
            size += ProtoWriterTools.sizeOfVarInt32(1);
        }
        return size;
    }

    @Override
    public void protoSerialize(final WritableSequentialData out) throws MerkleSerializationException {
        if (version != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VNCPATHTOKEY_VERSION);
            out.writeVarLong(version, false);
        }
        if (path != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VNCPATHTOKEY_PATH);
            out.writeVarLong(path, false);
        }
        final AtomicReference<MerkleSerializationException> ex = new AtomicReference<>();
        ProtoWriterTools.writeDelimited(out, FIELD_VNCPATHTOKEY_KEY, key.getProtoSizeInBytes(), o -> {
            try {
                key.protoSerialize(o);
            } catch (final MerkleSerializationException e) {
                ex.set(e);
            }
        });
        if (ex.get() != null) {
            throw ex.get();
        }
        if (deleted) {
            ProtoWriterTools.writeTag(out, FIELD_VNCPATHTOKEY_DELETED);
            out.writeVarInt(1, false);
        }
    }
}
