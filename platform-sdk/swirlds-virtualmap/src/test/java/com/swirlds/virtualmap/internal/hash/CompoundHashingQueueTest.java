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

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CompoundHashingQueueTest extends HashingQueueTest {
    @Override
    protected HashingQueue<TestKey, TestValue> queue() {
        final ArrayHashingQueue<TestKey, TestValue> q1 = new ArrayHashingQueue<>();
        q1.ensureCapacity(100);
        final ArrayHashingQueue<TestKey, TestValue> q2 = new ArrayHashingQueue<>();
        q2.ensureCapacity(100);
        return new CompoundHashingQueue<>(q1, q2);
    }

    @Test
    @DisplayName("Must specify both queues")
    void specifyBothQueues() {
        final ArrayHashingQueue<TestKey, TestValue> q1 = new ArrayHashingQueue<>();
        final ArrayHashingQueue<TestKey, TestValue> q2 = new ArrayHashingQueue<>();
        assertThrows(NullPointerException.class, () -> new CompoundHashingQueue<>(q1, null), "Should be illegal");
        assertThrows(NullPointerException.class, () -> new CompoundHashingQueue<>(null, q2), "Should be illegal");
        assertThrows(NullPointerException.class, () -> new CompoundHashingQueue<>(null, null), "Should be illegal");
    }
}
