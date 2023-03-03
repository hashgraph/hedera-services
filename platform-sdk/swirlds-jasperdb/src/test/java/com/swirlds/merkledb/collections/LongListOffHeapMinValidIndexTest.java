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

package com.swirlds.merkledb.collections;

import static com.swirlds.merkledb.collections.LongList.IMPERMISSIBLE_VALUE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LongListOffHeapMinValidIndexTest {

    public static final int MAX_LONGS = 1000;
    private LongListOffHeap list;

    @BeforeEach
    public void setUp() {
        final int longsPerChunk = 3;
        final int reservedBufferLength = 1;
        list = new LongListOffHeap(longsPerChunk, MAX_LONGS, reservedBufferLength);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Update min valid index behavior with invalid boundaries")
    public void testUpdateMinValidIndexNegativeValue() {
        assertThrows(IndexOutOfBoundsException.class, () -> list.updateValidRange(-1, maxValidIndex()));
    }

    private long maxValidIndex() {
        return list.size() - 1;
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Min valid index exceeds the list size")
    public void testUpdateMinValidIndexOnEmptyList() {
        // updateMinValidIndex is no op. It has no effect in this case, new memory chunks are not created

        assertDoesNotThrow(() -> list.updateValidRange(0, maxValidIndex()));
        assertMemoryChunksNumber(0);
        assertDoesNotThrow(() -> list.updateValidRange(1, maxValidIndex()));
        assertMemoryChunksNumber(0);

        fill(2);
        assertMemoryChunksNumber(1);
        assertDoesNotThrow(() -> list.updateValidRange(3, maxValidIndex()));
        assertMemoryChunksNumber(1);
    }

    @SuppressWarnings("resource")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Attempt to create LongListOffHeap with too many memory chunks")
    public void testInvalidMemoryChunkNumber() {
        new LongListOffHeap(1, 32768, 1);
        assertThrows(IllegalArgumentException.class, () -> new LongListOffHeap(1, 32769, 1));
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Update min valid index behavior in a list with a single chunk")
    public void testUpdateMinValidIndexOneChunk() {
        // one chunk: 0-2
        fill(2);
        assertHasValuesInRange(0, 2);
        assertMemoryChunksNumber(1);

        list.updateValidRange(1, maxValidIndex());

        assertMemoryChunksNumber(1);
        assertEmptyUpToIndex(0);
        assertHasValuesInRange(1, 2);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Update min valid index behavior in a list with a single chunk to the same value")
    public void testUpdateMinValidIndexOneChunkToSameValue() {
        // one chunk: 0-2
        fill(2);
        assertHasValuesInRange(0, 2);
        assertMemoryChunksNumber(1);

        list.updateValidRange(1, maxValidIndex());
        // one more time
        list.updateValidRange(1, maxValidIndex());

        assertMemoryChunksNumber(1);
        assertEmptyUpToIndex(0);
        assertHasValuesInRange(1, 2);
    }

    private void assertMemoryChunksNumber(final int expected) {
        assertEquals(expected, list.dataCopy().stream().filter(Objects::nonNull).count());
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Update min valid index behavior in a list of two chunks")
    public void testUpdateMinValidIndexTwoChunks() {
        // two chunks: 0-2, 3-5
        fill(5);
        assertHasValuesInRange(0, 5);
        assertMemoryChunksNumber(2);

        // 4 is min valid index, 3 is a buffer. 0-2 should be freed
        list.updateValidRange(4, maxValidIndex());

        assertEmptyUpToIndex(3);
        assertHasValuesInRange(5, 5);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Update min valid index to the last index in a list of two chunks")
    public void testUpdateMinValidIndexTwoChunksLastIndex() {
        // two chunks: 0-2, 3-5
        fill(5);

        list.updateValidRange(5, maxValidIndex());
        assertEmptyUpToIndex(4);

        assertHasValuesInRange(5, 5);
        // it never discards the last chunk because there is at least one valid index
        assertMemoryChunksNumber(1);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Increase and decrease min valid index in a list of two chunks")
    public void testUpdateMinValidIndexTwoChunksTwice() {
        // two chunks: 0-2, 3-5
        fill(5);

        list.updateValidRange(5, maxValidIndex());
        // shrinks
        assertMemoryChunksNumber(1);

        list.updateValidRange(2, maxValidIndex());
        // expands
        assertMemoryChunksNumber(2);

        assertEmptyUpToIndex(4);
        assertHasValuesInRange(5, 5);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Expand on write at the index lesser than min valid index")
    public void testExpandOnWriteBeforeMinValidIndex() {
        // three chunks: 0-2, 3-5, 6-8
        fill(8);
        assertMemoryChunksNumber(3);

        list.updateValidRange(8, maxValidIndex());
        assertMemoryChunksNumber(1);

        list.put(0, 1);
        // it allows non-contiguous list, therefore chunk at index 1 is absent
        assertMemoryChunksNumber(2);

        list.put(1, 2);
        assertMemoryChunksNumber(2);
        assertEquals(IMPERMISSIBLE_VALUE, list.get(3));

        list.put(3, 4);
        assertMemoryChunksNumber(3);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Make sure that the chunk is not deleted if it has no data but required for the buffer")
    public void testKeepChunkForBuffer() {
        // three chunks: 0-2, 3-5, 6-8
        fill(8);
        assertMemoryChunksNumber(3);

        list.updateValidRange(6, maxValidIndex());

        // one chunk has data, another chunk is kept for the buffer
        assertMemoryChunksNumber(2);

        assertEmptyUpToIndex(5);
        assertHasValuesInRange(6, 8);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Make sure that multiple chunks a not deleted if it has no data but required "
            + "for the buffer that spans across multiple chunks")
    public void testKeepChunkForLargeBuffer() {
        list.close();
        list = new LongListOffHeap(3, 100, 6);

        // three chunks: 0-2, 3-5, 6-8, 9-11
        fill(11);
        assertMemoryChunksNumber(4);

        list.updateValidRange(10, maxValidIndex());

        // one chunk has data, another chunk is kept for the offset
        assertMemoryChunksNumber(3);

        assertEmptyUpToIndex(9);
        assertHasValuesInRange(10, 11);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Make sure that the chunk is not deleted if it has no data but required for the buffer offset "
            + "and the first valid chunk is partially cleaned up")
    public void testKeepChunkForOffsetAndPartiallyCleanUpFirstValid() {
        list.close();
        list = new LongListOffHeap(3, 100, 2);
        // three chunks: 0-2, 3-5, 6-8
        fill(8);
        assertMemoryChunksNumber(3);

        list.updateValidRange(7, maxValidIndex());

        // one chunk has data, another chunk is kept for the offset
        assertMemoryChunksNumber(2);

        assertEmptyUpToIndex(6);
        assertHasValuesInRange(7, 8);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Make sure that if we shrink the list twice, all the chunks that have to be deleted are deleted")
    public void testShrinkTwice() {
        // three chunks: 0-2, 3-5, 6-8
        fill(11);
        assertMemoryChunksNumber(4);

        list.updateValidRange(6, maxValidIndex());
        list.updateValidRange(10, maxValidIndex());

        // one chunk has data, another chunk is kept for the offset
        assertMemoryChunksNumber(1);

        assertEmptyUpToIndex(9);
        assertHasValuesInRange(10, 11);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Make sure that putIfEquals doesn't create memory chunks unnecessarily")
    public void testPutIfEqualQuitsEarly() {
        // four chunks: 0-2, 3-5, 6-8, 9-11
        fill(11);
        assertMemoryChunksNumber(4);

        list.updateValidRange(10, maxValidIndex());
        // one chunk has data, another chunk is kept for the offset
        assertMemoryChunksNumber(1);

        assertFalse(list.putIfEqual(1L, 2L, 42L));

        // putIfEqual doesn't result in memory block creation of indices that are lesser than
        // minValidIndex
        assertMemoryChunksNumber(1);
    }

    public static final int NUM_LONGS_PER_CHUNK = 10000;
    public static final int INITIAL_DATA_SIZE = 10_000_000;

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Make sure that the list releases allocated memory")
    @Execution(SAME_THREAD) // to make sure that the other tests don't mess with the memory
    @ParameterizedTest
    @CsvSource(
            value = {
                // truncate the list to one element, it should have only one chunk left
                INITIAL_DATA_SIZE - 1 + ":" + NUM_LONGS_PER_CHUNK * Long.BYTES,
                // truncate half of the list, it should have half + 1 chunks
                INITIAL_DATA_SIZE / 2 + ":" + (INITIAL_DATA_SIZE / 2 + NUM_LONGS_PER_CHUNK) * Long.BYTES
            },
            delimiter = ':')
    public void allocateAndRelease(final long newMinValidIndex, final long expectedAmountOfMemoryUsed)
            throws InterruptedException {
        final BufferPoolMXBean directPool = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .filter(v -> v.getName().equals("direct"))
                .findFirst()
                .get();
        // this is an attempt to stabilize memory footprint
        System.gc();
        Thread.sleep(10);

        final long initialMemoryAmount = directPool.getMemoryUsed();
        // 8K is the minimal memory consumption
        list = new LongListOffHeap(NUM_LONGS_PER_CHUNK, INITIAL_DATA_SIZE, 200);
        // fill with some data
        for (int i = 0; i < INITIAL_DATA_SIZE; i++) {
            list.put(i, i + 1);
        }

        final long filledListMemoryAmount = directPool.getMemoryUsed() - initialMemoryAmount;
        assertEquals(
                INITIAL_DATA_SIZE * Long.BYTES,
                filledListMemoryAmount,
                "Unexpected amount of memory consumed by the filled list");


        list.updateValidRange(newMinValidIndex, INITIAL_DATA_SIZE - 1);

        final long truncatedListMemoryAmount = directPool.getMemoryUsed() - initialMemoryAmount;

        assertEquals(
                expectedAmountOfMemoryUsed,
                truncatedListMemoryAmount,
                "Unexpected amount of memory consumed by the truncated list");
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @Test
    @DisplayName("Make sure that off-heap memory consumption calculation is correct")
    public void testGetOffHeapConsumptionMb() {
        // empty list doesn't consume off-heap memory
        assertEquals(0, list.getOffHeapConsumption());

        // fill one element in the first chunk
        fill(0);
        assertEquals(24, list.getOffHeapConsumption());

        // fill three elements in the first chunk
        fill(2);
        // memory consumption remains the same
        assertEquals(24, list.getOffHeapConsumption());

        // fill 12 elements in the first 4 chunks
        fill(11);
        // memory consumption increased
        assertEquals(96, list.getOffHeapConsumption());

        // shrink the list, release three chunks
        list.updateValidRange(10, maxValidIndex());
        // memory consumption reduced to size of a single chunk
        assertEquals(24, list.getOffHeapConsumption());

        // creating a "hole" in the list - only the first and the last chunks have data
        fill(0);
        // memory consumption reduced to size of a single chunk
        assertEquals(48, list.getOffHeapConsumption());
    }

    /**
     * Assert empty values up to index 'inclusive'.
     */
    private void assertEmptyUpToIndex(final int inclusive) {
        for (int i = 0; i <= inclusive; i++) {
            assertEquals(IMPERMISSIBLE_VALUE, list.get(i));
        }
    }

    /**
     * Assert values in a range (inclusive on both sides)
     */
    private void assertHasValuesInRange(final int leftInclusive, final int rightInclusive) {
        for (int i = leftInclusive; i <= rightInclusive; i++) {
            assertEquals(i + 1, list.get(i));
        }
    }

    /**
     * Fill the list with values calculated as "index + 1" up to maxIndexInclusive.
     *
     * @param maxIndexInclusive right boundary of a segment with values
     */
    public void fill(final int maxIndexInclusive) {
        for (int i = 0; i <= maxIndexInclusive; i++) {
            list.put(i, i + 1);
        }
    }

    @AfterEach
    public void cleanUp() {
        list.close();
    }
}
