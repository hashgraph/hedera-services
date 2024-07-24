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

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.BlockSignature;
import com.hedera.hapi.node.base.BlockHashAlgorithm;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.BlockStreamInfo;
import com.swirlds.platform.system.Round;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BlockStreamManagerImpl implements BlockStreamManager {
    private static final Logger logger = LogManager.getLogger(BlockStreamManagerImpl.class);
    // All this state is scoped to producing the block for a single round
    private long blockNo;
    private ByteString previousBlockHash;
    private CompletableFuture<Bytes> nMinus3OutputHash;
    private CompletableFuture<Bytes> nMinus2OutputHash;
    private CompletableFuture<Bytes> nMinus1OutputHash;
    private CompletableFuture<Bytes> outputHash;
    private StreamingTreeHasher inputTreeHasher;
    private StreamingTreeHasher outputTreeHasher;
    private List<BlockItem> pendingItems = new ArrayList<>();
    private Round round;
    private boolean closed = false;
    // TODO - when the block signature is communicated to the node via some mechanism, set it here
    private BlockSignature blockSignature;

    /**
     * A future that completes after all items not in the pending list have been fully serialized
     * to bytes, with their hashes scheduled for incorporation in the input/output trees and running
     * hashes if applicable; <b>and</b> written to the block item writer.
     */
    private CompletableFuture<Void> writeFuture = CompletableFuture.completedFuture(null);

    private final BlockItemWriter writer;
    private final ExecutorService executor;
    private final int chunkSize;

    @Inject
    public BlockStreamManagerImpl(
            @NonNull final BlockItemWriter writer,
            @NonNull final ExecutorService executor,
            @NonNull final BlockStreamConfig config) {
        this.writer = requireNonNull(writer);
        this.executor = requireNonNull(executor);
        this.chunkSize = config.pendingChunkSize();
    }

    @Override
    public void startRound(@NonNull final Round round, @NonNull final HederaState state) {
        this.round = requireNonNull(round, "Round cannot be null");
        ensureNotClosed();

        //   - init block metadata and re-create process objects from state with round info
        final var lastBlockInfo = getLastInfo(state);
        final var runningHashes = getLastRunningHashes(state);

        //   - create new StreamingTreeHasher for input and output trees
        inputTreeHasher = new ConcurrentStreamingTreeHasher(executor);
        outputTreeHasher = new ConcurrentStreamingTreeHasher(executor);

        //   - sync running hashes, block number, previous block hash of output items from state
        //      - running hashes:
        this.outputHash = CompletableFuture.completedFuture(
                Bytes.wrap(runningHashes.runningHash().toByteArray()));
        this.nMinus1OutputHash = CompletableFuture.completedFuture(
                Bytes.wrap(runningHashes.nMinus1RunningHash().toByteArray()));
        this.nMinus2OutputHash = CompletableFuture.completedFuture(
                Bytes.wrap(runningHashes.nMinus2RunningHash().toByteArray()));
        this.nMinus3OutputHash = CompletableFuture.completedFuture(
                Bytes.wrap(runningHashes.nMinus3RunningHash().toByteArray()));

        //      - block number:
        final var lastBlockNum = lastBlockInfo.getLastBlockNumber();
        blockNo = lastBlockNum + 1;
        //      - previous block hash:
        previousBlockHash = lastBlockInfo.getLastBlockHash();

        //   - Write the block header
        final var headerBuilder = BlockHeader.newBuilder()
                .number(blockNo)
                .previousBlockHash(Bytes.wrap(previousBlockHash.toByteArray()))
                .hashAlgorithm(BlockHashAlgorithm.SHA2_384)
                // TODO - set address book version when it's available in the round
                .addressBookVersion(SemanticVersion.DEFAULT)
                // ??? What should go in headerBuilder.hapiProtoVersion(???)
                .hapiProtoVersion(SemanticVersion.DEFAULT);

        final var firstEvent = round.iterator().next();

        headerBuilder.softwareVersion(firstEvent.getSoftwareVersion());
        writeItem(BlockItem.newBuilder().header(headerBuilder).build());
    }

    @Override
    public void endRound(@NonNull final HederaState state) {
        ensureNotClosed();

        //   - Ensure work is scheduled for all pending items, if any
        if (!pendingItems.isEmpty()) {
            schedulePendingWork();
        }

        //   - Upon waiting for the write future to complete, also await
        //     (1) The root hashes of the input and output tree
        CompletableFuture<Bytes> inputTreeHashFut = writeFuture.thenCompose(ignore -> inputTreeHasher.rootHash());
        CompletableFuture<Bytes> outputTreeHashFut = writeFuture.thenCompose(ignore -> outputTreeHasher.rootHash());
        //      (2) All running hash items
        CompletableFuture<Bytes> nMinus3OutputHashFut = writeFuture.thenCompose(ignore -> nMinus3OutputHash);
        CompletableFuture<Bytes> nMinus2OutputHashFut = writeFuture.thenCompose(ignore -> nMinus2OutputHash);
        CompletableFuture<Bytes> nMinus1OutputHashFut = writeFuture.thenCompose(ignore -> nMinus1OutputHash);
        CompletableFuture<Bytes> outputHashFut = writeFuture.thenCompose(ignore -> this.outputHash);
        CompletableFuture<RunningHashes> runningHashesFut = CompletableFuture.allOf(
                        nMinus3OutputHashFut, nMinus2OutputHashFut, nMinus1OutputHashFut, outputHashFut)
                .thenApply(unused -> RunningHashes.newBuilder()
                        .nMinus3RunningHash(
                                Bytes.wrap(nMinus3OutputHashFut.join().toByteArray()))
                        .nMinus2RunningHash(
                                Bytes.wrap(nMinus2OutputHashFut.join().toByteArray()))
                        .nMinus1RunningHash(
                                Bytes.wrap(nMinus1OutputHashFut.join().toByteArray()))
                        .runningHash(Bytes.wrap(outputHashFut.join().toByteArray()))
                        .build());
        //      (3) The root hash of the state Merkle tree <-- for now, a placeholder hash.
        CompletableFuture<Bytes> stateRootHashFut = stateRootHash(state);

        //   - And when this is available, create the block proof
        CompletableFuture<BlockItem> proofFut = CompletableFuture.allOf(
                        inputTreeHashFut, outputTreeHashFut, runningHashesFut, stateRootHashFut)
                .thenCompose(ignore -> {
                    final var blockTree = new ConcurrentStreamingTreeHasher(executor);
                    blockTree.addLeaf(Bytes.wrap(previousBlockHash.toByteArray()));
                    blockTree.addLeaf(inputTreeHashFut.join());
                    blockTree.addLeaf(outputTreeHashFut.join());
                    blockTree.addLeaf(stateRootHashFut.join());

                    // Note: rootHash() returns a Future object
                    return blockTree.rootHash();
                })
                .thenApply(blockHash -> {
                    // Construct the block proof
                    final var blockProof = BlockProof.newBuilder()
                            .block(blockNo)
                            .blockRootHash(Bytes.wrap(blockHash.toByteArray()))
                            .build();
                    final var blockProofItem =
                            BlockItem.newBuilder().stateProof(blockProof).build();

                    // Write the block proof directly to the stream, since it's likely the last item to be written for
                    // this block
                    final var serialized = BlockItem.PROTOBUF.toBytes(blockProofItem);
                    writer.writeItem(serialized);

                    return blockProofItem;
                });

        //   -  and update state with the new block metadata
        writeFuture = writeFuture.thenCombine(proofFut, (ignore, blockProofItem) -> {
            final var blockProof = blockProofItem.stateProof();

            final var newBlockInfo = BlockStreamInfo.newBuilder()
                    .setLastBlockNumber(blockProof.block())
                    .setLastBlockHash(
                            ByteString.copyFrom(blockProof.blockRootHash().toByteArray()))
                    .setLastBlockTime(com.hederahashgraph.api.proto.java.Timestamp.newBuilder()
                            .setSeconds(blockTimestamp().seconds())
                            .setNanos(blockTimestamp().nanos()))
                    .setTrailingBlockHashes(ByteString.copyFrom(
                            toTrailingBlockHashes(runningHashesFut.join()).toByteArray()))
                    .setTrailingOutputHashes(ByteString.copyFrom(
                            toTrailingOutputHashes(runningHashesFut.join()).toByteArray()))
                    .build();
            final var newRunningHashes = runningHashesFut.join();

            // TODO - update new state singletons with the new block info and running hashes

            return null;
        });
        writeFuture.join();
    }

    @Override
    public void writeItem(@NonNull final BlockItem item) {
        requireNonNull(item);
        ensureNotClosed();

        pendingItems.add(item);

        //  - If we have filled a chunk of items, schedule their serialization, hashing, and writing
        if (pendingItems.size() >= chunkSize) {
            schedulePendingWork();
        }
    }

    @Override
    public void closeStream() {
        ensureNotClosed();

        writeFuture.thenCompose(ignore -> {
            writer.closeStream();

            //   - mark this manager as unwilling to accept any other calls
            closed = true;

            return CompletableFuture.completedFuture(null);
        });
    }

    @Nullable
    @Override
    public Bytes prngSeed() {
        ensureNotClosed();

        try {
            return nMinus3OutputHash.get();
        } catch (final InterruptedException | ExecutionException e) {
            logger.warn("Failed to get n-3 output hash!", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public long blockNo() {
        ensureNotClosed();

        return blockNo;
    }

    @NonNull
    @Override
    public Timestamp blockTimestamp() {
        ensureNotClosed();

        // ???  block timestamp from the round consensus timestamp
        final var roundTimestamp = round.getConsensusTimestamp();
        return Timestamp.newBuilder()
                .seconds(roundTimestamp.getEpochSecond())
                .nanos(roundTimestamp.getNano())
                .build();
    }

    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(long blockNo) {
        ensureNotClosed();

        // TODO - provide these hashes based on the trailing_block_hashes field of the BlockStreamInfo
        // that we found in state at the beginning of this round
        return null;
    }

    private CompletableFuture<Bytes> stateRootHash(@NonNull final HederaState state) {
        // Return a placeholder hash until we have a platform implementation
        return CompletableFuture.completedFuture(Bytes.wrap(new byte[48]));
    }

    private Bytes toTrailingBlockHashes(@NonNull final RunningHashes runningHashes) {
        // TODO - implement
        return Bytes.EMPTY;
    }

    private Bytes toTrailingOutputHashes(@NonNull final RunningHashes runningHashes) {
        // TODO - implement
        return Bytes.EMPTY;
    }

    private void schedulePendingWork() {
        final var itemsToWrite = pendingItems;

        final var serializedAndHashScheduled = CompletableFuture.supplyAsync(
                () -> {
                    final var serializedItems = new ArrayList<Bytes>();
                    itemsToWrite.forEach(blockItem -> {
                        // Serialize:
                        final var bytes = BlockItem.PROTOBUF.toBytes(blockItem);
                        serializedItems.add(bytes);

                        // Schedule for hashing:
                        if (blockItem.hasHeader() || blockItem.hasStateProof()) {
                            // No need to hash this block item, as it is not needed by the input or output trees
                        } else if (blockItem.hasStartEvent()
                                || blockItem.hasTransaction()
                                || blockItem.hasSystemTransaction()) {
                            inputTreeHasher.addLeaf(bytes);
                        } else if (blockItem.hasTransactionResult()
                                || blockItem.hasTransactionOutput()
                                || blockItem.hasStateChanges()) {
                            outputTreeHasher.addLeaf(bytes);
                        } else {
                            logger.error("Unrecognized block item type for block item {}", blockItem);
                            throw new IllegalStateException("Unrecognized block item type");
                        }
                    });

                    return serializedItems;
                },
                executor);

        // Write:
        writeFuture = writeFuture.thenCombine(serializedAndHashScheduled, (ignore, serialized) -> {
            serialized.forEach(writer::writeItem);
            return null;
        });

        // Finally, reset pending items
        pendingItems = new ArrayList<>();
    }

    private BlockStreamInfo getLastInfo(@NonNull final HederaState state) {
        // TODO - create new singleton in state for block stream info
        return BlockStreamInfo.getDefaultInstance();
    }

    private RunningHashes getLastRunningHashes(@NonNull final HederaState state) {
        // TODO - create new singleton in state for new running hash
        return RunningHashes.DEFAULT;
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("Cannot write to a closed block stream manager");
        }
    }
}
