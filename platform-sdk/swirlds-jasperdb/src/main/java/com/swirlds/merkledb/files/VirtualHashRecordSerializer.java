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
import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_VARINT;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.utilities.ProtoUtils;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import java.io.IOException;

public final class VirtualHashRecordSerializer implements DataItemSerializer<VirtualHashRecord> {

    private static final FieldDefinition FIELD_HASHRECORD_PATH =
            new FieldDefinition("path", FieldType.UINT64, false, true, false, 1);
    private static final FieldDefinition FIELD_HASHRECORD_HASH =
            new FieldDefinition("hash", FieldType.BYTES, false, true, false, 2);

    /**
     * The digest type to use for Virtual Internals, if this is changed then serialized version need
     * to change
     */
    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;

    /**
     * This will need to change if we ever write different data due to path changing or
     * DEFAULT_DIGEST changing
     */
    private static final long CURRENT_SERIALIZATION_VERSION = 1;

    private static final int SERIALIZED_SIZE = Long.BYTES + DEFAULT_DIGEST.digestLength(); // path + hash

    public VirtualHashRecordSerializer() {
        // for deserialization
    }

    @Override
    public long getCurrentDataVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    @Override
    // TODO: is it really used anywhere?
    public int getSerializedSize() {
        return SERIALIZED_SIZE;
    }

    @Override
    public int getSerializedSize(VirtualHashRecord data) {
//        return ProtoWriterTools.sizeOfLong(FIELD_HASHRECORD_PATH, data.path()) +
        int size = 0;
        if (data.path() != 0) {
            size += ProtoUtils.sizeOfTag(FIELD_HASHRECORD_PATH, WIRE_TYPE_VARINT) +
                    ProtoUtils.sizeOfUnsignedVarInt64(data.path());
        }
        size += ProtoUtils.sizeOfDelimited(FIELD_HASHRECORD_HASH, data.hash().getValue().length);
        return size;
    }

    @Override
    public VirtualHashRecord deserialize(final ReadableSequentialData in) {
        // default values
        long path = 0;
        Hash hash = null;

        // read fields, they may be in any order or even missing at all
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int number = tag >> TAG_FIELD_OFFSET;
            if (number == FIELD_HASHRECORD_PATH.number()) {
                path = readPath(in);
            } else if (number == FIELD_HASHRECORD_HASH.number()) {
                hash = readHash(in);
            } else {
                throw new IllegalArgumentException("Unknown virtual hash record field: " + number);
            }
        }

        // we actually don't expect null hashes here
        assert hash != null : "Null virtual hash record hash";
        return new VirtualHashRecord(path, hash);
    }

    private long readPath(final ReadableSequentialData in) {
        final long path = in.readVarLong(false);
        return path;
    }

    private Hash readHash(final ReadableSequentialData in) {
        final int hashSize = in.readVarInt(false);
        final Hash hash = new Hash(DigestType.SHA_384);
        assert hashSize == hash.getValue().length;
        in.readBytes(hash.getValue());
        return hash;
    }

    @Override
    public long extractKey(BufferedData dataItemData) {
        return dataItemData.getLong(0);
    }

    @Override
    public void serialize(final VirtualHashRecord hashRecord, final WritableSequentialData out) throws IOException {
        final DigestType digestType = hashRecord.hash().getDigestType();
        if (DEFAULT_DIGEST != digestType) {
            throw new IllegalArgumentException(
                    "Only " + DEFAULT_DIGEST + " digests allowed, but received hash with digest " + digestType);
        }
//        ProtoWriterTools.writeLong(out, FIELD_HASHRECORD_PATH, hashRecord.path());
        // TODO: force write default values
        ProtoUtils.writeBytes(out, FIELD_HASHRECORD_HASH, hashRecord.hash().getValue().length,
                o -> o.writeBytes(hashRecord.hash().getValue()));
        if (hashRecord.path() != 0) {
            ProtoUtils.writeTag(out, FIELD_HASHRECORD_PATH);
            out.writeVarLong(hashRecord.path(), false);
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
