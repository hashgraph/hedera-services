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

package com.swirlds.merkledb.collections;

import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;

import java.io.IOException;
import java.nio.file.Path;

public class LongListHeapTest extends AbstractLongListTest<LongListHeap> {

    @Override
    protected LongListHeap createLongListWithChunkSizeInMb(final int chunkSizeInMb) {
        final int impliedLongsPerChunk = Math.toIntExact((chunkSizeInMb * (long) MEBIBYTES_TO_BYTES) / Long.BYTES);
        return new LongListHeap(impliedLongsPerChunk);
    }

    @Override
    protected LongListHeap createFullyParameterizedLongListWith(final int numLongsPerChunk, final long maxLongs) {
        return new LongListHeap(numLongsPerChunk, maxLongs);
    }

    @Override
    protected LongListHeap createLongListFromFile(final Path file) throws IOException {
        return new LongListHeap(file);
    }
}
