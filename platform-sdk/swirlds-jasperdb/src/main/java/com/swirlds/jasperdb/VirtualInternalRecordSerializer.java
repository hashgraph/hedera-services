/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.jasperdb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataItemHeader;
import com.swirlds.jasperdb.files.DataItemSerializer;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Serializer for VirtualInternalRecord objects
 */
public class VirtualInternalRecordSerializer implements DataItemSerializer<VirtualInternalRecord>, SelfSerializable {

    private static final long CLASS_ID = 0x16b097b6e74e0659L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /** The digest type to use for Virtual Internals, if this is changed then serialized version need to change */
    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;
    /** This will need to change if we ever write different data due to path changing or DEFAULT_DIGEST changing */
    private static final long CURRENT_SERIALIZATION_VERSION = 1;
    /** number of bytes a data item takes when serialized */
    private final int serializedSize;

    public VirtualInternalRecordSerializer() {
        this.serializedSize = Long.BYTES + DEFAULT_DIGEST.digestLength();
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
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return Long.BYTES;
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer
     * 		Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(final ByteBuffer buffer) {
        return new DataItemHeader(serializedSize, buffer.getLong());
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return serializedSize;
    }

    /**
     * Get the current data item serialization version
     */
    @Override
    public long getCurrentDataVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer
     * 		The buffer to read from
     * @param dataVersion
     * 		The serialization version the data item was written with
     * @return Deserialized data item
     * @throws IllegalArgumentException
     * 		if used with version other than current
     */
    @Override
    public VirtualInternalRecord deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        if (dataVersion != CURRENT_SERIALIZATION_VERSION) {
            throw new IllegalArgumentException(
                    "Cannot deserialize version " + dataVersion + ", current is " + CURRENT_SERIALIZATION_VERSION);
        }
        final long path = buffer.getLong();
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3928 */
        final Hash newHash = new Hash(DEFAULT_DIGEST);
        buffer.get(newHash.getValue());
        return new VirtualInternalRecord(path, newHash);
    }

    /**
     * Serialize a data item to the output stream returning the size of the data written
     *
     * @param data
     * 		The data item to serialize
     * @param outputStream
     * 		Output stream to write to
     */
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
        return serializedSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // nothing to serialize
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // nothing to deserialize
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
        final VirtualInternalRecordSerializer that = (VirtualInternalRecordSerializer) o;
        return serializedSize == that.serializedSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(serializedSize);
    }
}
