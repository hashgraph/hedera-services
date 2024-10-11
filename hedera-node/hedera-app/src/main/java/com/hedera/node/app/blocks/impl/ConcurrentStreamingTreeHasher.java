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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * A {@link StreamingTreeHasher} that computes the root hash of a perfect binary Merkle tree of {@link Bytes} leaves
 * using a concurrent algorithm that hashes leaves in parallel and combines the resulting hashes in parallel.
 * <p>
 * <b>Important:</b> This class is not thread-safe, and client code must not make concurrent calls to
 * {@link #addLeaf(Bytes)} or {@link #rootHash()}.
 */
public class ConcurrentStreamingTreeHasher implements StreamingTreeHasher {
    /**
     * The number of leaves to hash in parallel before combining the resulting hashes.
     */
    private static final int HASHING_CHUNK_SIZE = 16;

    /**
     * The base {@link HashCombiner} that combines the hashes of the leaves of the tree, at depth zero.
     */
    private final HashCombiner combiner = new HashCombiner(0);
    /**
     * The {@link ExecutorService} used to parallelize the hashing and combining of the leaves of the tree.
     */
    private final ExecutorService executorService;

    /**
     * The number of leaves added to the tree.
     */
    private int numLeaves;
    /**
     * Set once before the root hash is requested, to the depth of the tree implied by the number of leaves.
     */
    private int maxDepth;
    /**
     * Whether the tree has been finalized by requesting the root hash.
     */
    private boolean rootHashRequested = false;
    /**
     * Leaves added but not yet scheduled to be hashed.
     */
    private List<Bytes> pendingLeaves = new ArrayList<>();
    /**
     * A future that completes after all leaves not in the pending list have been hashed and combined.
     */
    private CompletableFuture<Void> hashed = CompletableFuture.completedFuture(null);

    public ConcurrentStreamingTreeHasher(@NonNull final ExecutorService executorService) {
        this.executorService = requireNonNull(executorService);
    }

    @Override
    public void addLeaf(@NonNull final Bytes leaf) {
        requireNonNull(leaf);
        if (rootHashRequested) {
            throw new IllegalStateException("Cannot add leaves after requesting the root hash");
        }
        numLeaves++;
        pendingLeaves.add(leaf);
        if (pendingLeaves.size() == HASHING_CHUNK_SIZE) {
            schedulePendingWork();
        }
    }

    @Override
    public CompletableFuture<Bytes> rootHash() {
        rootHashRequested = true;
        if (!pendingLeaves.isEmpty()) {
            schedulePendingWork();
        }
        maxDepth = maxDepthFor(numLeaves);
        return hashed.thenCompose(ignore -> combiner.finalCombination());
    }

    @Override
    public Status status() {
        if (numLeaves == 0) {
            return Status.EMPTY;
        } else {
            schedulePendingWork();
            final var n = numLeaves;
            return hashed.thenApply(ignore -> {
                        final var rightmostHashes = new ArrayList<Bytes>();
                        combiner.flushAvailable(rightmostHashes, maxDepthFor(n + 1));
                        return new Status(n, rightmostHashes);
                    })
                    .join();
        }
    }

    /**
     * Computes the root hash of a perfect binary Merkle tree of {@link Bytes} leaves (padded on the right with
     * empty leaves to reach a power of two), given the penultimate status of the tree and the last leaf added to
     * the tree.
     * @param penultimateStatus the penultimate status of the tree
     * @param lastLeaf the last leaf added to the tree
     * @return the root hash of the tree
     */
    public static Bytes rootHashFrom(@NonNull final Status penultimateStatus, @NonNull final Bytes lastLeaf) {
        requireNonNull(lastLeaf);
        var hash = noThrowSha384HashOf(lastLeaf.toByteArray());
        final var maxDepth = maxDepthFor(penultimateStatus.numLeaves() + 1);
        for (int i = 0; i < maxDepth; i++) {
            final var rightmostHash = penultimateStatus.rightmostHashes().get(i);
            if (rightmostHash.length() == 0) {
                hash = BlockImplUtils.combine(hash, HashCombiner.EMPTY_HASHES[i]);
            } else {
                hash = BlockImplUtils.combine(rightmostHash.toByteArray(), hash);
            }
        }
        return Bytes.wrap(hash);
    }

    private void schedulePendingWork() {
        final var scheduledWork = pendingLeaves;
        final var pendingHashes = CompletableFuture.supplyAsync(
                () -> {
                    final List<byte[]> result = new ArrayList<>();
                    for (final var leaf : scheduledWork) {
                        result.add(noThrowSha384HashOf(leaf.toByteArray()));
                    }
                    return result;
                },
                executorService);
        hashed = hashed.thenCombine(pendingHashes, (ignore, hashes) -> {
            hashes.forEach(combiner::combine);
            return null;
        });
        pendingLeaves = new ArrayList<>();
    }

    private class HashCombiner {
        private static final int MAX_DEPTH = 24;
        /**
         * <b>IMPORTANT</b> - This must be an even number so we can safely assume that any odd number
         * of scheduled hashes to combine can be padded with appropriately nested combination of hashes
         * whose descendants are all empty leaves.
         */
        private static final int COMBINATION_CHUNK_SIZE = 32;

        private static final byte[][] EMPTY_HASHES = new byte[MAX_DEPTH][];

        static {
            EMPTY_HASHES[0] = noThrowSha384HashOf(new byte[0]);
            for (int i = 1; i < MAX_DEPTH; i++) {
                EMPTY_HASHES[i] = BlockImplUtils.combine(EMPTY_HASHES[i - 1], EMPTY_HASHES[i - 1]);
            }
        }

        private final int depth;

        private HashCombiner delegate;
        private List<byte[]> pendingHashes = new ArrayList<>();
        private CompletableFuture<Void> combination = CompletableFuture.completedFuture(null);

        private HashCombiner(final int depth) {
            if (depth >= MAX_DEPTH) {
                throw new IllegalArgumentException("Cannot combine hashes at depth " + depth);
            }
            this.depth = depth;
        }

        public void combine(@NonNull final byte[] hash) {
            pendingHashes.add(hash);
            if (pendingHashes.size() == COMBINATION_CHUNK_SIZE) {
                schedulePendingWork();
            }
        }

        public CompletableFuture<Bytes> finalCombination() {
            if (depth == maxDepth) {
                final var rootHash = pendingHashes.isEmpty() ? EMPTY_HASHES[0] : pendingHashes.getFirst();
                return CompletableFuture.completedFuture(Bytes.wrap(rootHash));
            } else {
                if (!pendingHashes.isEmpty()) {
                    schedulePendingWork();
                }
                return combination.thenCompose(ignore -> delegate.finalCombination());
            }
        }

        public void flushAvailable(@NonNull final List<Bytes> rightmostHashes, final int stopDepth) {
            if (depth < stopDepth) {
                final var newPendingHash = pendingHashes.size() % 2 == 0 ? null : pendingHashes.removeLast();
                schedulePendingWork();
                combination.join();
                if (newPendingHash != null) {
                    pendingHashes.add(newPendingHash);
                    rightmostHashes.add(Bytes.wrap(newPendingHash));
                } else {
                    rightmostHashes.add(Bytes.EMPTY);
                }
                delegate.flushAvailable(rightmostHashes, stopDepth);
            }
        }

        private void schedulePendingWork() {
            if (delegate == null) {
                delegate = new HashCombiner(depth + 1);
            }
            final var scheduledWork = pendingHashes;
            final var pendingCombination = CompletableFuture.supplyAsync(
                    () -> {
                        final List<byte[]> result = new ArrayList<>();
                        for (int i = 0, m = scheduledWork.size(); i < m; i += 2) {
                            final var left = scheduledWork.get(i);
                            final var right = i + 1 < m ? scheduledWork.get(i + 1) : EMPTY_HASHES[depth];
                            result.add(BlockImplUtils.combine(left, right));
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
    }

    private static int maxDepthFor(final int numLeaves) {
        final var numPerfectLeaves = containingPowerOfTwo(numLeaves);
        return numPerfectLeaves == 0 ? 0 : Integer.numberOfTrailingZeros(numPerfectLeaves);
    }

    private static int containingPowerOfTwo(final int n) {
        if ((n & (n - 1)) == 0) {
            return n;
        }
        return Integer.highestOneBit(n) << 1;
    }
}
