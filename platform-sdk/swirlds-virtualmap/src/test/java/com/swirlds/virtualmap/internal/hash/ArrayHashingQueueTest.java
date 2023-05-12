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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ArrayHashingQueueTest extends HashingQueueTest {
    private static final int CAPACITY = 100;

    @Override
    protected ArrayHashingQueue<TestKey, TestValue> queue() {
        final ArrayHashingQueue<TestKey, TestValue> q = new ArrayHashingQueue<>();
        q.ensureCapacity(CAPACITY);
        return q;
    }

    @Test
    @DisplayName("Adding more than capacity items throws")
    void addingMoreThanCapacityThrows() {
        final ArrayHashingQueue<TestKey, TestValue> q = queue();
        for (int i = 0; i < CAPACITY; i++) {
            q.appendHashJob();
        }
        assertThrows(AssertionError.class, q::appendHashJob, "Should be over limit");

        // change the capacity and try again
        q.ensureCapacity(10);
        for (int i = 0; i < 10; i++) {
            q.appendHashJob();
        }
        assertThrows(AssertionError.class, q::appendHashJob, "Should be over limit");
    }

    @Test
    @DisplayName("ensureCapacity resets size")
    void ensureCapacityResetsSize() {
        final ArrayHashingQueue<TestKey, TestValue> q = queue();
        for (int i = 0; i < CAPACITY; i++) {
            q.appendHashJob();
        }
        q.ensureCapacity(10);
        assertEquals(0, q.size(), "Size should be zero after ensureCapacity");
    }
}
