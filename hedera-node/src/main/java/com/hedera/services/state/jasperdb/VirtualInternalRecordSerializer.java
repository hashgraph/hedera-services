package com.hedera.services.state.jasperdb;

import com.hedera.services.state.jasperdb.files.DataItemHeader;
import com.hedera.services.state.jasperdb.files.DataItemSerializer;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Serializer for VirtualInternalRecord objects
 */
public class VirtualInternalRecordSerializer implements DataItemSerializer<VirtualInternalRecord> {
    /** The digest type to use for Virtual Internals, if this is changed then serialized version need to change */
    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;
    /** This will need to change if we ever write different data due to path changing or DEFAULT_DIGEST changing */
    private final long currentSerializationVersion = 1;
    /**  number of bytes a data item takes when serialized */
    private final int serializedSize;

    public VirtualInternalRecordSerializer() {
        this.serializedSize = Long.BYTES + DEFAULT_DIGEST.digestLength();
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
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        return new DataItemHeader(serializedSize,buffer.getLong());
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
        return currentSerializationVersion;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer      The buffer to read from
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public VirtualInternalRecord deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        assert dataVersion == currentSerializationVersion;
        final long path = buffer.getLong();
        // TODO the hashDigestType for deserialize should be based on dataVersion
        final Hash newHash = new Hash(DEFAULT_DIGEST);
        buffer.get(newHash.getValue());
        return new VirtualInternalRecord(path,newHash);
    }

    /**
     * Serialize a data item to the output stream returning the size of the data written
     *
     * @param data         The data item to serialize
     * @param outputStream Output stream to write to
     */
    @Override
    public int serialize(VirtualInternalRecord data, SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeLong(data.getPath());
        assert data.getHash().getDigestType().equals(DEFAULT_DIGEST); // TODO is there a better option
        outputStream.write(data.getHash().getValue());
        return serializedSize;
    }

}
