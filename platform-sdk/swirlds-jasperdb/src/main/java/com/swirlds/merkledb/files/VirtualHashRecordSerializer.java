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
import static com.swirlds.virtualmap.datasource.VirtualHashBytes.FIELD_HASHRECORD_HASH;
import static com.swirlds.virtualmap.datasource.VirtualHashBytes.FIELD_HASHRECORD_PATH;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.serialize.BaseSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * Serializer to store and read virtual hash records in MerkleDb data files.
 *
 * <p>Protobuf schema:
 *
 * <pre>
 * message HashRecord {
 *
 *     // Virtual node path
 *     optional uint64 path = 1;
 *
 *     // Hash. Always DigestType.SHA_384 for now
 *     bytes hash = 2;
 * }
 * </pre>>
 */
public final class VirtualHashRecordSerializer implements BaseSerializer<VirtualHashRecord> {

    /**
     * The digest type to use for Virtual hashes, if this is changed then serialized version need
     * to change
     */
    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;

    /**
     * This will need to change if we ever write different data due to path changing or
     * DEFAULT_DIGEST changing
     */
    private static final long CURRENT_SERIALIZATION_VERSION = 1;

    public VirtualHashRecordSerializer() {
        // for deserialization
    }

    @Override
    public long getCurrentDataVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    @Override
    @Deprecated(forRemoval = true)
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return ProtoWriterTools.sizeOfTag(FIELD_HASHRECORD_PATH, ProtoConstants.WIRE_TYPE_FIXED_64_BIT)
                + Long.BYTES
                + ProtoWriterTools.sizeOfDelimited(FIELD_HASHRECORD_HASH, DigestType.SHA_384.digestLength());
    }

    @Override
    public int getSerializedSize(VirtualHashRecord data) {
        int size = 0;
        if (data.path() != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_HASHRECORD_PATH, ProtoConstants.WIRE_TYPE_FIXED_64_BIT)
                    + Long.BYTES;
        }
        size += ProtoWriterTools.sizeOfDelimited(
                FIELD_HASHRECORD_HASH, (int) data.hash().getBytes().length());
        return size;
    }

    @Override
    public void serialize(@NonNull final VirtualHashRecord hashRecord, @NonNull final WritableSequentialData out) {
        final DigestType digestType = hashRecord.hash().getDigestType();
        if (DEFAULT_DIGEST != digestType) {
            throw new IllegalArgumentException(
                    "Only " + DEFAULT_DIGEST + " digests allowed, but received hash with digest " + digestType);
        }
        if (hashRecord.path() != 0) {
            ProtoWriterTools.writeTag(out, FIELD_HASHRECORD_PATH);
            // Use long instead of var long to keep the size fixed
            out.writeLong(hashRecord.path());
        }
        ProtoWriterTools.writeDelimited(
                out,
                FIELD_HASHRECORD_HASH,
                (int) hashRecord.hash().getBytes().length(),
                o -> hashRecord.hash().getBytes().writeTo(o));
    }

    @Override
    public VirtualHashRecord deserialize(@NonNull final ReadableSequentialData in) {
        // default values
        long path = 0;
        Hash hash = null;

        // read fields, they may be in any order or even missing at all
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            if (fieldNum == FIELD_HASHRECORD_PATH.number()) {
                path = readPath(in);
            } else if (fieldNum == FIELD_HASHRECORD_HASH.number()) {
                hash = readHash(in);
            } else {
                throw new IllegalArgumentException("Unknown virtual hash record field: " + fieldNum);
            }
        }

        // we actually don't expect null hashes here
        assert hash != null : "Null virtual hash record hash";
        return new VirtualHashRecord(path, hash);
    }

    private long readPath(final ReadableSequentialData in) {
        final long path = in.readLong();
        return path;
    }

    private Hash readHash(final ReadableSequentialData in) {
        final int hashSize = in.readVarInt(false);
        assert hashSize == DigestType.SHA_384.digestLength();
        final byte[] hashBytes = new byte[hashSize];
        in.readBytes(hashBytes);
        return new Hash(hashBytes, DigestType.SHA_384);
    }

    public void extractAndWriteHashBytes(final ReadableSequentialData in, final SerializableDataOutputStream out)
            throws IOException {
        // Hash.serialize() format is: digest ID (4 bytes) + size (4 bytes) + hash (48 bytes)
        out.writeInt(DigestType.SHA_384.id());
        // hashBytes is in protobuf format
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            if (fieldNum == FIELD_HASHRECORD_PATH.number()) {
                in.skip(Long.BYTES);
            } else if (fieldNum == FIELD_HASHRECORD_HASH.number()) {
                final int hashSize = in.readVarInt(false);
                assert hashSize == DigestType.SHA_384.digestLength();
                // It would be helpful to have BufferedData.readBytes(OutputStream) method, similar to
                // readBytes(ByteBuffer) or readBytes(BufferedData). Since there is no such method,
                // use a workaround to allocate a byte array
                final byte[] arr = new byte[hashSize];
                in.readBytes(arr);
                out.writeInt(hashSize);
                out.write(arr);
                break;
            } else {
                throw new IllegalArgumentException("Unknown virtual hash record field: " + fieldNum);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return (o != null) && (getClass() == o.getClass());
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (int) CURRENT_SERIALIZATION_VERSION;
    }
}
