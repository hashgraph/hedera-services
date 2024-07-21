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

package com.hedera.node.app.blocks;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ConcurrentItemTreeHasher implements ItemTreeHasher {
    private static final byte[] EMPTY_HASH = noThrowSha384HashOf(new byte[0]);

    private final ExecutorService executorService;
    private final List<BlockItem> items = new ArrayList<>();
    private boolean rootHashRequested = false;

    public ConcurrentItemTreeHasher(@NonNull final ExecutorService executorService) {
        this.executorService = requireNonNull(executorService);
    }

    @Override
    public void addLeaf(@NonNull final BlockItem item) {
        requireNonNull(item);
        if (rootHashRequested) {
            throw new IllegalStateException("Root hash already requested");
        }
        items.add(item);
    }

    @Override
    public CompletableFuture<Bytes> rootHash() {
        rootHashRequested = true;
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(Bytes.wrap(EMPTY_HASH));
        }
        final var n = containingPowerOfTwo(items.size());
        final var combiner = new HashCombiner(n, 0);
        for (final var item : items) {
            final var serializedItem = BlockItem.PROTOBUF.toBytes(item).toByteArray();
            final var leafHash = noThrowSha384HashOf(serializedItem);
            combiner.combine(leafHash);
        }
        return combiner.finalCombination();
    }

    private class HashCombiner {
        private static final byte[] EMPTY_HASH = noThrowSha384HashOf(new byte[0]);
        private static final int MAX_DEPTH = 24;
        private static final int MAX_CHUNK_SIZE = 128;
        private static final byte[][] EMPTY_HASHES = new byte[MAX_DEPTH][];

        static {
            EMPTY_HASHES[0] = EMPTY_HASH;
            for (int i = 1; i < MAX_DEPTH; i++) {
                EMPTY_HASHES[i] = combine(EMPTY_HASHES[i - 1], EMPTY_HASHES[i - 1]);
            }
        }

        private static final int MAX_LEAVES = 1 << MAX_DEPTH;

        private final int n;
        private final int depth;
        private final int chunkSize;

        private int numCombined;
        private HashCombiner delegate;
        private List<byte[]> pendingHashes = new ArrayList<>();
        private CompletableFuture<Void> combination = CompletableFuture.completedFuture(null);

        private HashCombiner(final int n, final int depth) {
            if ((n & (n - 1)) != 0) {
                throw new IllegalArgumentException("Can only combine 2^n hashes");
            }
            if (n > MAX_LEAVES) {
                throw new IllegalArgumentException("Cannot combine more than " + MAX_LEAVES + " hashes");
            }
            this.n = n;
            this.depth = depth;
            this.chunkSize = Math.min(n, MAX_CHUNK_SIZE);
        }

        public void combine(@NonNull final byte[] hash) {
            numCombined++;
            pendingHashes.add(hash);
            if (n > 1 && pendingHashes.size() == chunkSize) {
                schedulePendingWork();
            }
        }

        public CompletableFuture<Bytes> finalCombination() {
            if (n == 1) {
                return CompletableFuture.completedFuture(Bytes.wrap(pendingHashes.getFirst()));
            } else {
                if (!pendingHashes.isEmpty()) {
                    schedulePendingWork();
                }
                return combination.thenCompose(ignore -> delegate.finalCombination());
            }
        }

        private void schedulePendingWork() {
            if (delegate == null) {
                delegate = new HashCombiner(n / 2, depth + 1);
            }
            final var scheduledWork = pendingHashes;
            final var pendingCombination = CompletableFuture.supplyAsync(
                    () -> {
                        final List<byte[]> result = new ArrayList<>();
                        for (int i = 0, m = scheduledWork.size(); i < m; i += 2) {
                            final var left = scheduledWork.get(i);
                            final var right = i + 1 < m ? scheduledWork.get(i + 1) : EMPTY_HASHES[depth];
                            result.add(combine(left, right));
                        }
                        return result;
                    },
                    executorService);
            combination = combination.thenCombine(pendingCombination, (ignore, combined) -> {
                combined.forEach(delegate::combine);
                return null;
            });
            pendingHashes = new ArrayList<>();
        }

        private static byte[] combine(final byte[] leftHash, final byte[] rightHash) {
            try {
                final var digest = MessageDigest.getInstance("SHA-384");
                digest.update(leftHash);
                digest.update(rightHash);
                return digest.digest();
            } catch (final NoSuchAlgorithmException fatal) {
                throw new IllegalStateException(fatal);
            }
        }
    }

    private static int containingPowerOfTwo(final int n) {
        if ((n & (n - 1)) == 0) {
            return n;
        }
        return Integer.highestOneBit(n) << 1;
    }
}
