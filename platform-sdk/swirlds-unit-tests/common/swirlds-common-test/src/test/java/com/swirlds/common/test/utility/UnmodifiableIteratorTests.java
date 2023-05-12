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

package com.swirlds.common.test.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.utility.UnmodifiableIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnmodifiableIterator Tests")
class UnmodifiableIteratorTests {

    @Test
    @DisplayName("Null Base Iterator Test")
    void nullBaseIteratorTest() {
        assertThrows(IllegalArgumentException.class, () -> new UnmodifiableIterator<>(null));
    }

    @Test
    @DisplayName("Iteration Test")
    void iterationTest() {
        final List<Integer> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(i);
        }

        final List<Integer> resultingData = new ArrayList<>();
        final Iterator<Integer> it = new UnmodifiableIterator<>(data.iterator());
        while (it.hasNext()) {
            resultingData.add(it.next());
            assertThrows(UnsupportedOperationException.class, it::remove);
        }
        assertThrows(NoSuchElementException.class, it::next);

        assertEquals(data, resultingData);
        for (int i = 0; i < 100; i++) {
            assertEquals(i, resultingData.get(i));
        }
    }

    @Test
    @DisplayName("forEachRemaining() Test")
    void forEachRemainingTest() {
        final List<Integer> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(i);
        }

        final List<Integer> resultingData = new ArrayList<>();
        final Iterator<Integer> it = new UnmodifiableIterator<>(data.iterator());
        it.forEachRemaining(x -> {
            resultingData.add(x);
            assertThrows(UnsupportedOperationException.class, it::remove);
        });
        assertThrows(NoSuchElementException.class, it::next);

        assertEquals(data, resultingData);
        for (int i = 0; i < 100; i++) {
            assertEquals(i, resultingData.get(i));
        }
    }
}
