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

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class VirtualInternalRecordSerializer implements DataItemSerializer<VirtualInternalRecord> {

    /** The digest type to use for Virtual Internals, if this is changed then serialized version need to change */
    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;

    /** This will need to change if we ever write different data due to path changing or DEFAULT_DIGEST changing */
    private static final long CURRENT_SERIALIZATION_VERSION = 1;

    private static final int SERIALIZED_SIZE = Long.BYTES + DEFAULT_DIGEST.digestLength(); // path + hash

    public VirtualInternalRecordSerializer() {
        // for deserialization
    }

    @Override
    public long getCurrentDataVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    @Override
    public int getHeaderSize() {
        return Long.BYTES; // path
    }

    @Override
    public DataItemHeader deserializeHeader(final ByteBuffer buffer) {
        final long path = buffer.getLong();
        final int size = getSerializedSize();
        return new DataItemHeader(size, path);
    }

    @Override
    public int getSerializedSize() {
        return SERIALIZED_SIZE;
    }

    @Override
    public VirtualInternalRecord deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        if (dataVersion != CURRENT_SERIALIZATION_VERSION) {
            throw new IllegalArgumentException(
                    "Cannot deserialize version " + dataVersion + ", current is " + CURRENT_SERIALIZATION_VERSION);
        }
        final long path = buffer.getLong();
        final Hash newHash = new Hash(DigestType.SHA_384);
        buffer.get(newHash.getValue());
        return new VirtualInternalRecord(path, newHash);
    }

    @Override
    public int serialize(final VirtualInternalRecord data, final SerializableDataOutputStream outputStream)
            throws IOException {
        final DigestType digestType = data.getHash().getDigestType();
        if (DEFAULT_DIGEST != digestType) {
            throw new IllegalArgumentException(
                    "Only " + DEFAULT_DIGEST + " digests allowed, but received hash with digest " + digestType);
        }
        outputStream.writeLong(data.getPath());
        outputStream.write(data.getHash().getValue());
        return SERIALIZED_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return (o != null) && (getClass() == o.getClass());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return (int) CURRENT_SERIALIZATION_VERSION;
    }
}
