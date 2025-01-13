/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.merkledb.collections.AbstractLongListTest.SAMPLE_SIZE;
import static com.swirlds.merkledb.collections.AbstractLongListTest.checkData;
import static com.swirlds.merkledb.collections.AbstractLongListTest.populateList;
import static com.swirlds.merkledb.collections.LongListOffHeap.DEFAULT_RESERVED_BUFFER_LENGTH;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Provides specialized or ad hoc tests for certain edge cases in {@link LongList} implementations.
 * These scenarios do not neatly fit into the broader, cross-compatibility tests found in
 * {@link AbstractLongListTest}, but still warrant individual coverage for bug fixes or
 * concurrency concerns.
 */
class LongListAdHocTest {

    @ParameterizedTest
    @MethodSource("provideLongLists")
    void test4089(final AbstractLongList<?> list) {
        list.updateValidRange(0, list.maxLongs - 1);
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

    // Tests https://github.com/hashgraph/hedera-services/issues/16860
    @Test
    void testReallocateThreadLocalBufferWhenMemoryChunkSizeChanges_10K() throws IOException {
        // SAMPLE_SIZE should be 10K for this test
        final int SAMPLE_SIZE = 10_000;

        // Create two long lists with different memory chunk sizes
        var largeMemoryChunkList = new LongListDisk(100, SAMPLE_SIZE * 2, 0, CONFIGURATION);
        var smallMemoryChunkList = new LongListDisk(10, SAMPLE_SIZE * 2, 0, CONFIGURATION);

        // Populate both long lists with sample data and validate
        populateList(largeMemoryChunkList, SAMPLE_SIZE);
        checkData(largeMemoryChunkList, 0, SAMPLE_SIZE);
        populateList(smallMemoryChunkList, SAMPLE_SIZE);
        checkData(smallMemoryChunkList, 0, SAMPLE_SIZE);

        // Capture the original file channel sizes before closing chunks
        final long originalLargeListChannelSize =
                largeMemoryChunkList.getCurrentFileChannel().size();
        final long originalSmallListChannelSize =
                smallMemoryChunkList.getCurrentFileChannel().size();

        // Close all chunks in long lists
        for (int i = 0; i < largeMemoryChunkList.chunkList.length(); i++) {
            final Long chunk = largeMemoryChunkList.chunkList.get(i);
            if (chunk != null) {
                largeMemoryChunkList.closeChunk(chunk);
            }
        }
        for (int i = 0; i < smallMemoryChunkList.chunkList.length(); i++) {
            final Long chunk = smallMemoryChunkList.chunkList.get(i);
            if (chunk != null) {
                smallMemoryChunkList.closeChunk(chunk);
            }
        }

        // Ensure that file channel sizes have not inadvertently grown
        assertEquals(
                originalLargeListChannelSize,
                largeMemoryChunkList.getCurrentFileChannel().size());
        assertEquals(
                originalSmallListChannelSize,
                smallMemoryChunkList.getCurrentFileChannel().size());

        // Tear down
        largeMemoryChunkList.close();
        largeMemoryChunkList.resetTransferBuffer();
        smallMemoryChunkList.close();
        smallMemoryChunkList.resetTransferBuffer();
    }
}
