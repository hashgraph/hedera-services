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

package com.swirlds.merkledb.files.hashmap;

import com.swirlds.virtualmap.VirtualKey;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ReusableBucketPool<K extends VirtualKey<? super K>> {

    private static final int DEFAULT_POOL_SIZE = 8192;

    private final int poolSize;

    private final AtomicReferenceArray<Bucket<K>> buckets;
    private final AtomicReferenceArray<Object> locks;

    private final AtomicInteger nextA = new AtomicInteger(0);
    private final AtomicInteger nextE = new AtomicInteger(0);

    public ReusableBucketPool(final BucketSerializer<K> serializer) {
        this(DEFAULT_POOL_SIZE, serializer);
    }

    public ReusableBucketPool(final int size, final BucketSerializer<K> serializer) {
        poolSize = size;
        buckets = new AtomicReferenceArray<>(poolSize);
        locks = new AtomicReferenceArray<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            buckets.set(i, new Bucket<>(serializer.getKeySerializer(), this));
            locks.set(i, new Object());
        }
    }

    public Bucket<K> getBucket() {
        final int index = nextA.getAndUpdate(t -> (t + 1) % poolSize);
        Bucket<K> bucket = buckets.getAndSet(index, null);
        if (bucket == null) {
            final Object lock = locks.get(index);
            synchronized (lock) {
                bucket = buckets.getAndSet(index, null);
                while (bucket == null) {
                    try {
                        lock.wait();
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    bucket = buckets.getAndSet(index, null);
                }
            }
        }
        bucket.clear();
        return bucket;
    }

    public void releaseBucket(final Bucket<K> bucket) {
        assert bucket.getBucketPool() == this;
        int index = nextE.getAndUpdate(t -> (t + 1) % poolSize);
        boolean released = buckets.compareAndSet(index, null, bucket);
        while (!released) {
            released = buckets.compareAndSet(index, null, bucket);
        }
        final Object lock = locks.get(index);
        synchronized (lock) {
            lock.notify();
        }
    }

    /*
    private final Bucket<K>[] buckets;

    private int available;

    public ReusableBucketPool(final BucketSerializer<K> serializer) {
        this(DEFAULT_POOL_SIZE, serializer);
    }

    public ReusableBucketPool(final int size, final BucketSerializer<K> serializer) {
        poolSize = size;
        synchronized (this) {
            available = poolSize;
            buckets = new Bucket[poolSize];
            for (int i = 0; i < poolSize; i++) {
                buckets[i] = new Bucket<>(serializer.getKeySerializer(), this);
            }
        }
    }

    public Bucket<K> getBucket() {
        Bucket<K> bucket;
        synchronized (this) {
            while (available == 0) {
                try {
                    wait();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            int index = --available;
            bucket = buckets[index];
            buckets[index] = null;
        }
        bucket.clear();
        return bucket;
    }

    public void releaseBucket(final Bucket<K> bucket) {
        assert bucket.getBucketPool() == this;
        synchronized (this) {
            assert available < poolSize;
            int index = available++;
            assert buckets[index] == null;
            buckets[index] = bucket;
            notify();
        }
    }
    */
}
