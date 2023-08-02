/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.queue;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UniqueValueBlockingQueueTest {

    private final String test1 = "test1";
    private final String test2 = "test2";
    private final String test3 = "test3";
    private UniqueValueBlockingQueue<String> uniqueQueue;

    @BeforeEach
    void setUp() {
        uniqueQueue = new UniqueValueBlockingQueue<>(2);
    }

    @Test
    void testAddUniqueValue() {
        assertTrue(uniqueQueue.add(test1));
        assertEquals(1, uniqueQueue.size());
        assertFalse(uniqueQueue.add(test1));
        assertEquals(1, uniqueQueue.size());

        assertTrue(uniqueQueue.add(test2));

        // reached capacity
        assertThrows(IllegalStateException.class, () -> uniqueQueue.add(test3));
    }

    @Test
    void testPutUniqueValue() throws InterruptedException {
        uniqueQueue.put(test1);
        uniqueQueue.put(test2);
        assertTrue(uniqueQueue.contains(test1));
        assertTrue(uniqueQueue.contains(test2));
        assertEquals(2, uniqueQueue.size());
        uniqueQueue.put(test1);
        uniqueQueue.put(test2);
        assertEquals(2, uniqueQueue.size());
    }

    @Test
    void testPutUniqueValueWithBlocking() throws InterruptedException {

        uniqueQueue.put(test1);
        uniqueQueue.put(test2);
        assertTrue(uniqueQueue.contains(test1));
        assertTrue(uniqueQueue.contains(test2));

        commonPool().execute(() -> {
            try {
                // the thread will be blocked till the moment test1 is removed from the queue
                uniqueQueue.put(test3);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(test1, uniqueQueue.poll());
        assertEventuallyTrue(() -> uniqueQueue.contains(test3), Duration.ofMillis(10), "test3 not added");

        assertEquals(test2, uniqueQueue.poll());
        assertEquals(test3, uniqueQueue.poll());

        assertEquals(0, uniqueQueue.size());
    }

    @Test
    void testOfferUniqueValue() {
        assertTrue(uniqueQueue.offer(test1));
        assertFalse(uniqueQueue.offer(test1));
        assertTrue(uniqueQueue.offer(test2));
        // reached queue capacity
        assertFalse(uniqueQueue.offer(test3));
    }

    @Test
    void testOfferWithTimeout() throws InterruptedException {
        assertTrue(uniqueQueue.offer(test1, 1, MILLISECONDS));
        assertFalse(uniqueQueue.offer(test1, 1, MILLISECONDS));
        assertTrue(uniqueQueue.offer(test2, 1, MILLISECONDS));
        commonPool().execute(() -> uniqueQueue.poll());
        assertTrue(uniqueQueue.offer(test3, 10, MILLISECONDS));
    }

    @Test
    void testRemove() {
        uniqueQueue.add(test1);
        assertEquals(test1, uniqueQueue.remove());
        assertFalse(uniqueQueue.contains(test1));
    }

    @Test
    void testRemoveWithParameter() {
        uniqueQueue.add(test1);
        uniqueQueue.add(test2);
        assertTrue(uniqueQueue.remove(test2));
        assertFalse(uniqueQueue.remove(test3));

        assertEquals(1, uniqueQueue.size());
        assertEquals(test1, uniqueQueue.poll());
    }

    @Test
    void testPoll() {
        uniqueQueue.add(test1);
        assertEquals(test1, uniqueQueue.poll());
        assertNull(uniqueQueue.poll());
    }

    @Test
    void testContains() {
        uniqueQueue.add(test1);
        assertTrue(uniqueQueue.contains(test1));
        assertFalse(uniqueQueue.contains(test2));
    }

    @Test
    public void testClear() {
        uniqueQueue.add(test1);
        uniqueQueue.add(test2);
        uniqueQueue.clear();
        assertFalse(uniqueQueue.contains(test1));
        assertFalse(uniqueQueue.contains(test2));
        assertEquals(0, uniqueQueue.size());
    }

    @Test
    public void testUnsupportedOperations() {
        assertThrows(UnsupportedOperationException.class, () -> uniqueQueue.addAll(null));
        assertThrows(UnsupportedOperationException.class, () -> uniqueQueue.retainAll(null));
        assertThrows(UnsupportedOperationException.class, () -> uniqueQueue.removeAll(null));
        assertThrows(UnsupportedOperationException.class, () -> uniqueQueue.drainTo(null));
        assertThrows(UnsupportedOperationException.class, () -> uniqueQueue.drainTo(null, 1));
    }
}
