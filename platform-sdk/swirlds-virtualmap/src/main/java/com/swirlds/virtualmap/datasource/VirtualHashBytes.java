/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * Virtual hash record bytes.
 *
 * <p>Protobuf schema:
 *
 * <p><pre>
 * message HashRecord {
 *
 *     // Virtual node path
 *     optional fixed64 path = 1;
 *
 *     // Hash. Always DigestType.SHA_384 for now
 *     bytes hash = 2;
 * }
 * </pre>
 *
 * @param path virtual record path
 * @param hashBytes hash bytes
 */
public record VirtualHashBytes(long path, Bytes hashBytes) {

    public static final FieldDefinition FIELD_HASHRECORD_PATH =
            new FieldDefinition("path", FieldType.FIXED64, false, true, false, 1);
    public static final FieldDefinition FIELD_HASHRECORD_HASH =
            new FieldDefinition("hash", FieldType.BYTES, false, true, false, 2);

    /**
     * Reads a virtual hash bytes object from the given sequential data.
     *
     * @param in sequential data to read from
     * @return the virtual hash bytes object
     */
    public static VirtualHashBytes parseFrom(final ReadableSequentialData in) {
        if (in == null) {
            return null;
        }

        long path = 0;
        Bytes hashBytes = null;

        while (in.hasRemaining()) {
            final int field = in.readVarInt(false);
            final int tag = field >> ProtoParserTools.TAG_FIELD_OFFSET;
            if (tag == FIELD_HASHRECORD_PATH.number()) {
                if ((field & ProtoConstants.TAG_WIRE_TYPE_MASK) != ProtoConstants.WIRE_TYPE_FIXED_64_BIT.ordinal()) {
                    throw new IllegalArgumentException("Wrong field type: " + field);
                }
                path = in.readLong();
            } else if (tag == FIELD_HASHRECORD_HASH.number()) {
                if ((field & ProtoConstants.TAG_WIRE_TYPE_MASK) != ProtoConstants.WIRE_TYPE_DELIMITED.ordinal()) {
                    throw new IllegalArgumentException("Wrong field type: " + field);
                }
                final int len = in.readVarInt(false);
                hashBytes = in.readBytes(len);
            } else {
                throw new IllegalArgumentException("Unknown field: " + field);
            }
        }

        return new VirtualHashBytes(path, hashBytes);
    }

    public int getSizeInBytes() {
        int size = 0;
        size += ProtoWriterTools.sizeOfTag(FIELD_HASHRECORD_PATH);
        size += Long.BYTES;
        if (hashBytes != null) {
            size += ProtoWriterTools.sizeOfDelimited(FIELD_HASHRECORD_HASH, Math.toIntExact(hashBytes.length()));
        }
        return size;
    }

    /**
     * Writes this virtual hash bytes object to the given sequential data.
     *
     * @param out the sequential data to write to
     */
    public void writeTo(final WritableSequentialData out) {
        ProtoWriterTools.writeTag(out, FIELD_HASHRECORD_PATH);
        out.writeLong(path);
        if (hashBytes != null) {
            ProtoWriterTools.writeDelimited(
                    out, FIELD_HASHRECORD_HASH, Math.toIntExact(hashBytes.length()), hashBytes::writeTo);
        }
    }

    public static void extractAndWriteHashBytes(final ReadableSequentialData in, final SerializableDataOutputStream out)
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

    /**
     * Convert this bytes object to a virtual hash record.
     *
     * @return the virtual hash record
     */
    public VirtualHashRecord toRecord() {
        return new VirtualHashRecord(path, new Hash(hashBytes.toByteArray()));
    }
}
