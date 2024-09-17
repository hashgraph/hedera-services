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

package com.hedera.node.app.blocks.impl;

import static com.hedera.node.app.blocks.impl.ConcurrentStreamingTreeHasher.rootHashFrom;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.blocks.StreamingTreeHasher.Status;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConcurrentStreamingTreeHasherTest {
    private static final int LEAF_SIZE = 48;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private final NaiveStreamingTreeHasher comparison = new NaiveStreamingTreeHasher();
    private final ConcurrentStreamingTreeHasher subject = new ConcurrentStreamingTreeHasher(ForkJoinPool.commonPool());

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5, 32, 69, 100, 123, 234})
    void testAddLeafAndRootHash(final int numLeaves) {
        Bytes lastLeaf = null;
        var status = Status.EMPTY;
        for (int i = 1; i <= numLeaves; i++) {
            final var contents = new byte[LEAF_SIZE];
            RANDOM.nextBytes(contents);
            final var leaf = Bytes.wrap(contents);
            subject.addLeaf(leaf);
            comparison.addLeaf(leaf);
            if (i == numLeaves - 1) {
                status = subject.status();
            } else if (i == numLeaves) {
                lastLeaf = leaf;
            }
        }

        final var actual = subject.rootHash().join();
        final var expected = comparison.rootHash().join();
        assertEquals(expected, actual);
        if (lastLeaf != null) {
            requireNonNull(status);
            final var recalculated = rootHashFrom(status, lastLeaf);
            assertEquals(expected, recalculated);
        }
    }

    @Test
    void testAddLeafAfterRootHashRequested() {
        subject.addLeaf(Bytes.wrap(new byte[48]));
        subject.rootHash();
        assertThrows(IllegalStateException.class, () -> subject.addLeaf(Bytes.EMPTY));
    }
}
