package com.swirlds.common.merkle.proto;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_HASH_DATA;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_HASH_DIGESTTYPE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_NODE_HASH;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.exceptions.MerkleSerializationException;

public final class MerkleProtoUtils {

    private MerkleProtoUtils() {}

    public static int getHashSizeInBytes(final Hash hash) {
        if (hash == null) {
            return 0;
        }
        int size = 0;
        size += ProtoWriterTools.sizeOfTag(FIELD_HASH_DIGESTTYPE) +
                ProtoWriterTools.sizeOfVarInt32(hash.getDigestType().id());
        size += ProtoWriterTools.sizeOfDelimited(FIELD_HASH_DATA, hash.getValue().length);
        return ProtoWriterTools.sizeOfDelimited(FIELD_NODE_HASH, size);
    }

    public static void protoWriteHash(final WritableSequentialData out, final Hash hash) {
        if (hash == null) {
            return;
        }
        ProtoWriterTools.writeDelimited(out, FIELD_NODE_HASH, getHashSizeInBytes(hash), w -> {
            final int hashDigestType = hash.getDigestType().id();
            // Hash digest type
            if (hashDigestType != 0) {
                ProtoWriterTools.writeTag(w, FIELD_HASH_DIGESTTYPE);
                w.writeVarInt(hashDigestType, false);
            }
            // Hash value
            final byte[] value = hash.getValue();
            // TODO: completelyWrite ?
            ProtoWriterTools.writeDelimited(w, FIELD_HASH_DATA, value.length, x -> x.writeBytes(value));
        });
    }

    public static Hash protoReadHash(final ReadableSequentialData in) throws MerkleSerializationException {
        if (!in.hasRemaining()) {
            return null;
        }

        // defaults
        DigestType digestType = null;
        byte[] value = null;

        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> ProtoParserTools.TAG_FIELD_OFFSET;
            if (fieldNum == FIELD_HASH_DIGESTTYPE.number()) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
                final int digestTypeId = in.readVarInt(false);
                digestType = DigestType.valueOf(digestTypeId);
            } else if (fieldNum == FIELD_HASH_DATA.number()) {
                assert (tag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
                final int len = in.readVarInt(false);
                value = new byte[len];
                // TODO: completelyRead ?
                if (in.readBytes(value) != len) {
                    throw new MerkleSerializationException("Failed to read hash bytes");
                }
            } else {
                throw new MerkleSerializationException("Unknown Hash field: " + tag);
            }
        }

        if (digestType == null) {
            throw new MerkleSerializationException("Failed to read hash, no / unknown digest type");
        }
        if (value == null) {
            throw new MerkleSerializationException("Failed to read hash, no bytes");
        }

        return new Hash(value, digestType);
    }
}
