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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Defines a streaming hash computation for a perfect binary Merkle tree of {@link Bytes} leaves; where the leaves
 * given before calling {@link #rootHash()} are right-padded with empty leaves as needed to ensure the final tree is
 * a perfect binary tree.
 */
public interface StreamingTreeHasher {
    /**
     * Describes the status of the tree hash computation.
     * @param numLeaves the number of leaves added to the tree
     * @param rightmostHashes the rightmost hashes of the tree at each depth
     */
    record Status(int numLeaves, @NonNull List<Bytes> rightmostHashes) {
        public static Status EMPTY = new Status(0, List.of());

        public boolean isEmpty() {
            return numLeaves == 0;
        }
    }

    /**
     * Adds a leaf to the implicit tree of items.
     * @param leaf the leaf to add
     * @throws IllegalStateException if the root hash has already been requested
     */
    void addLeaf(@NonNull Bytes leaf);

    /**
     * Returns a future that completes with the root hash of the tree of items. Once called, this hasher will not accept
     * any more leaf items.
     * @return a future that completes with the root hash of the tree of items
     */
    CompletableFuture<Bytes> rootHash();

    /**
     * If supported, blocks until this hasher can give a deterministic summary of the status of the
     * tree hash computation.
     * @return the status of the tree hash computation
     * @throws UnsupportedOperationException if the implementation does not support status reporting
     */
    default Status status() {
        throw new UnsupportedOperationException();
    }
}
