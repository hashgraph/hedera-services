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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.Round;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BlockStreamManagerImpl implements BlockStreamManager {
    // All this state is scoped to producing the block for a single round
    private long blockNo;
    private Bytes previousBlockHash;
    private CompletableFuture<Bytes> nMinus3OutputHash;
    private CompletableFuture<Bytes> nMinus2OutputHash;
    private CompletableFuture<Bytes> nMinus1OutputHash;
    private CompletableFuture<Bytes> outputHash;
    private StreamingTreeHasher inputTreeHasher;
    private StreamingTreeHasher outputTreeHasher;
    private List<BlockItem> pendingItems;
    /**
     * A future that completes after all items not in the pending list have been fully serialized
     * to bytes, with their hashes scheduled for incorporation in the input/output trees and running
     * hashes if applicable; <b>and</b> written to the block item writer.
     */
    private CompletableFuture<Void> writeFuture = CompletableFuture.completedFuture(null);

    private final BlockItemWriter writer;
    private final ExecutorService executor;

    @Inject
    public BlockStreamManagerImpl(@NonNull final BlockItemWriter writer, @NonNull final ExecutorService executor) {
        this.writer = requireNonNull(writer);
        this.executor = requireNonNull(executor);
    }

    @Override
    public void startRound(@NonNull final Round round, @NonNull final HederaState state) {
        // TODO
        //   - init block metadata and re-create process objects from state with round info
        //   - create new StreamingTreeHasher for input and output trees
        //   - sync running hashes, block number, previous block hash of output items from state
        //   - Write the block header
    }

    @Override
    public void endRound(@NonNull final HederaState state) {
        // TODO
        //   - Ensure work is scheduled for all pending items, if any
        //   - Upon waiting for the write future to complete, also await
        //      (1) The root hashes of the input and output tree
        //      (2) All running hash items
        //      (3) The root hash of the state Merkle tree <-- for now, a placeholder hash.
        //   - And when this is available, create the block proof
        //   - Compute the hash of this block and update state with the new block metadata
    }

    @Override
    public void writeItem(@NonNull final BlockItem item) {
        // TODO
        //  - Add this to the pending list
        //  - If we have filled a chunk of items, schedule their serialization, hashing, and writing
    }

    @Override
    public void closeStream() {
        // TODO
        //   - Same as endRound() but without the state changes;
        //   - Also mark this manager as unwilling to accept any other calls
    }

    @Nullable
    @Override
    public Bytes prngSeed() {
        // TODO
        //   - Await and return the n-3 output hash
        throw new AssertionError("Not implemented");
    }

    @Override
    public long blockNo() {
        return blockNo;
    }

    @NonNull
    @Override
    public Timestamp blockTimestamp() {
        // TODO - get block timestamp from the round consensus timestamp
        return null;
    }

    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(long blockNo) {
        // TODO - provide these hashes based on the trailing_block_hashes field of the BlockStreamInfo
        // that we found in state at the beginning of this round
        return null;
    }
}
