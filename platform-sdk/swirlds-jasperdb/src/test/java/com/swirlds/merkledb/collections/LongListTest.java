/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.collections;

import static com.swirlds.merkledb.collections.LongListOffHeap.DEFAULT_RESERVED_BUFFER_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LongListTest {

    @Test
    void constructWithTooBigChunkSizeThrowsException() {
        assertThrows(
                ArithmeticException.class,
                () -> new LongListHeap((Integer.MAX_VALUE / 8) + 1, Integer.MAX_VALUE, 0),
                "Check that ArithmeticException of num longs per chuck is too big");
        assertThrows(
                IllegalArgumentException.class,
                () -> new LongListHeap(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 0),
                "Check that IllegalArgumentException of num longs per chuck is too big");
    }

    @Test
    void spliteratorEdgeCasesWork() {
        final LongConsumer firstConsumer = mock(LongConsumer.class);
        final LongConsumer secondConsumer = mock(LongConsumer.class);

        final LongListHeap list = new LongListHeap(32, 32, 0);
        for (int i = 1; i <= 3; i++) {
            list.put(i, i);
        }

        final LongListSpliterator subject = new LongListSpliterator(list);

        assertThrows(
                IllegalStateException.class,
                subject::getComparator,
                "An unordered spliterator should not be asked to provide an ordering");

        final Spliterator.OfLong firstSplit = subject.trySplit();
        assertNotNull(firstSplit, "firstSplit should not be null");
        assertEquals(2, subject.estimateSize(), "Splitting 4 elements should yield 2");
        final Spliterator.OfLong secondSplit = subject.trySplit();
        assertNotNull(secondSplit, "secondSplit should not be null");
        assertEquals(1, subject.estimateSize(), "Splitting 2 elements should yield 1");
        assertNull(subject.trySplit(), "Splitting 1 element should yield null");

        assertTrue(firstSplit.tryAdvance(firstConsumer), "First split should yield 0 first");
        verify(firstConsumer).accept(0);
        assertTrue(firstSplit.tryAdvance(firstConsumer), "First split should yield 1 second");
        verify(firstConsumer).accept(1);
        assertFalse(firstSplit.tryAdvance(firstConsumer), "First split should be exhausted after 2 yields");

        secondSplit.forEachRemaining(secondConsumer);
        verify(secondConsumer).accept(2);
        verifyNoMoreInteractions(secondConsumer);
    }

    @ParameterizedTest
    @MethodSource("provideLongLists")
    void test4089(final AbstractLongList<?> list) {
        // Issue #4089: ArrayIndexOutOfBoundsException from VirtualMap.put()
        final long maxLongs = list.maxLongs;
        final int defaultValue = -1;
        final AtomicBoolean done = new AtomicBoolean();

        IntStream.range(0, 2).parallel().forEach(thread -> {
            if (thread == 0) {
                // Getter
                while (!done.get()) {
                    assertEquals(defaultValue, list.get(maxLongs - 2, defaultValue), "Value should be whats expected.");
                }
            } else {
                // Putter
                list.put(maxLongs - 1, 1);
                done.set(true);
            }
        });
    }

    static Stream<LongList> provideLongLists() {
        final int numLongsPerChunk = 32;
        final int maxLongs = numLongsPerChunk * 4096;
        return Stream.of(
                new LongListHeap(numLongsPerChunk, maxLongs, 0),
                new LongListOffHeap(numLongsPerChunk, maxLongs, DEFAULT_RESERVED_BUFFER_LENGTH));
    }
}
