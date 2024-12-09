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

package com.swirlds.merkledb.collections;

import static com.swirlds.base.units.UnitConstants.MEBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.config.api.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LongListHeapTest extends AbstractLongListTest<LongListHeap> {

    @Override
    protected LongListHeap createLongListWithChunkSizeInMb(final int chunkSizeInMb) {
        final int impliedLongsPerChunk = Math.toIntExact((chunkSizeInMb * (long) MEBIBYTES_TO_BYTES) / Long.BYTES);
        return new LongListHeap(impliedLongsPerChunk);
    }

    @Override
    protected LongListHeap createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs) {
        return new LongListHeap(numLongsPerChunk, maxLongs, 0);
    }

    @Override
    protected LongListHeap createLongListFromFile(final Path file, final Configuration configuration)
            throws IOException {
        return new LongListHeap(file, configuration);
    }

    @Test
    void writeReadSize1(@TempDir final Path tempDir) throws IOException {
        try (final AbstractLongList<?> list = createLongList()) {
            list.updateValidRange(2, 2);
            list.put(2, 1);
            final Path file = tempDir.resolve("writeReadSize1.ll");
            // write longList data
            list.writeToFile(file);
            // check file exists and contains some data
            assertTrue(Files.exists(file), "file does not exist");
            // now try and construct a new LongList reading from the file
            try (final LongList list2 = createLongListFromFile(file, CONFIGURATION)) {
                // now check data and other attributes
                assertEquals(list.capacity(), list2.capacity(), "Unexpected value for list2.capacity()");
                assertEquals(list.size(), list2.size(), "Unexpected value for list2.size()");
                assertEquals(1, list2.get(2));
            }
            // delete file as we are done with it
            Files.delete(file);
        }
    }
}
