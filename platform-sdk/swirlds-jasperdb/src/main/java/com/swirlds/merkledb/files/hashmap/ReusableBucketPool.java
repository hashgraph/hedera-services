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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ReusableBucketPool<K extends VirtualKey<? super K>> {

    private static final int POOL_SIZE = 1024;

    private final BlockingQueue<Bucket<K>> availableBuckets = new ArrayBlockingQueue<>(POOL_SIZE, false);

    public ReusableBucketPool(final BucketSerializer<K> serializer) {
        try {
            for (int i = 0; i < POOL_SIZE; i++) {
                availableBuckets.put(new Bucket<>(serializer.getKeySerializer(), this));
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Bucket<K> getBucket() {
        try {
            final Bucket<K> bucket = availableBuckets.take();
            bucket.clear();
            return bucket;
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void releaseBucket(final Bucket<K> bucket) {
        assert bucket.getBucketPool() == this;
        final boolean added = availableBuckets.add(bucket);
        assert added;
    }
}
