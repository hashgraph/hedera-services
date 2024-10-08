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

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.blocks.StreamingTreeHasher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

public class NaiveStreamingTreeHasher implements StreamingTreeHasher {
    private static final byte[] EMPTY_HASH = noThrowSha384HashOf(new byte[0]);

    private final List<ByteBuffer> leaves = new ArrayList<>();
    private boolean rootHashRequested = false;

    public static Bytes hashNaively(@NonNull final List<ByteBuffer> leaves) {
        final var hasher = new NaiveStreamingTreeHasher();
        for (final var item : leaves) {
            hasher.addLeaf(item);
        }
        return hasher.rootHash().join();
    }

    @Override
    public void addLeaf(@NonNull final ByteBuffer leaf) {
        if (rootHashRequested) {
            throw new IllegalStateException("Root hash already requested");
        }
        leaves.add(leaf);
    }

    @Override
    public CompletableFuture<Bytes> rootHash() {
        rootHashRequested = true;
        if (leaves.isEmpty()) {
            return CompletableFuture.completedFuture(Bytes.wrap(EMPTY_HASH));
        }
        Queue<byte[]> leafHashes = new LinkedList<>();
        for (final var leaf : leaves) {
            leafHashes.add(noThrowSha384HashOf(leaf.array()));
        }
        final int n = leafHashes.size();
        if ((n & (n - 1)) != 0) {
            final var paddedN = Integer.highestOneBit(n) << 1;
            while (leafHashes.size() < paddedN) {
                leafHashes.add(EMPTY_HASH);
            }
        }
        while (leafHashes.size() > 1) {
            final Queue<byte[]> newLeafHashes = new LinkedList<>();
            while (!leafHashes.isEmpty()) {
                final byte[] left = leafHashes.poll();
                final byte[] right = leafHashes.poll();
                final byte[] combined = new byte[left.length + requireNonNull(right).length];
                System.arraycopy(left, 0, combined, 0, left.length);
                System.arraycopy(right, 0, combined, left.length, right.length);
                newLeafHashes.add(noThrowSha384HashOf(combined));
            }
            leafHashes = newLeafHashes;
        }
        return CompletableFuture.completedFuture(Bytes.wrap(requireNonNull(leafHashes.poll())));
    }
}
