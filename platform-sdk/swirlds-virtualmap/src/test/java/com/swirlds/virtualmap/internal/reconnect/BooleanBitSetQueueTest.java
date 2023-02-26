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

package com.swirlds.virtualmap.internal.reconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BooleanBitSetQueueTest {
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    @DisplayName("Queue with invalid capacity throws")
    void negativeCapacity(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> new BooleanBitSetQueue(capacity), "Should throw");
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1", // Edge case where we have the smallest possible queue and use it all
        "10, 10", // Case where we use everything available in a single bitset
        "1000, 100", // Case where we have more capacity in the bitset than we use
        "10, 87", // Case where we use more than the capacity that we have
        "10, 90"
    }) // Case where we use exactly several multiples of the capacity that we have
    @DisplayName("Add, remove, and isEmpty, intermixed add and remove")
    void addRemoveIsEmptyOneElementAtATime(final int capacity, final int maxElements) {
        final BooleanBitSetQueue queue = new BooleanBitSetQueue(capacity);
        final Random rand = new Random(42);

        // Try adding one item and removing one item.
        assertTrue(queue.isEmpty(), "Queue should be empty to start with");
        for (int i = 0; i < maxElements; i++) {
            final boolean value = rand.nextBoolean();
            assertTrue(queue.isEmpty(), "Queue should be empty on iteration " + i);
            queue.add(value);
            assertFalse(queue.isEmpty(), "We added something so it shouldn't be empty anymore on iteration " + i);
            assertEquals(value, queue.remove(), "Value was not read correctly on iteration " + i);
            assertTrue(queue.isEmpty(), "Queue should be empty again on iteration " + i);
        }
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "10, 10", "1000, 100", "10, 87", "10, 90"})
    @DisplayName("Add, remove, and isEmpty, fully sequentially")
    void addRemoveIsEmptySequentially(final int capacity, final int maxElements) {
        final BooleanBitSetQueue queue = new BooleanBitSetQueue(capacity);
        final Random rand = new Random(42);

        final boolean[] expected = new boolean[maxElements];
        assertTrue(queue.isEmpty(), "Queue should be empty to start with");

        for (int i = 0; i < maxElements; i++) {
            final boolean value = rand.nextBoolean();
            expected[i] = value;
            queue.add(value);
            assertFalse(queue.isEmpty(), "Should not be empty on iteration " + i);
        }

        for (int i = 0; i < maxElements; i++) {
            assertFalse(queue.isEmpty(), "Should not be empty on iteration " + i);
            assertEquals(expected[i], queue.remove(), "Value was not read correctly on iteration " + i);
        }

        assertTrue(queue.isEmpty(), "Queue should be empty again");
    }
}
