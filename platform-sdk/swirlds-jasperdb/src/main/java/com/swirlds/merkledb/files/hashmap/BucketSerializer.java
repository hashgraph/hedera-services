/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files.hashmap;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.merkledb.serialize.BaseSerializer;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Serializer for writing buckets into a DataFile.
 *
 * @param <K> The map key type stored in the buckets
 */
public class BucketSerializer<K extends VirtualKey> implements BaseSerializer<Bucket<K>> {

    /** Bucket pool used by this serializer */
    private final ReusableBucketPool<K> reusableBucketPool;

    /**
     * How many of the low-order bytes in the serialization version are devoted to non-key
     * serialization metadata.
     */
    private static final int LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION = 32;
    /** The version number for serialization data format for this bucket */
    private static final int BUCKET_SERIALIZATION_VERSION = 1;

    /** The current combined serialization version, for both bucket header and key serializer */
    private final long currentSerializationVersion;
    /** The key serializer that we use for keys in buckets */
    private final KeySerializer<K> keySerializer;

    public BucketSerializer(final KeySerializer<K> keySerializer) {
        this.keySerializer = keySerializer;
        long keyVersion = keySerializer.getCurrentDataVersion();
        if (Long.numberOfLeadingZeros(keyVersion) < Integer.SIZE) {
            throw new IllegalArgumentException(
                    "KeySerializer versions used in buckets have to be less than a integer.");
        }
        currentSerializationVersion =
                (keySerializer.getCurrentDataVersion() << LOW_ORDER_BYTES_FOR_NON_KEY_SERIALIZATION_VERSION)
                        | BUCKET_SERIALIZATION_VERSION;
        reusableBucketPool = new ReusableBucketPool<>(pool -> new ParsedBucket<>(keySerializer, pool));
    }

    /**
     * Get the key serializer.
     *
     * @return a key serializer
     */
    public KeySerializer<K> getKeySerializer() {
        return keySerializer;
    }

    /**
     * Reusable bucket pool for this bucket serializer.
     *
     * @return This serializer's reusable bucket pool.
     */
    public ReusableBucketPool<K> getBucketPool() {
        return reusableBucketPool;
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    /**
     * Get the current serialization version. This a combination of the bucket header's
     * serialization version and the KeySerializer's serialization version.
     */
    @Override
    public long getCurrentDataVersion() {
        return currentSerializationVersion;
    }

    @Override
    public int getSerializedSize(final Bucket<K> bucket) {
        return bucket.sizeInBytes();
    }

    @Override
    public void serialize(@NonNull final Bucket<K> bucket, @NonNull final WritableSequentialData out) {
        bucket.writeTo(out);
    }

    @Override
    public Bucket<K> deserialize(@NonNull final ReadableSequentialData in) {
        final Bucket<K> bucket = reusableBucketPool.getBucket();
        bucket.readFrom(in);
        return bucket;
    }
}
