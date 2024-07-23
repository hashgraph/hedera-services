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

import static com.hedera.node.app.blocks.schemas.V0XX0BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.HASH_SIZE;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.StreamingTreeHasher;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BlockStreamManagerImpl implements BlockStreamManager {
    // All this state is scoped to producing the block for a single round
    private long blockNumber;
    private Bytes previousBlockHash;
    private Instant blockTimestamp;
    private StreamingTreeHasher inputTreeHasher;
    private StreamingTreeHasher outputTreeHasher;
    private List<BlockItem> pendingItems;
    /**
     * A future that completes after all items not in the pending list have been fully serialized
     * to bytes, with their hashes scheduled for incorporation in the input/output trees and running
     * hashes if applicable; <b>and</b> written to the block item writer.
     */
    private CompletableFuture<Void> writeFuture = completedFuture(null);

    private final InitTrigger initTrigger;
    private final BlockItemWriter writer;
    private final ExecutorService executor;
    private final RunningHashManager runningHashManager;

    @Inject
    public BlockStreamManagerImpl(
            @NonNull final InitTrigger initTrigger,
            @NonNull final BlockItemWriter writer,
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider) {
        this.writer = requireNonNull(writer);
        this.executor = requireNonNull(executor);
        this.initTrigger = requireNonNull(initTrigger);
        this.runningHashManager = new RunningHashManager();
    }

    @Override
    public void startRound(@NonNull final Round round, @NonNull final HederaState state) {
        blockTimestamp = round.getConsensusTimestamp();
        var blockStreamInfo = state.getReadableStates(BlockStreamService.NAME)
                .<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY)
                .get();
        if (blockStreamInfo == null && initTrigger != GENESIS) {
            // TODO - initialize from record stream state
        }
        blockNumber = blockStreamInfo == null ? 0 : blockStreamInfo.lastBlockNumber() + 1;
        previousBlockHash = blockStreamInfo == null ? Bytes.EMPTY : blockStreamInfo.lastBlockHash();
        runningHashManager.startRound(blockStreamInfo);
        inputTreeHasher = new ConcurrentStreamingTreeHasher(executor);
        outputTreeHasher = new ConcurrentStreamingTreeHasher(executor);
        pendingItems.add(BlockItem.newBuilder()
                .header(BlockHeader.newBuilder()
                        .previousBlockHash(previousBlockHash)
                        .number(blockNumber))
                .build());
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

    @Override
    public @Nullable Bytes prngSeed() {
        final var seed = runningHashManager.nMinus3HashFuture.join();
        return seed == null ? null : Bytes.wrap(seed);
    }

    @Override
    public long blockNo() {
        return blockNumber;
    }

    @NonNull
    @Override
    public Timestamp blockTimestamp() {
        return new Timestamp(blockTimestamp.getEpochSecond(), blockTimestamp.getNano());
    }

    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(long blockNo) {
        // TODO - provide these hashes based on the trailing_block_hashes field of the BlockStreamInfo
        // that we found in state at the beginning of this round
        return null;
    }

    private class RunningHashManager {
        CompletableFuture<byte[]> nMinus3HashFuture;
        CompletableFuture<byte[]> nMinus2HashFuture;
        CompletableFuture<byte[]> nMinus1HashFuture;
        CompletableFuture<byte[]> hashFuture;

        /**
         * Starts managing running hashes for a new round, with the given trailing block hashes.
         *
         * @param blockStreamInfo the trailing block hashes at the start of the round
         */
        void startRound(@Nullable final BlockStreamInfo blockStreamInfo) {
            final var hashes = blockStreamInfo == null ? Bytes.EMPTY : blockStreamInfo.trailingBlockHashes();
            final var n = (int) (hashes.length() / HASH_SIZE);
            nMinus3HashFuture = completedFuture(n < 4 ? null : hashes.toByteArray(0, HASH_SIZE));
            nMinus2HashFuture = completedFuture(n < 3 ? null : hashes.toByteArray(HASH_SIZE, HASH_SIZE));
            nMinus1HashFuture = completedFuture(n < 2 ? null : hashes.toByteArray(2 * HASH_SIZE, HASH_SIZE));
            hashFuture = completedFuture(n < 1 ? null : hashes.toByteArray(3 * HASH_SIZE, HASH_SIZE));
        }

        /**
         * Updates the running hashes for the given serialized output block item.
         *
         * @param bytes the serialized output block item
         */
        void nextOutput(@NonNull final Bytes bytes) {
            requireNonNull(bytes);
            nMinus3HashFuture = nMinus2HashFuture;
            nMinus2HashFuture = nMinus1HashFuture;
            nMinus1HashFuture = hashFuture;
            hashFuture = hashFuture.thenCombineAsync(
                    supplyAsync(() -> noThrowSha384HashOf(bytes.toByteArray()), executor),
                    HashUtils::combine,
                    executor);
        }
    }
}
