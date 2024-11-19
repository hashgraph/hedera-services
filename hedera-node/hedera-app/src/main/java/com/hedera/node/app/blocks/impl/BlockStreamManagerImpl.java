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

import static com.hedera.hapi.block.stream.BlockItem.ItemOneOfType.TRANSACTION_RESULT;
import static com.hedera.hapi.node.base.BlockHashAlgorithm.SHA2_384;
import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.blocks.BlockStreamManager.PendingWork.GENESIS_WORK;
import static com.hedera.node.app.blocks.BlockStreamManager.PendingWork.NONE;
import static com.hedera.node.app.blocks.BlockStreamManager.PendingWork.POST_UPGRADE_WORK;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.appendHash;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.combine;
import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.node.app.hapi.utils.CommonUtils.sha384DigestOrThrow;
import static com.hedera.node.app.records.BlockRecordService.EPOCH;
import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.HASH_SIZE;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_DELIMITED;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeTag;
import static com.swirlds.platform.state.SwirldStateManagerUtils.isInFreezePeriod;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.MerkleSiblingHash;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.block.stream.schema.BlockSchema;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.InitialStateHash;
import com.hedera.node.app.blocks.StreamingTreeHasher;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.records.impl.BlockRecordInfoUtils;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    private final int roundsPerBlock;
    private final int hashCombineBatchSize;
    private final int serializationBatchSize;
    private final TssBaseService tssBaseService;
    private final SemanticVersion version;
    private final SemanticVersion hapiVersion;
    private final ExecutorService executor;
    private final Supplier<BlockItemWriter> writerSupplier;
    private final BoundaryStateChangeListener boundaryStateChangeListener;

    private final BlockHashManager blockHashManager;
    private final RunningHashManager runningHashManager;

    // The status of pending work
    private PendingWork pendingWork = NONE;
    // The last time at which interval-based processing was done
    private Instant lastIntervalProcessTime = Instant.EPOCH;
    // All this state is scoped to producing the current block
    private long blockNumber;
    // Set to the round number of the last round handled before entering a freeze period
    private long freezeRoundNumber = -1;
    // The last non-empty (i.e., not skipped) round number that will eventually get a start-of-state hash
    private long lastNonEmptyRoundNumber;
    private Bytes lastBlockHash;
    private Instant blockTimestamp;
    private BlockItemWriter writer;
    private List<BlockItem> pendingItems;
    private StreamingTreeHasher inputTreeHasher;
    private StreamingTreeHasher outputTreeHasher;
    /**
     * A future that completes after all items not in the pendingItems list have been serialized
     * to bytes, with their hashes scheduled for incorporation in the input/output trees and running
     * hashes if applicable
     */
    private CompletableFuture<Void> hashFuture = completedFuture(null);

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
     * Blocks awaiting proof via ledger signature on their block hash (or a subsequent block hash).
     */
    private final Queue<PendingBlock> pendingBlocks = new ConcurrentLinkedQueue<>();
    /**
     * Futures that resolve when the end-of-round state hash is available for a given round number.
     */
    private final Map<Long, CompletableFuture<Bytes>> endRoundStateHashes = new ConcurrentHashMap<>();

    @Inject
    public BlockStreamManagerImpl(
            @NonNull final Supplier<BlockItemWriter> writerSupplier,
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final TssBaseService tssBaseService,
            @NonNull final BoundaryStateChangeListener boundaryStateChangeListener,
            @NonNull final InitialStateHash initialStateHash,
            @NonNull final SemanticVersion version) {
        this.version = requireNonNull(version);
        this.writerSupplier = requireNonNull(writerSupplier);
        this.executor = requireNonNull(executor);
        this.tssBaseService = requireNonNull(tssBaseService);
        this.boundaryStateChangeListener = requireNonNull(boundaryStateChangeListener);
        requireNonNull(configProvider);
        final var config = configProvider.getConfiguration();
        this.hapiVersion = hapiVersionFrom(config);
        final var blockStreamConfig = config.getConfigData(BlockStreamConfig.class);
        this.roundsPerBlock = blockStreamConfig.roundsPerBlock();
        this.hashCombineBatchSize = blockStreamConfig.hashCombineBatchSize();
        this.serializationBatchSize = blockStreamConfig.serializationBatchSize();
        this.blockHashManager = new BlockHashManager(config);
        this.runningHashManager = new RunningHashManager();
        this.lastNonEmptyRoundNumber = initialStateHash.roundNum();
        final var hashFuture = initialStateHash.hashFuture();
        endRoundStateHashes.put(lastNonEmptyRoundNumber, hashFuture);
        log.info(
                "Initialized BlockStreamManager from round {} with end-of-round hash {}",
                lastNonEmptyRoundNumber,
                hashFuture.isDone() ? hashFuture.join().toHex() : "<PENDING>");
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
        // If the platform handled this round, it must eventually hash its end state
        endRoundStateHashes.put(round.getRoundNum(), new CompletableFuture<>());

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
            boundaryStateChangeListener.setBoundaryTimestamp(blockTimestamp);

            final var blockStreamInfo = blockStreamInfoFrom(state);
            pendingWork = classifyPendingWork(blockStreamInfo, version);
            lastIntervalProcessTime = asInstant(blockStreamInfo.lastIntervalProcessTimeOrElse(EPOCH));
            blockHashManager.startBlock(blockStreamInfo, lastBlockHash);
            runningHashManager.startBlock(blockStreamInfo);

            inputTreeHasher = new ConcurrentStreamingTreeHasher(executor, hashCombineBatchSize);
            outputTreeHasher = new ConcurrentStreamingTreeHasher(executor, hashCombineBatchSize);
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
    public void confirmPendingWorkFinished() {
        if (pendingWork == NONE) {
            // Should never happen but throwing IllegalStateException might make the situation even worse, so just log
            log.error("HandleWorkflow confirmed finished work but none was pending");
        }
        pendingWork = NONE;
    }

    @Override
    public @NonNull PendingWork pendingWork() {
        return pendingWork;
    }

    @Override
    public @NonNull Instant lastIntervalProcessTime() {
        return lastIntervalProcessTime;
    }

    @Override
    public void setLastIntervalProcessTime(@NonNull final Instant lastIntervalProcessTime) {
        this.lastIntervalProcessTime = requireNonNull(lastIntervalProcessTime);
    }

    @Override
    public void endRound(@NonNull final State state, final long roundNum) {
        if (shouldCloseBlock(roundNum, roundsPerBlock)) {
            // Flush all boundary state changes besides the BlockStreamInfo
            pendingItems.add(boundaryStateChangeListener.flushChanges());

            // Combine the output of all remaining pending work (except the final block hash, which
            // depends on the completion of this pending work)
            final var scheduledWork = new ScheduledWork(pendingItems);
            final var pendingOutput = CompletableFuture.supplyAsync(scheduledWork::computeOutput, executor);
            hashFuture = hashFuture
                    .thenCombine(pendingOutput, (fromHash, fromPending) -> {
                        // Include remaining input/output hashes into the corresponding trees and running hash
                        this.combineOutput(null, fromPending);

                        // Write the output items to the stream
                        executor.submit(() -> writer.writeItems(fromPending.data()));
                        return null;
                    });
            pendingItems = new ArrayList<>();
            // We can't go any further in our computations of the final hash until all pending work is completed
            hashFuture.join();

            final var inputHash = inputTreeHasher.rootHash().join();
            // This block's starting state hash is the end state hash of the last non-empty round
            final var blockStartStateHash = requireNonNull(endRoundStateHashes.get(lastNonEmptyRoundNumber))
                    .join();
            // Now forget that hash, since it's been used
            endRoundStateHashes.remove(lastNonEmptyRoundNumber);
            // And update the last non-empty round number to this round
            lastNonEmptyRoundNumber = roundNum;
            final var outputTreeStatus = outputTreeHasher.status();

            // Put this block hash context in state via the block stream info
            final var writableState = state.getWritableStates(BlockStreamService.NAME);
            final var blockStreamInfoState = writableState.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY);
            blockStreamInfoState.put(new BlockStreamInfo(
                    blockNumber,
                    blockTimestamp(),
                    runningHashManager.latestHashes(),
                    blockHashManager.blockHashes(),
                    inputHash,
                    blockStartStateHash,
                    outputTreeStatus.numLeaves(),
                    outputTreeStatus.rightmostHashes(),
                    boundaryStateChangeListener.boundaryTimestampOrThrow(),
                    pendingWork != POST_UPGRADE_WORK,
                    version,
                    asTimestamp(lastIntervalProcessTime)));
            ((CommittableWritableStates) writableState).commit();

            // Serialize and hash the final block item
            final var finalWork = new ScheduledWork(List.of(boundaryStateChangeListener.flushChanges()));
            final var finalOutput = finalWork.computeOutput();
            // Ensure we only write and incorporate the final hash after all preceding work is done
            hashFuture.join();
            combineOutput(null, finalOutput);
            final var outputHash = outputTreeHasher.rootHash().join();
            final var leftParent = combine(lastBlockHash, inputHash);
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
        if (pendingItems.size() == serializationBatchSize) {
            schedulePendingWork();
        }
    }

    @Override
    public @Nullable Bytes prngSeed() {
        // Incorporate all pending results before returning the seed to guarantee
        // no two consecutive transactions ever get the same seed
        schedulePendingWork();
        hashFuture.join();
        final var seed = runningHashManager.nMinus3Hash;
        return seed == null ? null : Bytes.wrap(runningHashManager.nMinus3Hash);
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
            final var proofItem = BlockItem.newBuilder().blockProof(proof).build();
            block.writer().writePbjItem(BlockItem.PROTOBUF.toBytes(proofItem)).closeBlock();
            if (block.number() != blockNumber) {
                siblingHashes.removeFirst();
            }
        }
    }

    /**
     * Classifies the type of work pending, if any, given the block stream info from state and the current
     * software version.
     *
     * @param blockStreamInfo the block stream info
     * @param version the version
     * @return the type of pending work given the block stream info and version
     */
    @VisibleForTesting
    static PendingWork classifyPendingWork(
            @NonNull final BlockStreamInfo blockStreamInfo, @NonNull final SemanticVersion version) {
        requireNonNull(version);
        requireNonNull(blockStreamInfo);
        if (EPOCH.equals(blockStreamInfo.lastIntervalProcessTimeOrElse(EPOCH))) {
            // If we have never processed any time-based events, we must be at genesis
            return GENESIS_WORK;
        } else if (impliesPostUpgradeWorkPending(blockStreamInfo, version)) {
            return POST_UPGRADE_WORK;
        } else {
            return NONE;
        }
    }

    private static boolean impliesPostUpgradeWorkPending(
            @NonNull final BlockStreamInfo blockStreamInfo, @NonNull final SemanticVersion version) {
        return !version.equals(blockStreamInfo.creationSoftwareVersion()) || !blockStreamInfo.postUpgradeWorkDone();
    }

    private void schedulePendingWork() {
        final var scheduledWork = new ScheduledWork(pendingItems);
        final var pendingOutput = CompletableFuture.supplyAsync(scheduledWork::computeOutput, executor);
        hashFuture = hashFuture.thenCombine(pendingOutput, this::combineOutput);
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
    private static class ScheduledWork {
        private final List<BlockItem> items;

        public record Output(
                @NonNull BufferedData data,
                @NonNull ByteBuffer inputHashes,
                @NonNull ByteBuffer outputHashes,
                @NonNull ByteBuffer resultHashes) {}

        public ScheduledWork(@NonNull final List<BlockItem> items) {
            this.items = requireNonNull(items);
        }

        /**
         * Serializes the scheduled work items to bytes using the {@link BlockItem#PROTOBUF} codec and
         * computes the associated input/output hashes, returning the serialized items and hashes bundled
         * into an {@link Output}.
         *
         * @return the output of doing the scheduled work
         */
        public Output computeOutput() {
            var size = 0;
            var numInputs = 0;
            var numOutputs = 0;
            var numResults = 0;
            final var n = items.size();
            final var sizes = new int[n];
            for (var i = 0; i < n; i++) {
                final var item = items.get(i);
                sizes[i] = BlockItem.PROTOBUF.measureRecord(item);
                // Plus (at most) 8 bytes for the preceding tag and length
                size += (sizes[i] + 8);
                final var kind = item.item().kind();
                switch (kind) {
                    case EVENT_HEADER, EVENT_TRANSACTION -> numInputs++;
                    case TRANSACTION_RESULT, TRANSACTION_OUTPUT, STATE_CHANGES -> {
                        numOutputs++;
                        if (kind == TRANSACTION_RESULT) {
                            numResults++;
                        }
                    }
                }
            }
            final var inputHashes = new byte[numInputs * HASH_SIZE];
            final var outputHashes = new byte[numOutputs * HASH_SIZE];
            final var resultHashes = ByteBuffer.allocate(numResults * HASH_SIZE);
            final var serializedItems = ByteBuffer.allocate(size);
            final var data = BufferedData.wrap(serializedItems);
            final var digest = sha384DigestOrThrow();
            var j = 0;
            var k = 0;
            for (var i = 0; i < n; i++) {
                final var item = items.get(i);
                writeTag(data, BlockSchema.ITEMS, WIRE_TYPE_DELIMITED);
                data.writeVarInt(sizes[i], false);
                final var pre = serializedItems.position();
                writeItemToBuffer(item, data);
                final var post = serializedItems.position();
                final var kind = item.item().kind();
                switch (kind) {
                    case EVENT_HEADER, EVENT_TRANSACTION, TRANSACTION_RESULT, TRANSACTION_OUTPUT, STATE_CHANGES -> {
                        digest.update(serializedItems.array(), pre, post - pre);
                        switch (kind) {
                            case EVENT_HEADER, EVENT_TRANSACTION -> finish(digest, inputHashes, j++ * HASH_SIZE);
                            case TRANSACTION_RESULT, TRANSACTION_OUTPUT, STATE_CHANGES -> finish(
                                    digest, outputHashes, k++ * HASH_SIZE);
                        }
                        if (kind == TRANSACTION_RESULT) {
                            resultHashes.put(Arrays.copyOfRange(outputHashes, (k - 1) * HASH_SIZE, k * HASH_SIZE));
                        }
                    }
                    default -> {
                        // Other items have no special processing to do
                    }
                }
            }
            data.flip();
            return new Output(data, ByteBuffer.wrap(inputHashes), ByteBuffer.wrap(outputHashes), resultHashes.flip());
        }

        private void finish(@NonNull final MessageDigest digest, final byte[] hashes, final int offset) {
            try {
                digest.digest(hashes, offset, HASH_SIZE);
            } catch (DigestException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * Given the output of a {@link ScheduledWork} instance, incorporates its input/output hashes into
     * the corresponding trees and running hash.
     *
     * @param ignore ignored, needed for type compatibility with {@link CompletableFuture#thenCombine}
     * @param output the output to be combined
     * @return {@code null}
     */
    private Void combineOutput(@Nullable final Void ignore, @NonNull final ScheduledWork.Output output) {
        while (output.inputHashes().hasRemaining()) {
            inputTreeHasher.addLeaf(output.inputHashes());
        }
        while (output.outputHashes().hasRemaining()) {
            outputTreeHasher.addLeaf(output.outputHashes());
        }
        while (output.resultHashes().hasRemaining()) {
            runningHashManager.nextResultHash(output.resultHashes());
        }
        return null;
    }

    private SemanticVersion hapiVersionFrom(@NonNull final Configuration config) {
        return config.getConfigData(VersionConfig.class).hapiVersion();
    }

    private static class RunningHashManager {
        private static final ThreadLocal<byte[]> HASHES = ThreadLocal.withInitial(() -> new byte[HASH_SIZE]);
        private static final ThreadLocal<MessageDigest> DIGESTS =
                ThreadLocal.withInitial(CommonUtils::sha384DigestOrThrow);

        byte[] nMinus3Hash;
        byte[] nMinus2Hash;
        byte[] nMinus1Hash;
        byte[] hash;

        Bytes latestHashes() {
            final var all = new byte[][] {nMinus3Hash, nMinus2Hash, nMinus1Hash, hash};
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
            nMinus3Hash = n < 4 ? null : hashes.toByteArray(0, HASH_SIZE);
            nMinus2Hash = n < 3 ? null : hashes.toByteArray((n - 3) * HASH_SIZE, HASH_SIZE);
            nMinus1Hash = n < 2 ? null : hashes.toByteArray((n - 2) * HASH_SIZE, HASH_SIZE);
            hash = n < 1 ? new byte[HASH_SIZE] : hashes.toByteArray((n - 1) * HASH_SIZE, HASH_SIZE);
        }

        /**
         * Updates the running hashes for the given serialized output block item.
         *
         * @param hash the serialized output block item
         */
        void nextResultHash(@NonNull final ByteBuffer hash) {
            requireNonNull(hash);
            nMinus3Hash = nMinus2Hash;
            nMinus2Hash = nMinus1Hash;
            nMinus1Hash = this.hash;
            final var digest = DIGESTS.get();
            digest.update(this.hash);
            final var resultHash = HASHES.get();
            hash.get(resultHash);
            digest.update(resultHash);
            this.hash = digest.digest();
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
            blockHashes = appendHash(prevBlockHash, blockStreamInfo.trailingBlockHashes(), numTrailingBlocks);
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
    public void notify(@NonNull final StateHashedNotification notification) {
        endRoundStateHashes
                .get(notification.round())
                .complete(notification.hash().getBytes());
    }

    private static void writeItemToBuffer(@NonNull final BlockItem item, @NonNull final BufferedData bufferedData) {
        try {
            BlockItem.PROTOBUF.write(item, bufferedData);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
