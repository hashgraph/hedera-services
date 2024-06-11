package com.swirlds.virtualmap.internal.cache;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCPATHTOHASH_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCPATHTOHASH_PATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VNCPATHTOHASH_VERSION;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOHASH_DELETED;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOHASH_HASH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOHASH_PATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VNCPATHTOHASH_VERSION;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.proto.MerkleProtoUtils;
import com.swirlds.common.merkle.proto.ProtoSerializable;
import com.swirlds.virtualmap.VirtualKey;
import java.util.function.Function;

public class CachePathToHashEntry implements ProtoSerializable {

    private final long version;
    private final long path;
    private final Hash hash;
    private final boolean deleted;

    public CachePathToHashEntry(final long version, final long path, final Hash hash, final boolean deleted) {
        this.version = version;
        this.path = path;
        this.hash = hash;
        this.deleted = deleted;
    }

    public CachePathToHashEntry(final ReadableSequentialData in) throws MerkleSerializationException {
        long defaultVersion = 0;
        long defaultPath = 0;
        Hash defaultHash = null;
        boolean defaultDeleted = false;

        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> ProtoConstants.TAG_WIRE_TYPE_MASK;
            if (fieldNum == NUM_VNCPATHTOHASH_VERSION) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultVersion = in.readVarLong(false);
            } else if (fieldNum == NUM_VNCPATHTOHASH_PATH) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultPath = in.readVarLong(false);
            } else if (fieldNum == NUM_VNCPATHTOHASH_HASH) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
                final int len = in.readVarInt(false);
                final long oldLimit = in.limit();
                in.limit(in.position() + len);
                try {
                    defaultHash = MerkleProtoUtils.protoReadHash(in);
                } finally {
                    in.limit(oldLimit);
                }
            } else if (fieldNum == NUM_VNCPATHTOHASH_DELETED) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                defaultDeleted = in.readVarInt(false) != 0;
            } else {
                throw new MerkleSerializationException("Unknown path/hash cache entry field: " + tag);
            }
        }

        version = defaultVersion;
        path = defaultPath;
        if (defaultHash == null) {
            throw new MerkleSerializationException("Failed to read hash");
        }
        hash = defaultHash;
        deleted = defaultDeleted;
    }

    public long getVersion() {
        return version;
    }

    public long getPath() {
        return path;
    }

    public Hash getHash() {
        return hash;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public int getProtoSizeInBytes() {
        int size = 0;
        if (version != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCPATHTOHASH_VERSION);
            size += ProtoWriterTools.sizeOfVarInt64(version);
        }
        if (path != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCPATHTOHASH_PATH);
            size += ProtoWriterTools.sizeOfVarInt64(path);
        }
        size += MerkleProtoUtils.getHashSizeInBytes(hash);
        if (deleted) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VNCPATHTOHASH_DELETED);
            size += ProtoWriterTools.sizeOfVarInt32(1);
        }
        return size;
    }

    @Override
    public void protoSerialize(final WritableSequentialData out) throws MerkleSerializationException {
        if (version != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VNCPATHTOHASH_VERSION);
            out.writeVarLong(version, false);
        }
        if (path != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VNCPATHTOHASH_PATH);
            out.writeVarLong(path, false);
        }
        MerkleProtoUtils.protoWriteHash(out, hash);
        if (deleted) {
            ProtoWriterTools.writeTag(out, FIELD_VNCPATHTOHASH_DELETED);
            out.writeVarInt(1, false);
        }
    }
}
