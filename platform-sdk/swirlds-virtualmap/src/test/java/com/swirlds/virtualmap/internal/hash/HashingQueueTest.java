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

package com.swirlds.virtualmap.internal.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.VirtualTestBase;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

abstract class HashingQueueTest extends VirtualTestBase {

    protected abstract HashingQueue<TestKey, TestValue> queue();

    @Test
    @DisplayName("Default size is 0")
    void sizeIsZeroByDefault() {
        assertEquals(0, queue().size(), "An empty queue should have zero size");
    }

    @Test
    @DisplayName("Get with index at or above size throws")
    void getWithIndexAtOrAboveZeroThrows() {
        final HashingQueue<TestKey, TestValue> q = queue();
        assertThrows(AssertionError.class, () -> q.get(0), "Should have thrown Exception");
        assertThrows(AssertionError.class, () -> q.get(1), "Should have thrown Exception");
        q.addHashJob(9);
        assertNotNull(q.get(9), "Should not have been null");
        assertThrows(AssertionError.class, () -> q.get(10), "Should have thrown Exception");
        assertThrows(AssertionError.class, () -> q.get(11), "Should have thrown Exception");
    }

    @Test
    @DisplayName("Get on reset queue throws")
    void getWithIndexAtOrAboveZeroOnResetQueueThrows() {
        final HashingQueue<TestKey, TestValue> q = queue();
        assertThrows(AssertionError.class, () -> q.get(0), "Should have thrown Exception");
        assertThrows(AssertionError.class, () -> q.get(1), "Should have thrown Exception");
        q.addHashJob(9);
        q.reset();
        assertThrows(AssertionError.class, () -> q.get(0), "Should have thrown Exception");
        assertThrows(AssertionError.class, () -> q.get(1), "Should have thrown Exception");
    }

    @Test
    @DisplayName("Stream of empty queue is OK")
    void streamOfEmptyQueue() {
        final HashingQueue<TestKey, TestValue> q = queue();
        assertNotNull(q.stream(), "Stream should exist and not be null");
        assertEquals(0, q.stream().count(), "Stream should be empty");
    }

    @Test
    @DisplayName("Reset queue has size 0")
    void resetQueueHasSizeZero() {
        final HashingQueue<TestKey, TestValue> q = queue();
        q.appendHashJob();
        q.appendHashJob();
        q.reset();
        assertEquals(0, q.size(), "Size should be zero after reset");
    }

    @Test
    @DisplayName("Reset queue has empty stream")
    void resetQueueHasEmptyStream() {
        final HashingQueue<TestKey, TestValue> q = queue();
        q.appendHashJob();
        q.appendHashJob();
        q.reset();
        assertNotNull(q.stream(), "Stream should exist and not be null");
        assertEquals(0, q.stream().count(), "Stream should be empty");
    }

    @Test
    @DisplayName("Adding items")
    void addingItems() {
        final HashingQueue<TestKey, TestValue> q = queue();
        q.addHashJob(1).dirtyLeaf(1, appleLeaf(1));
        q.addHashJob(0).dirtyLeaf(2, bananaLeaf(2));
        q.addHashJob(2).dirtyInternal(0, null, null);

        assertEquals(3, q.size(), "The size should be 3");
        assertEquals(2, q.get(0).getPath(), "Banana should be first with index 2");
        assertEquals(1, q.get(1).getPath(), "Apple should be second with index 1");
        assertEquals(0, q.get(2).getPath(), "Root should be first with index 0");

        assertNotNull(q.stream(), "Stream should exist and not be null");

        final List<HashJob<TestKey, TestValue>> streamContents = q.stream().toList();
        assertEquals(3, streamContents.size(), "Stream should have three elements");
        assertSame(q.get(0), streamContents.get(0), "First element should be Banana");
        assertSame(q.get(1), streamContents.get(1), "Second element should be Apple");
        assertSame(q.get(2), streamContents.get(2), "Third element should be root");
    }

