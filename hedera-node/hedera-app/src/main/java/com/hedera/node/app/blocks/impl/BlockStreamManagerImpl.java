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
import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.appendHash;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.combine;
import static com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.HASH_SIZE;
import static com.swirlds.platform.state.SwirldStateManagerUtils.isInFreezePeriod;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.MerkleSiblingHash;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.StreamingTreeHasher;
import com.hedera.node.app.records.impl.BlockRecordInfoUtils;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.state.notifications.StateHashedNotification;
import com.swirlds.state.State;
import com.swirlds.state.spi.CommittableWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BlockStreamManagerImpl implements BlockStreamManager {
    private static final Logger log = LogManager.getLogger(BlockStreamManagerImpl.class);

    private static final int CHUNK_SIZE = 8;

    private final int roundsPerBlock;
    private final TssBaseService tssBaseService;
    private final SemanticVersion hapiVersion;
    private final ExecutorService executor;
    private final Supplier<BlockItemWriter> writerSupplier;
    private final BoundaryStateChangeListener boundaryStateChangeListener;

    private final BlockHashManager blockHashManager;
    private final RunningHashManager runningHashManager;

    // All this state is scoped to producing the current block
    private long blockNumber;
    // Set to the round number of the last round handled before entering a freeze period
    private long freezeRoundNumber = -1;
    private Bytes lastBlockHash;
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

    // (FUTURE) Remove this once reconnect protocol also transmits the last block hash
    private boolean appendRealHashes = false;

    /**
     * Represents a block pending completion by the block hash signature needed for its block proof.
     *
     * @param number the block number
     * @param blockHash the block hash
     * @param proofBuilder the block proof builder
     * @param writer the block item writer
     * @param siblingHashes the sibling hashes needed for an indirect block proof of an earlier block
     */
    private record PendingBlock(
            long number,
            @NonNull Bytes blockHash,
            @NonNull BlockProof.Builder proofBuilder,
            @NonNull BlockItemWriter writer,
            @NonNull MerkleSiblingHash... siblingHashes) {}

    /**
     * A queue of blocks pending completion by the block hash signature needed for their block proofs.
     */
    private final Queue<PendingBlock> pendingBlocks = new ConcurrentLinkedQueue<>();

    /**
     * A map of round numbers to the hash of the round state.
     */
    private final Map<Long, AtomicReference<CompletableFuture<Bytes>>> roundHashes = new ConcurrentHashMap<>();

    @Inject
    public BlockStreamManagerImpl(
            @NonNull final Supplier<BlockItemWriter> writerSupplier,
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final TssBaseService tssBaseService,
            @NonNull final BoundaryStateChangeListener boundaryStateChangeListener) {
        this.writerSupplier = requireNonNull(writerSupplier);
        this.executor = requireNonNull(executor);
        this.tssBaseService = requireNonNull(tssBaseService);
        this.boundaryStateChangeListener = requireNonNull(boundaryStateChangeListener);
        requireNonNull(configProvider);
        final var config = configProvider.getConfiguration();
        this.hapiVersion = hapiVersionFrom(config);
        this.roundsPerBlock = config.getConfigData(BlockStreamConfig.class).roundsPerBlock();
        this.blockHashManager = new BlockHashManager(config);
        this.runningHashManager = new RunningHashManager();
        roundHashes.put(0L, new AtomicReference<>(completedFuture(Bytes.wrap(new byte[HASH_SIZE]))));
    }

    @Override
    public void initLastBlockHash(@NonNull final Bytes blockHash) {
        lastBlockHash = requireNonNull(blockHash);
    }

    @Override
    public void startRound(@NonNull final Round round, @NonNull final State state) {
        if (lastBlockHash == null) {
            throw new IllegalStateException("Last block hash must be initialized before starting a round");
        }
        final var platformState = state.getReadableStates(PlatformStateService.NAME)
                .<PlatformState>getSingleton(V0540PlatformStateSchema.PLATFORM_STATE_KEY)
                .get();
        requireNonNull(platformState);
        if (isFreezeRound(platformState, round)) {
            // Track freeze round numbers because they always end a block
            freezeRoundNumber = round.getRoundNum();
        }
        if (writer == null) {
            writer = writerSupplier.get();
            blockTimestamp = round.getConsensusTimestamp();
            boundaryStateChangeListener.setLastUsedConsensusTime(blockTimestamp);

            final var blockStreamInfo = blockStreamInfoFrom(state);
            blockHashManager.startBlock(blockStreamInfo, lastBlockHash);
            runningHashManager.startBlock(blockStreamInfo);

            inputTreeHasher = new ConcurrentStreamingTreeHasher(executor);
            outputTreeHasher = new ConcurrentStreamingTreeHasher(executor);
            blockNumber = blockStreamInfo.blockNumber() + 1;
            pendingItems = new ArrayList<>();

            pendingItems.add(BlockItem.newBuilder()
                    .blockHeader(BlockHeader.newBuilder()
                            .number(blockNumber)
                            .previousBlockHash(lastBlockHash)
                            .hashAlgorithm(SHA2_384)
                            .softwareVersion(platformState.creationSoftwareVersionOrThrow())
                            .hapiProtoVersion(hapiVersion))
                    .build());

            writer.openBlock(blockNumber);
        }
    }

    @Override
    public void endRound(@NonNull final State state, final long roundNum) {
        if (shouldCloseBlock(roundNum, roundsPerBlock)) {
            final var writableState = state.getWritableStates(BlockStreamService.NAME);
            final var blockStreamInfoState = writableState.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY);
            // Ensure runningHashManager futures include all result items and are completed
            schedulePendingWork();
            writeFuture.join();
            // Commit the block stream info to state before flushing the boundary state changes
            blockStreamInfoState.put(new BlockStreamInfo(
                    blockNumber, blockTimestamp(), runningHashManager.latestHashes(), blockHashManager.blockHashes()));
            ((CommittableWritableStates) writableState).commit();

            // Flush the boundary state changes as our final hashable block item
            pendingItems.add(boundaryStateChangeListener.flushChanges());
            schedulePendingWork();
            writeFuture.join();

            final var inputHash = inputTreeHasher.rootHash().join();
            final var outputHash = outputTreeHasher.rootHash().join();

            final var leftParent = combine(lastBlockHash, inputHash);

            final var blockStartStateHashRef =
                    roundHashes.computeIfAbsent(roundNum - 1, (k) -> new AtomicReference<>());
            final CompletableFuture<Bytes> blockStartStateFuture = new CompletableFuture<>();
            blockStartStateHashRef.compareAndSet(null, blockStartStateFuture);
            final var blockStartStateHash = blockStartStateHashRef.get().join();

            final var rightParent = combine(outputHash, blockStartStateHash);
            final var blockHash = combine(leftParent, rightParent);
            final var pendingProof = BlockProof.newBuilder()
                    .block(blockNumber)
                    .previousBlockRootHash(lastBlockHash)
                    .startOfBlockStateRootHash(blockStartStateHash);
            pendingBlocks.add(new PendingBlock(
                    blockNumber,
                    blockHash,
                    pendingProof,
                    writer,
                    new MerkleSiblingHash(false, inputHash),
                    new MerkleSiblingHash(false, rightParent)));
            // Update in-memory state to prepare for the next block
            lastBlockHash = blockHash;
            writer = null;

            tssBaseService.requestLedgerSignature(blockHash.toByteArray());
        }
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
        // Incorporate all pending results before returning the seed to guarantee
        // no two consecutive transactions ever get the same seed
        schedulePendingWork();
        writeFuture.join();
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

    /**
     * Synchronized to ensure that block proofs are always written in order, even in edge cases where multiple
     * pending block proofs become available at the same time.
     *
     * @param message the number of the block to finish
     * @param signature the signature to use in the block proof
     */
    @Override
    public synchronized void accept(@NonNull final byte[] message, @NonNull final byte[] signature) {
        // Find the block whose hash as the signed message, tracking any sibling hashes
        // needed for indirect proofs of earlier blocks along the way
        long blockNumber = Long.MIN_VALUE;
        boolean impliesIndirectProof = false;
        final List<List<MerkleSiblingHash>> siblingHashes = new ArrayList<>();
        final var blockHash = Bytes.wrap(message);
        for (final var block : pendingBlocks) {
            if (impliesIndirectProof) {
                siblingHashes.add(List.of(block.siblingHashes()));
            }
            if (block.blockHash().equals(blockHash)) {
                blockNumber = block.number();
                break;
            }
            impliesIndirectProof = true;
        }
        if (blockNumber == Long.MIN_VALUE) {
            log.info("Ignoring signature on already proven block hash '{}'", blockHash);
            return;
        }
        // Write proofs for all pending blocks up to and including the signed block number
        final var blockSignature = Bytes.wrap(signature);
        while (!pendingBlocks.isEmpty() && pendingBlocks.peek().number() <= blockNumber) {
            final var block = pendingBlocks.poll();
            final var proof = block.proofBuilder()
                    .blockSignature(blockSignature)
                    .siblingHashes(siblingHashes.stream().flatMap(List::stream).toList());
            block.writer()
                    .writeItem(BlockItem.PROTOBUF.toBytes(
                            BlockItem.newBuilder().blockProof(proof).build()))
                    .closeBlock();
            if (block.number() != blockNumber) {
                siblingHashes.removeFirst();
            }
        }
    }

    /**
     * (FUTURE) Remove this after reconnect protocol also transmits the last block hash.
     */
    public void appendRealHashes() {
        this.appendRealHashes = true;
    }

    private void schedulePendingWork() {
        final var scheduledWork = new ScheduledWork(pendingItems);
        final var pendingSerialization = CompletableFuture.supplyAsync(scheduledWork::serializeItems, executor);
        writeFuture = writeFuture.thenCombine(pendingSerialization, scheduledWork::combineSerializedItems);
        pendingItems = new ArrayList<>();
    }

    private @NonNull BlockStreamInfo blockStreamInfoFrom(@NonNull final State state) {
        final var blockStreamInfoState =
                state.getReadableStates(BlockStreamService.NAME).<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY);
        return requireNonNull(blockStreamInfoState.get());
    }

    private boolean shouldCloseBlock(final long roundNumber, final int roundsPerBlock) {
        return roundNumber % roundsPerBlock == 0 || roundNumber == freezeRoundNumber;
    }

    private boolean isFreezeRound(@NonNull final PlatformState platformState, @NonNull final Round round) {
        return isInFreezePeriod(
                round.getConsensusTimestamp(),
                platformState.freezeTime() == null ? null : asInstant(platformState.freezeTime()),
                platformState.lastFrozenTime() == null ? null : asInstant(platformState.lastFrozenTime()));
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
        void startBlock(@NonNull final BlockStreamInfo blockStreamInfo) {
            final var hashes = blockStreamInfo.trailingOutputHashes();
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

        private Bytes blockHashes;

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
            if (appendRealHashes) {
                blockHashes = appendHash(prevBlockHash, blockStreamInfo.trailingBlockHashes(), numTrailingBlocks);
            } else {
                // (FUTURE) Remove this after reconnect protocol also transmits the last block hash
                blockHashes = appendHash(ZERO_BLOCK_HASH, blockStreamInfo.trailingBlockHashes(), numTrailingBlocks);
            }
        }

        /**
         * Returns the hash of the block with the given number, or null if it is not available. Note that,
         * <ul>
         *     <li>We never know the hash of the {@code N+1} block currently being created.</li>
         *     <li>We start every block {@code N} by concatenating the {@code N-1} block hash to the trailing
         *     hashes up to block {@code N-2} that were in state at the end of block {@code N-1}.
         * </ul>
         *
         * @param blockNo the block number
         * @return the hash of the block with the given number, or null if it is not available
         */
        @Nullable
        Bytes hashOfBlock(final long blockNo) {
            return BlockRecordInfoUtils.blockHashByBlockNumber(blockHashes, blockNumber - 1, blockNo);
        }

        /**
         * Returns the trailing block hashes.
         *
         * @return the trailing block hashes
         */
        Bytes blockHashes() {
            return blockHashes;
        }
    }

    @Override
    public void notify(final StateHashedNotification notification) {
        log.info(
                "StateHashedNotification Received : hash: {}, roundNumber: {}",
                notification.hash(),
                notification.round());
        final var ref = roundHashes.computeIfAbsent(notification.round(), k -> new AtomicReference<>());
        ref.compareAndSet(null, completedFuture(notification.hash().getBytes()));
        ref.get().complete(notification.hash().getBytes());
    }
}
