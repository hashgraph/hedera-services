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

import com.swirlds.merkledb.ExampleLongKeyFixedSize;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReusableBucketPoolTest {

    @Test
    public void test1() {
        final BucketSerializer<ExampleLongKeyFixedSize> serializer =
                new BucketSerializer<>(new ExampleLongKeyFixedSize.Serializer());
        final ReusableBucketPool<ExampleLongKeyFixedSize> pool = new ReusableBucketPool<>(2, serializer);
        final Bucket<ExampleLongKeyFixedSize> bucket1 = pool.getBucket();
        Assertions.assertNotNull(bucket1);
        final Bucket<ExampleLongKeyFixedSize> bucket2 = pool.getBucket();
        Assertions.assertNotNull(bucket2);
        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket1));
        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket2));
        final Bucket<ExampleLongKeyFixedSize> bucket3 = pool.getBucket();
        Assertions.assertNotNull(bucket3);
        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket3));
    }

    @Test
    public void test2() {
        final BucketSerializer<ExampleLongKeyFixedSize> serializer =
                new BucketSerializer<>(new ExampleLongKeyFixedSize.Serializer());
        final ReusableBucketPool<ExampleLongKeyFixedSize> pool = new ReusableBucketPool<>(2, serializer);
        final Bucket<ExampleLongKeyFixedSize> bucket1 = pool.getBucket();
        Assertions.assertNotNull(bucket1);
        final Bucket<ExampleLongKeyFixedSize> bucket2 = pool.getBucket();
        Assertions.assertNotNull(bucket2);
        final AtomicBoolean bucket2Released = new AtomicBoolean(false);
        new Thread(() -> {
                    // This call should block until bucket2 is released
                    final Bucket<ExampleLongKeyFixedSize> bucket3 = pool.getBucket();
                    Assertions.assertNotNull(bucket3);
                    synchronized (bucket2Released) {
                        Assertions.assertTrue(bucket2Released.get());
                    }
                    Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket3));
                })
                .start();
        synchronized (bucket2Released) {
            Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket2));
            bucket2Released.set(true);
        }
        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket1));
    }

    @Test
    public void test3() {
        final BucketSerializer<ExampleLongKeyFixedSize> serializer =
                new BucketSerializer<>(new ExampleLongKeyFixedSize.Serializer());
        final ReusableBucketPool<ExampleLongKeyFixedSize> pool = new ReusableBucketPool<>(2, serializer);
        final Bucket<ExampleLongKeyFixedSize> bucket1 = pool.getBucket();
        Assertions.assertNotNull(bucket1);
        final Bucket<ExampleLongKeyFixedSize> bucket2 = pool.getBucket();
        Assertions.assertNotNull(bucket2);
        final AtomicBoolean bucket1Released = new AtomicBoolean(false);
        final AtomicBoolean bucket2Released = new AtomicBoolean(false);
        new Thread(() -> {
                    // This call should block until bucket1 or bucket4 is released
                    final Bucket<ExampleLongKeyFixedSize> bucket3 = pool.getBucket();
                    Assertions.assertNotNull(bucket3);
                    synchronized (bucket1Released) {
                        Assertions.assertTrue(bucket1Released.get());
                    }
                    synchronized (bucket2Released) {
                        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket3));
                    }
                })
                .start();
        new Thread(() -> {
                    // This call should block until bucket1 or bucket3 is released
                    final Bucket<ExampleLongKeyFixedSize> bucket4 = pool.getBucket();
                    Assertions.assertNotNull(bucket4);
                    synchronized (bucket2Released) {
                        Assertions.assertFalse(bucket2Released.get());
                        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket2));
                    }
                    Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket4));
                })
                .start();
        synchronized (bucket1Released) {
            Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket1));
            bucket1Released.set(true);
        }
    }

    @Test
    public void test4() throws Exception {
        final BucketSerializer<ExampleLongKeyFixedSize> serializer =
                new BucketSerializer<>(new ExampleLongKeyFixedSize.Serializer());
        final ReusableBucketPool<ExampleLongKeyFixedSize> pool = new ReusableBucketPool<>(2, serializer);
        final Bucket<ExampleLongKeyFixedSize> bucket1 = pool.getBucket();
        Assertions.assertNotNull(bucket1);
        final Bucket<ExampleLongKeyFixedSize> bucket2 = pool.getBucket();
        Assertions.assertNotNull(bucket2);
        final Set<Thread> threads = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            final Thread t = new Thread(() -> {
                final Bucket<ExampleLongKeyFixedSize> bucket3 = pool.getBucket();
                Assertions.assertNotNull(bucket3);
                Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket3));
            });
            threads.add(t);
            t.start();
        }
        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket1));
        for (final Thread t : threads) {
            t.join();
        }
        Assertions.assertDoesNotThrow(() -> pool.releaseBucket(bucket2));
    }
}