    @Test
    @DisplayName("Appending items")
    void appendingItems() {
        final HashingQueue<TestKey, TestValue> q = queue();
        q.appendHashJob().dirtyLeaf(2, bananaLeaf(2));
        q.appendHashJob().dirtyLeaf(1, appleLeaf(1));
        q.appendHashJob().dirtyInternal(0, null, null);

        assertEquals(3, q.size(), "The size should be 3");
        assertEquals(2, q.get(0).getPath(), "Banana should be first with index 2");
        assertEquals(1, q.get(1).getPath(), "Apple should be second with index 1");
        assertEquals(0, q.get(2).getPath(), "Root should be first with index 0");

        assertNotNull(q.stream(), "Stream should exist and not be null");

        final List<HashJob<TestKey, TestValue>> streamContents = q.stream().toList();
        assertEquals(3, streamContents.size(), "Stream should have three elements");
        assertSame(q.get(0), streamContents.get(0), "First element should be Banana");
        assertSame(q.get(1), streamContents.get(1), "Second element should be Apple");
        assertSame(q.get(2), streamContents.get(2), "Third element should be root");
    }

    @Test
    @DisplayName("Copy of null queue throws")
    void copyOfNullThrows() {
        final HashingQueue<TestKey, TestValue> q = queue();
        assertNotNull(q, "Should not be null");
        assertThrows(NullPointerException.class, () -> q.copyFrom(null), "Should have thrown NullPointerException");
    }

    @Test
    @DisplayName("Copy of queue with full source and empty dest")
    void copyOfQueueFullSourceEmptyDest() {
        final HashingQueue<TestKey, TestValue> source = queue();
        final HashingQueue<TestKey, TestValue> dest = queue();

        for (int i = 0; i < 10; i++) {
            source.appendHashJob().dirtyLeaf(i, new VirtualLeafRecord<>(i, new TestKey(i), new TestValue(i)));
        }

        dest.copyFrom(source);
        for (int i = 0; i < 10; i++) {
            assertEquals(
                    new VirtualLeafRecord<>(i, new TestKey(i), new TestValue(i)),
                    dest.get(i).getLeaf(),
                    "Different at index " + i);
        }
    }

    @Test
    @DisplayName("Copy of queue with empty source and full dest")
    void copyOfQueueEmptySourceFullDest() {
        final HashingQueue<TestKey, TestValue> source = queue();
        final HashingQueue<TestKey, TestValue> dest = queue();

        for (int i = 0; i < 10; i++) {
            dest.appendHashJob().dirtyLeaf(i, new VirtualLeafRecord<>(i, new TestKey(i), new TestValue(i)));
        }

        dest.copyFrom(source);
        assertEquals(0, dest.size(), "Dest should now be empty");
    }

    @Test
    @DisplayName("Copy of queue with empty source and empty dest")
    void copyOfQueueEmptySourceEmptyDest() {
        final HashingQueue<TestKey, TestValue> source = queue();
        final HashingQueue<TestKey, TestValue> dest = queue();

        dest.copyFrom(source);
        assertEquals(0, dest.size(), "dest should still be empty");
    }

    @Test
    @DisplayName("Copy of queue with full source and full dest")
    void copyOfQueueFullSourceFullDest() {
        final HashingQueue<TestKey, TestValue> source = queue();
        final HashingQueue<TestKey, TestValue> dest = queue();

        for (int i = 0; i < 10; i++) {
            final int j = i + 100;
            source.appendHashJob().dirtyLeaf(i, new VirtualLeafRecord<>(i, new TestKey(i), new TestValue(i)));
            dest.appendHashJob().dirtyLeaf(j, new VirtualLeafRecord<>(j, new TestKey(j), new TestValue(j)));
        }

        dest.copyFrom(source);

        for (int i = 0; i < 10; i++) {
            assertEquals(
                    new VirtualLeafRecord<>(i, new TestKey(i), new TestValue(i)),
                    dest.get(i).getLeaf(),
                    "Different at index " + i);
        }
    }
}
