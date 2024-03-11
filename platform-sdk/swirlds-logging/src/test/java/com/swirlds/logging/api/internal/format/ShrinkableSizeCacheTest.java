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

package com.swirlds.logging.api.internal.format;

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.base.test.fixtures.concurrent.TestExecutor;
import com.swirlds.base.test.fixtures.concurrent.WithTestExecutor;
import org.junit.jupiter.api.Test;

@WithTestExecutor
class ShrinkableSizeCacheTest {

    @Test
    void testCacheSize() throws InterruptedException {
        ShrinkableSizeCache<Integer, String> cache = new ShrinkableSizeCache<>(3, 10);

        cache.put(1, "One");
        cache.put(2, "Two");
        cache.put(3, "Three");

        assertEquals(3, cache.size());

        cache.put(4, "Four");
        Thread.sleep(11);

        assertEquals(3, cache.size());
    }

    @Test
    void testEldestEntriesRemoval() throws InterruptedException {
        ShrinkableSizeCache<Integer, String> cache = new ShrinkableSizeCache<>(3, 10);

        cache.put(1, "One");
        cache.put(2, "Two");
        cache.put(3, "Three");
        cache.put(4, "Four");
        Thread.sleep(11);
        assertNull(cache.get(1)); // The eldest entry should be removed
    }

    @Test
    void testBasicOperations() {
        ShrinkableSizeCache<Integer, String> cache = new ShrinkableSizeCache<>(3);

        cache.put(1, "One");
        cache.put(2, "Two");
        cache.put(3, "Three");

        assertTrue(cache.containsKey(1));
        assertTrue(cache.containsValue("Two"));
        assertEquals("Three", cache.get(3));
    }

    @Test
    void testConcurrency(TestExecutor executor) throws InterruptedException {
        ShrinkableSizeCache<Integer, String> cache = new ShrinkableSizeCache<>(50);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                cache.put(i, "Value" + i);
                cache.get(i);
            }
        };

        executor.executeAndWait(task, task);
        // No exception should occur during concurrent access
        Thread.sleep(1010);
        assertEquals(50, cache.size());
    }
}
