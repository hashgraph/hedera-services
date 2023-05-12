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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PeekIteratorTest {

    @Test
    @DisplayName("hasNext on empty iterator returns false")
    void hasNextOnEmpty() {
        final var itr = Collections.emptyIterator();
        final var peek = new PeekIterator<>(itr);
        assertFalse(peek.hasNext(), "Should be false on empty iterator");
    }

    @Test
    @DisplayName("hasNext on non-empty iterator returns true")
    void hasNextOnFull() {
        final var itr = List.of(1, 2, 3).iterator();
        final var peek = new PeekIterator<>(itr);
        assertTrue(peek.hasNext(), "Should be true on non-empty iterator");
    }

    @Test
    @DisplayName("next on empty iterator throws")
    void nextOnEmptyThrows() {
        final var itr = Collections.emptyIterator();
        final var peek = new PeekIterator<>(itr);
        assertThrows(NoSuchElementException.class, peek::next, "Should throw on empty");
    }

    @Test
    @DisplayName("next on consumed iterator throws")
    void nextOnConsumedThrows() {
        final var itr = List.of(1, 2, 3).iterator();
        final var peek = new PeekIterator<>(itr);
        for (int i = 0; i < 3; i++) {
            peek.next();
        }
        assertThrows(NoSuchElementException.class, peek::next, "Should throw on consumed");
    }

    @Test
    @DisplayName("next on iterator with elements returns")
    void nextOnFullOk() {
        final var itr = List.of(1, 2, 3).iterator();
        final var peek = new PeekIterator<>(itr);
        assertEquals(1, peek.next(), "First element should be 1");
        assertEquals(2, peek.next(), "Second element should be 2");
        assertEquals(3, peek.next(), "Third element should be 3");
    }

    @Test
    @DisplayName("peek on empty iterator throws")
    void peekOnEmptyThrows() {
        final var itr = Collections.emptyIterator();
        final var peek = new PeekIterator<>(itr);
        assertThrows(NoSuchElementException.class, peek::peek, "Should throw on empty");
    }

    @Test
    @DisplayName("peek on consumed iterator throws")
    void peekOnConsumedThrows() {
        final var itr = List.of(1, 2, 3).iterator();
        final var peek = new PeekIterator<>(itr);
        for (int i = 0; i < 3; i++) {
            peek.next();
        }
        assertThrows(NoSuchElementException.class, peek::peek, "Should throw on consumed");
    }

    @Test
    @DisplayName("peek on iterator with elements returns")
    void peekOnFullOk() {
        final var itr = List.of(1, 2, 3).iterator();
        final var peek = new PeekIterator<>(itr);
        assertEquals(1, peek.peek(), "First element should be 1");
        peek.next();
        assertEquals(2, peek.peek(), "Second element should be 2");
        peek.next();
        assertEquals(3, peek.peek(), "Third element should be 3");
    }

    @Test
    @DisplayName("peek twice on the same element is OK")
    void peekTwice() {
        final var itr = List.of(1, 2, 3).iterator();
        final var peek = new PeekIterator<>(itr);
        assertEquals(1, peek.peek(), "First element should be 1");
        assertEquals(1, peek.peek(), "First element should be 1");
    }

    @Test
    @DisplayName("peek followed by next returns the same element")
    void peekNextHasNext() {
        final int max = 100;
        final var itr = IntStream.range(0, max).iterator();
        final var peek = new PeekIterator<>(itr);

        int attempts = 0;
        int elements = 0;
        while (peek.hasNext() && attempts++ < max) {
            final int expected = elements++;
            assertEquals(expected, peek.peek(), "Element " + expected + " was not correct");
            assertEquals(peek.peek(), peek.next(), "Element " + expected + " was not correct");
        }

        assertEquals(max, elements, "Should have seen all 0-99 elements");
    }
}
