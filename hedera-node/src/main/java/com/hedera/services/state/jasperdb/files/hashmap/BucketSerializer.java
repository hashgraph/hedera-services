package com.hedera.services.state.jasperdb.files.hashmap;

import com.hedera.services.state.jasperdb.files.DataItemHeader;
import com.hedera.services.state.jasperdb.files.DataItemSerializer;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.hedera.services.state.jasperdb.files.hashmap.HalfDiskHashMap.BUCKET_SIZE;

public class BucketSerializer<K extends VirtualKey> implements DataItemSerializer<Bucket<K>> {
    /** Temporary bucket buffers. */
    private final ThreadLocal<Bucket<K>> reusableBuckets;

    public BucketSerializer(KeySerializer<K> keySerializer) {
        reusableBuckets = ThreadLocal.withInitial(() -> new Bucket<>(keySerializer));
    }

    /**
     * Get a reusable bucket for current thread, cleared as an empty bucket
     */
    public Bucket<K> getNewEmptyBucket() {
        return reusableBuckets.get().clear();
    }

    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return Integer.BYTES;
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        return new DataItemHeader(BUCKET_SIZE,buffer.getInt());
    }

    /**
     * Get if the number of bytes a data item takes when serialized is variable or fixed
     *
     * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
     */
    @Override
    public boolean isVariableSize() {
        return false;
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return BUCKET_SIZE;
    }

    /**
     * Get the current data item serialization version
     */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer      The buffer to read from
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public Bucket<K> deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        Bucket<K> bucket = reusableBuckets.get();
        bucket.getBucketBuffer().put(buffer); // TODO maybe just wrap
        // split bucketSerializationVersion
        bucket.setKeySerializationVersion((int)(dataVersion >> 32));
        return bucket;
    }

    /**
     * Serialize a data item to the output stream returning the size of the data written
     *
     * @param bucket         The data item to serialize
     * @param outputStream Output stream to write to
     */
    @Override
    public int serialize(Bucket<K> bucket, SerializableDataOutputStream outputStream) throws IOException {
        outputStream.write(bucket.getBucketBuffer().array());
        return BUCKET_SIZE;
    }

    /**
     * Copy the serialized data item in dataItemData into the writingStream. Important if serializedVersion is not the
     * same as current serializedVersion then update the data to the latest serialization.
     *
     * @param serializedVersion The serialized version of the data item in dataItemData
     * @param dataItemSize      The size in bytes of the data item dataItemData
     * @param dataItemData      Buffer containing complete data item including the data item header
     * @param writingStream     The stream to write data item out to
     * @return the number of bytes written, this could be the same as dataItemSize or bigger or smaller if
     * serialization version has changed.
     * @throws IOException if there was a problem writing data item to stream or converting it
     */
    @Override
    public int copyItem(long serializedVersion, int dataItemSize, ByteBuffer dataItemData, SerializableDataOutputStream writingStream) throws IOException {
        // TODO need to process bucket and remove deleted entries
        return DataItemSerializer.super.copyItem(serializedVersion, dataItemSize, dataItemData, writingStream);
    }
}
