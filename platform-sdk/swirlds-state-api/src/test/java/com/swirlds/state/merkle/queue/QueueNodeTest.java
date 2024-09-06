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

package com.swirlds.state.merkle.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.state.test.fixtures.merkle.MerkleTestBase;
import java.util.Iterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueNodeTest extends MerkleTestBase {

    private QueueNode<String> queueNode;

    @BeforeEach
    void setupTest() {
        queueNode = new QueueNode<>(
                FIRST_SERVICE,
                FRUIT_STATE_KEY,
                queueNodeClassId(FRUIT_STATE_KEY),
                onDiskValueSerializerClassId(FRUIT_STATE_KEY),
                onDiskValueClassId(FRUIT_STATE_KEY),
                STRING_CODEC);
    }

    @AfterEach
    void shutdownTest() {
        queueNode.release();
    }

    @Test
    void usesClassIdFromMetadata() {
        assertNotEquals(0x990FF87AD2691DCL, queueNode.getClassId());
    }

    @Test
    void emptyQueuePeek() {
        assertNull(queueNode.peek(), "peek() should return null for empty queue");
    }

    @Test
    void emptyQueueRemove() {
        assertNull(queueNode.remove(), "remove() should return null for empty queue");
    }

    @Test
    void getPeekRemove() {
        queueNode.add(APPLE);
        assertEquals(APPLE, queueNode.peek(), "peek() should return APPLE");
        assertEquals(APPLE, queueNode.remove(), "remove() should return APPLE");
        assertNull(queueNode.peek(), "peek() should return null after remove");
        assertNull(queueNode.remove(), "peek() should return null after remove");
    }

    @Test
    void addMultipleValues() {
        queueNode.add(APPLE);
        queueNode.add(BANANA);
        queueNode.add(CHERRY);
        assertIteratorYieldsValues(queueNode.iterator(), APPLE, BANANA, CHERRY);
    }

    @Test
    void addSameValue() {
        queueNode.add(APPLE);
        queueNode.add(APPLE);
        queueNode.add(APPLE);
        assertIteratorYieldsValues(queueNode.iterator(), APPLE, APPLE, APPLE);
    }

    @Test
    void addRemoveMultipleValues() {
        queueNode.add(APPLE);
        queueNode.add(BANANA);
        queueNode.add(CHERRY);
        assertEquals(APPLE, queueNode.remove(), "remove() should return APPLE");
        queueNode.add(DATE);
        assertIteratorYieldsValues(queueNode.iterator(), BANANA, CHERRY, DATE);
    }

    @Test
    void addRemoveWithCopies() {
        queueNode.add(APPLE);
        queueNode.add(BANANA);
        final QueueNode<String> copy = queueNode.copy();
        queueNode.release();
        queueNode = copy;
        assertEquals(APPLE, queueNode.remove(), "remove() should return APPLE");
        queueNode.add(CHERRY);
        assertIteratorYieldsValues(queueNode.iterator(), BANANA, CHERRY);
    }

    @Test
    void removeAllAfterCopy() {
        queueNode.add(BANANA);
        queueNode.add(CHERRY);
        QueueNode<String> copy = queueNode.copy();
        queueNode.release();
        queueNode = copy;
        assertEquals(BANANA, queueNode.remove(), "remove() should return BANANA");
        copy = queueNode.copy();
        queueNode.release();
        queueNode = copy;
        assertEquals(CHERRY, queueNode.remove(), "remove() should return CHERRY");
        assertNull(queueNode.peek(), "Queue should be empty now");
    }

    private void assertIteratorYieldsValues(final Iterator<String> it, final String... values) {
        for (final String value : values) {
            assertTrue(it.hasNext(), "Iterator should contain a value");
            assertEquals(value, it.next(), "Iterator should yield the value: " + value);
        }
        assertFalse(it.hasNext(), "Iterator should not contain any more items");
    }
}
