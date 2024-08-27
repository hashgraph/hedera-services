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

import static com.hedera.hapi.node.base.BlockHashAlgorithm.SHA2_384;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.appendHash;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.combine;
import static com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.HASH_SIZE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.StreamingTreeHasher;
import com.hedera.node.app.records.impl.BlockRecordInfoUtils;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.Round;
import com.swirlds.state.State;
import com.swirlds.state.spi.CommittableWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BlockStreamManagerImpl implements BlockStreamManager {
    private static final Logger log = LogManager.getLogger(BlockStreamManagerImpl.class);

    private static final int CHUNK_SIZE = 8;
    private static final CompletableFuture<Bytes> MOCK_START_STATE_ROOT_HASH_FUTURE =
            completedFuture(Bytes.wrap(new byte[48]));

    private final SemanticVersion hapiVersion;
    private final SemanticVersion nodeVersion;
    private final ExecutorService executor;
    private final BlockHashManager blockHashManager;
    private final RunningHashManager runningHashManager;
    private final Supplier<BlockItemWriter> writerSupplier;
    private final BoundaryStateChangeListener boundaryStateChangeListener;
    private final int roundsPerBlock;

    // All this state is scoped to producing the block for the last-started round
    private long blockNumber;
    private Bytes lastBlockHash = Bytes.wrap(new byte[48]);
    private Instant blockTimestamp;
    private BlockItemWriter writer;
    private List<BlockItem> pendingItems;

    private StreamingTreeHasher inputTreeHasher;
    private StreamingTreeHasher outputTreeHasher;
    /**
     * A future that completes after all items not in the pendingItems list have been serialized
     * to bytes, with their hashes scheduled for incorporation in the input/output trees and running
     * hashes if applicable; <b>and</b> written to the block item writer.
     */
    private CompletableFuture<Void> writeFuture = completedFuture(null);

    private final Queue<PendingBlock> pendingBlocks = new ConcurrentLinkedQueue<>();

    @Inject
    public BlockStreamManagerImpl(
            @NonNull final Supplier<BlockItemWriter> writerSupplier,
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final BoundaryStateChangeListener boundaryStateChangeListener) {
        this.writerSupplier = requireNonNull(writerSupplier);
        this.executor = requireNonNull(executor);
        this.boundaryStateChangeListener = requireNonNull(boundaryStateChangeListener);
        final var config = requireNonNull(configProvider).getConfiguration();
        this.hapiVersion = hapiVersionFrom(config);
        this.nodeVersion = nodeVersionFrom(config);
        this.roundsPerBlock = config.getConfigData(BlockStreamConfig.class).roundsPerBlock();
        this.blockHashManager = new BlockHashManager(config);
        this.runningHashManager = new RunningHashManager();
    }

    @Override
    public void writeFreezeBlock(@NonNull final State state, @NonNull final Instant consensusNow) {
        requireNonNull(state);
        requireNonNull(consensusNow);
        // While writing a freeze block, we need to close the existing block, if it is open.
        if (writer != null) {
            endBlock(state);
        }
        // This state already has the platform state changes that must be included in a block
        startBlock(consensusNow, state);
        endBlock(state);
    }

    @Override
    public void finishBlockProof(final long blockNumber, final Bytes signature) {
        // FUTURE: TSS service will release this proof
        if (pendingBlocks.isEmpty() || pendingBlocks.peek().blockNumber != blockNumber) {
            log.error("Block {} is not having the same block number", blockNumber);
            return;
        }

        final var block = pendingBlocks.poll();
        final var proof = block.proofBuilder().blockSignature(signature).build();
        block.writer()
                .writeItem(BlockItem.PROTOBUF.toBytes(
                        BlockItem.newBuilder().blockProof(proof).build()));
        block.writer().closeBlock();
    }

    @Override
    public void startRound(@NonNull final Round round, @NonNull final State state) {
        if (writer == null) {
            writer = writerSupplier.get();
            startBlock(round.getConsensusTimestamp(), state);
        }
    }

    @Override
    public void endRound(@NonNull final State state, final long roundNum) {
        //        log.info(
        //                "Capturing end of round state changes for block {} with {}",
        //                blockNumber,
        //                roundStateChangeListener.stateChanges());
        if (!shouldCloseBlock(roundNum, roundsPerBlock)) {
            return;
        }
        endBlock(state);
    }

    private Bytes computeBlockHash(
            final Bytes prevBlockHash,
            final Bytes inputRootHash,
            final Bytes outputRootHash,
            final Bytes stateRootHash) {
        final var leftParent = combine(prevBlockHash.toByteArray(), inputRootHash.toByteArray());
        final var rightParent = combine(outputRootHash.toByteArray(), stateRootHash.toByteArray());
        return Bytes.wrap(combine(leftParent, rightParent));
    }

    @Override
    public void writeItem(@NonNull final BlockItem item) {
        pendingItems.add(item);
        if (pendingItems.size() == CHUNK_SIZE) {
            schedulePendingWork();
        }
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

    @Override
    public @NonNull Timestamp blockTimestamp() {
        return new Timestamp(blockTimestamp.getEpochSecond(), blockTimestamp.getNano());
    }

    @Override
    public @Nullable Bytes blockHashByBlockNumber(final long blockNo) {
        return blockHashManager.hashOfBlock(blockNo);
    }

    private void schedulePendingWork() {
        final var scheduledWork = new ScheduledWork(pendingItems);
        final var pendingSerialization = CompletableFuture.supplyAsync(scheduledWork::serializeItems, executor);
        writeFuture = writeFuture.thenCombine(pendingSerialization, scheduledWork::combineSerializedItems);
        pendingItems = new ArrayList<>();
    }

    private void startBlock(@NonNull final Instant consensusNow, @NonNull final State state) {
        blockTimestamp = consensusNow;
        // If there are no other changes to state, we can just use the round's consensus timestamp as the
        // last-assigned consensus time for externalizing the PUT to the block stream info singleton
        boundaryStateChangeListener.setLastUsedConsensusTime(blockTimestamp);
        final var blockStreamInfo = requireNonNull(state.getReadableStates(BlockStreamService.NAME)
                .<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY)
                .get());
        blockHashManager.startBlock(blockStreamInfo, lastBlockHash);
        runningHashManager.startRound(blockStreamInfo);
        inputTreeHasher = new ConcurrentStreamingTreeHasher(executor);
        outputTreeHasher = new ConcurrentStreamingTreeHasher(executor);
        blockNumber = blockStreamInfo.blockNumber() + 1;
        pendingItems = new ArrayList<>();

        pendingItems.add(BlockItem.newBuilder()
                .blockHeader(BlockHeader.newBuilder()
                        .number(blockNumber)
                        .previousBlockHash(lastBlockHash)
                        .hashAlgorithm(SHA2_384)
                        .softwareVersion(nodeVersion)
                        .hapiProtoVersion(hapiVersion))
                .build());

        writer.openBlock(blockNumber);
    }

    private void endBlock(@NonNull final State state) {
        requireNonNull(state);
        final var writableState = state.getWritableStates(BlockStreamService.NAME);
        final var blockStreamInfoState = writableState.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY);
        final var stateRootHash = MOCK_START_STATE_ROOT_HASH_FUTURE.join();
        // Ensure all runningHashManager futures are complete
        writeFuture.join();

        blockStreamInfoState.put(new BlockStreamInfo(
                blockNumber,
                blockTimestamp(),
                runningHashManager.latestHashes(),
                blockHashManager.blockHashes,
                stateRootHash));
        ((CommittableWritableStates) writableState).commit();

        pendingItems.add(boundaryStateChangeListener.flushChanges());
        schedulePendingWork();
        writeFuture.join();

        final var inputRootHash = inputTreeHasher.rootHash().join();
        final var outputRootHash = outputTreeHasher.rootHash().join();
        final var blockHash = computeBlockHash(lastBlockHash, inputRootHash, outputRootHash, stateRootHash);
        // FUTURE: Platform API to sign the block hash and gossip the signature

        final var blockProofBuilder = BlockProof.newBuilder()
                .block(blockNumber)
                .previousBlockRootHash(lastBlockHash)
                .startOfBlockStateRootHash(stateRootHash);
        pendingBlocks.add(new PendingBlock(writer, blockProofBuilder, blockNumber));
        lastBlockHash = blockHash;
        final long blockNumberToComplete = this.blockNumber;
        writer = null;
        CompletableFuture.runAsync(
                () -> finishBlockProof(blockNumberToComplete, Bytes.wrap(new byte[48])),
                executor);
    }

    /**
     * Encapsulates the work to be done for a batch of pending {@link BlockItem}s. This work includes,
     * <ol>
     *     <li>Serializing the items to bytes using the {@link BlockItem#PROTOBUF} codec.</li>
     *     <li>Given the serialized items,
     *          <ul>
     *              <Li>For each input item, scheduling its hash to be incorporated in the input item Merkle tree.</Li>
     *              <li>For each output item, scheduling its hash to be incorporated in the input item Merkle tree.</li>
     *              <li>For each {@link TransactionResult}, scheduling its hash to be incorporated in the running hash.</li>
     *              <li>For each item, writing its serialized bytes to the {@link BlockItemWriter}.</li>
     *          </ul>
     *     </li>
     * </ol>
     */
    private class ScheduledWork {
        private final List<BlockItem> scheduledWork;

        public ScheduledWork(@NonNull final List<BlockItem> scheduledWork) {
            this.scheduledWork = requireNonNull(scheduledWork);
        }

        /**
         * Serializes the scheduled work items to bytes using the {@link BlockItem#PROTOBUF} codec.
         *
         * @return the serialized items
         */
        public List<Bytes> serializeItems() {
            final List<Bytes> serializedItems = new ArrayList<>(scheduledWork.size());
            for (final var item : scheduledWork) {
                serializedItems.add(BlockItem.PROTOBUF.toBytes(item));
            }
            return serializedItems;
        }

        /**
         * Given the serialized items, schedules the hashes of the input/output items and running hash
         * for the {@link TransactionResult}s to be incorporated in the input/output trees and running hash
         * respectively; and writes the serialized bytes to the {@link BlockItemWriter}.
         *
         * @param ignore ignored, needed for type compatibility with {@link CompletableFuture#thenCombine}
         * @param serializedItems the serialized items to be processed
         * @return {@code null}
         */
        public Void combineSerializedItems(@Nullable Void ignore, @NonNull final List<Bytes> serializedItems) {
            for (int i = 0, n = scheduledWork.size(); i < n; i++) {
                final var item = scheduledWork.get(i);
                final var serializedItem = serializedItems.get(i);
                final var kind = item.item().kind();
                switch (kind) {
                    case EVENT_HEADER, EVENT_TRANSACTION -> inputTreeHasher.addLeaf(serializedItem);
                    case TRANSACTION_RESULT, TRANSACTION_OUTPUT, STATE_CHANGES -> outputTreeHasher.addLeaf(
                            serializedItem);
                    default -> {
                        // Other items are not part of the input/output trees
                    }
                }
                if (kind == BlockItem.ItemOneOfType.TRANSACTION_RESULT) {
                    runningHashManager.nextResult(serializedItem);
                }
                writer.writeItem(serializedItem);
            }
            return null;
        }
    }

    private SemanticVersion nodeVersionFrom(@NonNull final Configuration config) {
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        final var versionConfig = config.getConfigData(VersionConfig.class);
        return (hederaConfig.configVersion() == 0)
                ? versionConfig.servicesVersion()
                : versionConfig
                        .servicesVersion()
                        .copyBuilder()
                        .build("" + hederaConfig.configVersion())
                        .build();
    }

    private SemanticVersion hapiVersionFrom(@NonNull final Configuration config) {
        return config.getConfigData(VersionConfig.class).hapiVersion();
    }

    private class RunningHashManager {
        CompletableFuture<byte[]> nMinus3HashFuture;
        CompletableFuture<byte[]> nMinus2HashFuture;
        CompletableFuture<byte[]> nMinus1HashFuture;
        CompletableFuture<byte[]> hashFuture;

        Bytes latestHashes() {
            final var all = new byte[][] {
                nMinus3HashFuture.join(), nMinus2HashFuture.join(), nMinus1HashFuture.join(), hashFuture.join()
            };
            int numMissing = 0;
            while (numMissing < all.length && all[numMissing] == null) {
                numMissing++;
            }
            final byte[] hashes = new byte[(all.length - numMissing) * HASH_SIZE];
            for (int i = numMissing; i < all.length; i++) {
                System.arraycopy(all[i], 0, hashes, (i - numMissing) * HASH_SIZE, HASH_SIZE);
            }
            return Bytes.wrap(hashes);
        }

        /**
         * Starts managing running hashes for a new round, with the given trailing block hashes.
         *
         * @param blockStreamInfo the trailing block hashes at the start of the round
         */
        void startRound(@Nullable final BlockStreamInfo blockStreamInfo) {
            final var hashes = blockStreamInfo == null ? Bytes.EMPTY : blockStreamInfo.trailingBlockHashes();
            final var n = (int) (hashes.length() / HASH_SIZE);
            nMinus3HashFuture = completedFuture(n < 4 ? null : hashes.toByteArray(0, HASH_SIZE));
            nMinus2HashFuture = completedFuture(n < 3 ? null : hashes.toByteArray((n - 3) * HASH_SIZE, HASH_SIZE));
            nMinus1HashFuture = completedFuture(n < 2 ? null : hashes.toByteArray((n - 2) * HASH_SIZE, HASH_SIZE));
            hashFuture =
                    completedFuture(n < 1 ? new byte[HASH_SIZE] : hashes.toByteArray((n - 1) * HASH_SIZE, HASH_SIZE));
        }

        /**
         * Updates the running hashes for the given serialized output block item.
         *
         * @param bytes the serialized output block item
         */
        void nextResult(@NonNull final Bytes bytes) {
            requireNonNull(bytes);
            nMinus3HashFuture = nMinus2HashFuture;
            nMinus2HashFuture = nMinus1HashFuture;
            nMinus1HashFuture = hashFuture;
            hashFuture = hashFuture.thenCombineAsync(
                    supplyAsync(() -> noThrowSha384HashOf(bytes.toByteArray()), executor),
                    BlockImplUtils::combine,
                    executor);
        }
    }

    private class BlockHashManager {
        final int numTrailingBlocks;

        Bytes blockHashes;

        BlockHashManager(@NonNull final Configuration config) {
            this.numTrailingBlocks =
                    config.getConfigData(BlockRecordStreamConfig.class).numOfBlockHashesInState();
        }

        /**
         * Starts managing running hashes for a new round, with the given trailing block hashes.
         *
         * @param blockStreamInfo the trailing block hashes at the start of the round
         */
        void startBlock(@NonNull final BlockStreamInfo blockStreamInfo, @NonNull Bytes prevBlockHash) {
            blockHashes = blockStreamInfo.trailingBlockHashes();
            hashesAfterLatest(prevBlockHash);
        }

        @Nullable
        Bytes hashOfBlock(final long blockNo) {
            return BlockRecordInfoUtils.blockHashByBlockNumber(blockHashes, blockNumber - 1, blockNo);
        }

        Bytes hashesAfterLatest(@NonNull final Bytes blockHash) {
            return appendHash(blockHash, blockHashes, numTrailingBlocks);
        }
    }

    private record PendingBlock(BlockItemWriter writer, BlockProof.Builder proofBuilder, long blockNumber) {}
}
