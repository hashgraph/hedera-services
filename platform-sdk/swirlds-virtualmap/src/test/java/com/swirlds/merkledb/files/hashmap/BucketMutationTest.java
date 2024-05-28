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

import static com.swirlds.merkledb.files.hashmap.HalfDiskHashMap.INVALID_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BucketMutationTest {

    @Test
    void nullKeyThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new BucketMutation<ExampleLongKeyFixedSize>(null, 1),
                "Null is not allowed");
    }

    @Test
    void createList() {
        final var root = new BucketMutation<>(new ExampleLongKeyFixedSize(1), 10);
        for (int i = 2; i < 100; i++) {
            root.put(new ExampleLongKeyFixedSize(i), 10 * i);
        }

        final AtomicLong index = new AtomicLong(1);
        root.forEachKeyValue((k, ov, v) -> {
            final long i = index.getAndIncrement();
            Assertions.assertEquals(new ExampleLongKeyFixedSize(i), k, "Unexpected key " + k + " for iteration " + i);
            assertEquals(i * 10, v, "Unexpected value " + v + " for iteration " + i);
        });
    }

    @Test
    void updateList() {
        // Test adding the keys out of order, and also updating only the first, middle and last.
        final var root = new BucketMutation<>(new ExampleLongKeyFixedSize(1), 10);
        root.put(new ExampleLongKeyFixedSize(3), 30);
        root.put(new ExampleLongKeyFixedSize(2), 20);
        root.put(new ExampleLongKeyFixedSize(5), 50);
        root.put(new ExampleLongKeyFixedSize(4), 40);

        root.put(new ExampleLongKeyFixedSize(1), 100);
        root.put(new ExampleLongKeyFixedSize(2), 200);
        root.put(new ExampleLongKeyFixedSize(4), 400);

        final var expectedKeys = new LinkedList<>(List.of(
                new ExampleLongKeyFixedSize(1),
                new ExampleLongKeyFixedSize(3),
                new ExampleLongKeyFixedSize(2),
                new ExampleLongKeyFixedSize(5),
                new ExampleLongKeyFixedSize(4)));

        final var expectedValues = new LinkedList<>(List.of(100, 30, 200, 50, 400));

        root.forEachKeyValue((k, ov, v) -> {
            assertEquals(expectedKeys.removeFirst(), k, "Unexpected key");
            assertEquals((long) expectedValues.removeFirst(), v, "Unexpected value");
        });

        assertTrue(expectedKeys.isEmpty(), "Shouldn't have any expected keys left");
        assertTrue(expectedValues.isEmpty(), "Shouldn't have any expected values left");
    }

    @Test
    void updateWithoutOldValue() {
        final var mutation = new BucketMutation<>(new ExampleLongKeyFixedSize(1), 2);
        mutation.put(new ExampleLongKeyFixedSize(1), 3);
        assertEquals(3, mutation.getValue());
        assertEquals(INVALID_VALUE, mutation.getOldValue());
        assertNull(mutation.getNext());
        mutation.putIfEqual(new ExampleLongKeyFixedSize(1), 3, 4);
        assertEquals(4, mutation.getValue());
        assertEquals(INVALID_VALUE, mutation.getOldValue());
        assertNull(mutation.getNext());
        mutation.putIfEqual(new ExampleLongKeyFixedSize(1), 3, 5);
        assertEquals(4, mutation.getValue());
        assertEquals(INVALID_VALUE, mutation.getOldValue());
        assertNull(mutation.getNext());
        mutation.put(new ExampleLongKeyFixedSize(1), 6);
        assertEquals(6, mutation.getValue());
        assertEquals(INVALID_VALUE, mutation.getOldValue());
        assertNull(mutation.getNext());
    }

    @Test
    void updateWithOldValue() {
        final var mutation = new BucketMutation<>(new ExampleLongKeyFixedSize(1), -1, 2);
        mutation.putIfEqual(new ExampleLongKeyFixedSize(1), 3, 4);
        assertEquals(2, mutation.getValue());
        assertNotEquals(INVALID_VALUE, mutation.getOldValue());
        assertNull(mutation.getNext());
        mutation.putIfEqual(new ExampleLongKeyFixedSize(1), 2, 5);
        assertEquals(5, mutation.getValue());
        assertNotEquals(INVALID_VALUE, mutation.getOldValue());
        assertNull(mutation.getNext());
        mutation.put(new ExampleLongKeyFixedSize(1), 6);
        assertEquals(6, mutation.getValue());
        assertEquals(INVALID_VALUE, mutation.getOldValue());
        assertNull(mutation.getNext());
    }

    @Test
    void size() {
        final var root = new BucketMutation<>(new ExampleLongKeyFixedSize(1), 10);
        assertEquals(1, root.size(), "Unexpected size");
        root.put(new ExampleLongKeyFixedSize(3), 30);
        assertEquals(2, root.size(), "Unexpected size");
        root.put(new ExampleLongKeyFixedSize(2), 20);
        assertEquals(3, root.size(), "Unexpected size");
        root.put(new ExampleLongKeyFixedSize(5), 50);
        assertEquals(4, root.size(), "Unexpected size");
        root.put(new ExampleLongKeyFixedSize(4), 40);
        assertEquals(5, root.size(), "Unexpected size");
    }
}
