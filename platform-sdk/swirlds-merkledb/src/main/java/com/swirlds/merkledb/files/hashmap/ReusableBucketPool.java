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

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

/**
 * HalfDiskHashMap buckets are somewhat expensive resources. Every bucket has an
 * underlying byte buffer to store bucket data and metadata, and the number of
 * buckets is huge. This class provides a bucket pool, so buckets can be reused
 * rather than created on every read / write call.
 *
 * <p>Bucket pool is accessed from multiple threads:
 * <ul>
 *     <li>Transaction thread, when a key path is loaded from HDHM as a part of
 *     get or getForModify call</li>
 *     <li>Lifecycle thread, when updated bucket is written to disk in the end
 *     of HDHM flushing</li>
 *     <li>HDHM background bucket reading threads</li>
 *     <li>Warmup (aka prefetch) threads</li>
 * </ul>
 *
 * <p>If buckets were created, updated, and then released (marked as available
 * for other threads) on a single thread, this class would be as simple as a
 * single {@link ThreadLocal} object. This is not the case, unfortunately. For
 * example, when HDHM background reading threads read buckets from disk, buckets
 * are requested from the pool by {@link BucketSerializer} as a part of data
 * file collection read call. Then buckets are updated and put to a queue, which
 * is processed on a different thread, virtual pipeline (aka lifecycle) thread.
 * Only after that buckets can be reused. This is why the pool is implemented as
 * an array of buckets with fast concurrent read/write access from multiple
 * threads.
 */
public class ReusableBucketPool {

    /** Default number of reusable buckets in this pool */
    private static final int DEFAULT_POOL_SIZE = 64;

    /** Buckets */
    private final ConcurrentLinkedDeque<Bucket> buckets;

    private final Function<ReusableBucketPool, Bucket> newBucketSupplier;

    /**
     * Creates a new reusable bucket pool of the default size.
     *
     * @param bucketSupplier To create new buckets
     */
    public ReusableBucketPool(final Function<ReusableBucketPool, Bucket> bucketSupplier) {
        this(DEFAULT_POOL_SIZE, bucketSupplier);
    }

    /**
     * Creates a new reusable bucket pool of the specified size.
     *
     * @param bucketSupplier To create new buckets
     */
    public ReusableBucketPool(final int size, Function<ReusableBucketPool, Bucket> bucketSupplier) {
        this.newBucketSupplier = bucketSupplier;
        buckets = new ConcurrentLinkedDeque<>();
        for (int i = 0; i < size; i++) {
            buckets.offerLast(bucketSupplier.apply(this));
        }
    }

    /**
     * Gets a bucket from the pool. If the pool is empty, the calling thread waits
     * until a bucket is released to the pool.
     *
     * @return A bucket that can be used for reads / writes until it's released back
     * to the pool
     */
    public Bucket getBucket() {
        Bucket bucket = buckets.pollLast();
        if (bucket == null) {
            bucket = newBucketSupplier.apply(this);
        }
        bucket.clear();
        return bucket;
    }

    /**
     * Releases a bucket back to this pool. The bucket cannot be used after this call, until it's
     * borrowed from the pool again using {@link #getBucket()}.
     *
     * @param bucket A bucket to release to this pool
     */
    public void releaseBucket(final Bucket bucket) {
        buckets.offerLast(bucket);
    }
}
