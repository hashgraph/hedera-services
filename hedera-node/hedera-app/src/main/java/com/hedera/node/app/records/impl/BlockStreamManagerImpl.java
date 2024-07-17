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

package com.hedera.node.app.records.impl;

import static com.hedera.node.app.records.BlockRecordService.EPOCH;
import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.HASH_SIZE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.state.SingleTransactionStreamRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 */
@Singleton
public class BlockStreamManagerImpl implements BlockRecordManager {
    private static final Logger logger = LogManager.getLogger(BlockStreamManagerImpl.class);
    private final ExecutorService executor;

    /**
     * The number of rounds in the current provisional block. "provisional" because the block is not yet complete.
     */
    // Should this be stored in BlockInfo so that it's safe for restarts?
    private int roundsUntilNextBlock;

    /**
     * Keeps track if this block is currently open.
     */
    // Should this be stored in BlockInfo so that it's safe for restarts?
    private boolean blockOpen;

    private final int numBlockHashesToKeepBytes;
    private final int numRoundsInBlock;
    private final BlockStreamProducer blockStreamProducer;
    /** True when we have completed event recovery. This is not yet implemented properly. */
    private boolean eventRecoveryCompleted = false;

    /**
     * A {@link BlockInfo} of the most recently completed block. This is actually available in state, but there
     * is no reason for us to read it from state every time we need it, we can just recompute and cache this every
     * time we finish a provisional block.
     */
    private BlockInfo lastBlockInfo;

    @Inject
    public BlockStreamManagerImpl(
            @NonNull final ExecutorService executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaState state,
            @NonNull final BlockStreamProducer blockStreamProducer) {

        this.executor = requireNonNull(executor);
        requireNonNull(state);
        requireNonNull(configProvider);
        this.blockStreamProducer = requireNonNull(blockStreamProducer);

        // Get static configuration that is assumed not to change while the node is running
        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        this.numRoundsInBlock = blockStreamConfig.numRoundsInBlock();
        if (this.numRoundsInBlock <= 0) {
            throw new IllegalArgumentException("numRoundsInBlock must be greater than 0");
        }
        this.numBlockHashesToKeepBytes = blockStreamConfig.numOfBlockHashesInState() * HASH_SIZE;

        // Initialize the last block info and provisional block info.
        // NOTE: State migration happens BEFORE dagger initialization, and this object is managed by dagger. So we are
        // guaranteed that the state exists PRIOR to this call.
        final var states = state.getReadableStates(BlockRecordService.NAME);
        final var blockInfoState = states.<BlockInfo>getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY);
        this.lastBlockInfo = blockInfoState.get();
        assert this.lastBlockInfo != null : "Cannot be null, because this state is created at genesis";
        this.blockOpen = false;
        this.roundsUntilNextBlock = numRoundsInBlock;

        // Initialize the stream file producer. NOTE, if the producer cannot be initialized, and a random exception is
        // thrown here, then startup of the node will fail. This is the intended behavior. We MUST be able to produce
        // block streams, or there really is no point to running the node!
        final var runningHashState =
                states.<RunningHashes>getSingleton(V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY);
        final var lastRunningHashes = runningHashState.get();
        assert lastRunningHashes != null : "Cannot be null, because this state is created at genesis";
        this.blockStreamProducer.initFromLastBlock(lastRunningHashes, this.lastBlockInfo.lastBlockNumber());
    }

    @Override
    public boolean startUserTransaction(
            @NonNull Instant consensusTime, @NonNull HederaState state, @NonNull PlatformState platformState) {
        if (EPOCH.equals(lastBlockInfo.firstConsTimeOfCurrentBlock())) {
            // This is the first transaction of the first block, so set both the firstConsTimeOfCurrentBlock
            // and the current consensus time to now
            final var now = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
            lastBlockInfo = lastBlockInfo
                    .copyBuilder()
                    .consTimeOfLastHandledTxn(now)
                    .firstConsTimeOfCurrentBlock(now)
                    .build();
            putLastBlockInfo(state);
            return true;
        }
        return false;
    }

    @Override
    public void advanceConsensusClock(@NonNull Instant consensusTime, @NonNull HederaState state) {
        final var builder = this.lastBlockInfo
                .copyBuilder()
                .consTimeOfLastHandledTxn(Timestamp.newBuilder()
                        .seconds(consensusTime.getEpochSecond())
                        .nanos(consensusTime.getNano()));
        if (!this.lastBlockInfo.migrationRecordsStreamed()) {
            // Any records created during migration should have been published already. Now we shut off the flag to
            // disallow further publishing
            builder.migrationRecordsStreamed(true);
        }
        final var newBlockInfo = builder.build();

        // Update the latest block info in state
        final var states = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = states.<BlockInfo>getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY);
        blockInfoState.put(newBlockInfo);
        // Commit the changes. We don't ever want to roll back when advancing the consensus clock
        ((WritableSingletonStateBase<BlockInfo>) blockInfoState).commit();

        // Cache the updated block info
        this.lastBlockInfo = newBlockInfo;
    }

    @Override
    public void endUserTransaction(
            @NonNull SingleTransactionStreamRecord transactionRecord, @NonNull HederaState state) {
        // check if we need to run event recovery before we can write any new records to stream
        if (!this.eventRecoveryCompleted) {
            // FUTURE create event recovery class and call it here. Should this be in startUserTransaction()?
            this.eventRecoveryCompleted = true;
        }

        // Write the user transaction records to the block stream.
        blockStreamProducer.writeBlockItems(
                transactionRecord.blockItemStream() == null
                        ? Stream.empty()
                        : requireNonNull(transactionRecord.blockItemStream()));
    }

    @Override
    public void startRound() {
        roundsUntilNextBlock--;

        // We do not have an open block so create a new one.
        if (!isBlockOpen()) beginBlock();
    }

    @Override
    public void endRound(@NonNull HederaState state) {
        if (roundsUntilNextBlock <= 0) {
            updateBlockInfoForEndRound(state);
            resetRoundsUntilNextBlock();
        }

        updateRunningHashesInState(state);

        blockStreamProducer.endBlock();
    }

    @Override
    public void close() {
        try {
            blockStreamProducer.close();
        } catch (final Exception e) {
            // This is a fairly serious warning. This basically means we cannot guarantee that some records were
            // produced. However, since the {@link BlockRecordManager} is a singleton, this close method is only called
            // when the node is being shutdown anyway.
            logger.warn("Failed to close blockStreamProducer properly", e);
        }
    }

    @Override
    public void markMigrationRecordsStreamed() {
        lastBlockInfo =
                lastBlockInfo.copyBuilder().migrationRecordsStreamed(true).build();
    }

    @NonNull
    @Override
    public Instant consTimeOfLastHandledTxn() {
        final var lastHandledTxn = lastBlockInfo.consTimeOfLastHandledTxn();
        return lastHandledTxn != null
                ? Instant.ofEpochSecond(lastHandledTxn.seconds(), lastHandledTxn.nanos())
                : Instant.EPOCH;
    }

    @Override
    public void processSystemTransaction(ConsensusTransaction platformTxn) {
        blockStreamProducer.writeSystemTransaction(platformTxn);
    }

    @Nullable
    @Override
    public Bytes getRunningHash() {
        return blockStreamProducer.getRunningHash();
    }

    @Nullable
    @Override
    public Bytes getNMinus3RunningHash() {
        return blockStreamProducer.getNMinus3RunningHash();
    }

    @Override
    public long lastBlockNo() {
        return lastBlockInfo.lastBlockNumber();
    }

    @Nullable
    @Override
    public Instant firstConsTimeOfLastBlock() {
        return BlockRecordInfoUtils.firstConsTimeOfLastBlock(lastBlockInfo);
    }

    @NonNull
    @Override
    public Timestamp currentBlockTimestamp() {
        return lastBlockInfo.firstConsTimeOfCurrentBlockOrThrow();
    }

    @Nullable
    @Override
    public Bytes lastBlockHash() {
        return BlockRecordInfoUtils.lastBlockHash(lastBlockInfo);
    }

    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(long blockNo) {
        return BlockRecordInfoUtils.blockHashByBlockNumber(lastBlockInfo, blockNo);
    }

    private boolean isBlockOpen() {
        return blockOpen;
    }

    /**
     * Begin a new block.
     */
    private void beginBlock() {
        blockOpen = true;
        blockStreamProducer.beginBlock();
        // Log start of block if needed.
        if (logger.isDebugEnabled()) {
            logger.debug(
                    """
                                    --- BLOCK UPDATE ---
                                      Started: #{} @ {}""",
                    provisionalCurrentBlockNumber(),
                    consTimeOfLastHandledTxn());
        }
    }

    /**
     * End the current block.
     */
    private void updateBlockInfoForEndRound(@NonNull final HederaState state) {
        blockOpen = false;

        // Compute the state for the newly completed block. The `lastBlockHashBytes` is the running hash after
        // the last transaction.
        final var justFinishedBlockNumber = provisionalCurrentBlockNumber();
        final var lastBlockHashBytes = blockStreamProducer.getRunningHash();
        lastBlockInfo = infoOfJustFinished(lastBlockInfo, justFinishedBlockNumber, lastBlockHashBytes);

        // Update BlockInfo state.
        final var states = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = states.<BlockInfo>getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY);
        blockInfoState.put(lastBlockInfo);

        final var consensusTime = consTimeOfLastHandledTxn();

        // Log end of block if needed.
        if (logger.isDebugEnabled()) {
            logger.debug(
                    """
                                    --- BLOCK UPDATE ---
                                      Finished: #{} @ {} with hash {}""",
                    justFinishedBlockNumber,
                    consensusTime,
                    new Hash(lastBlockHashBytes.toByteArray(), DigestType.SHA_384));
        }
    }

    /**
     * The provisional current block number. This is the block number of the block we are currently writing to. This is
     * provisional because the block is not yet complete.
     * @return The provisional current block number.
     */
    private long provisionalCurrentBlockNumber() {
        return lastBlockInfo.lastBlockNumber() + 1;
    }

    /**
     * Create a new updated BlockInfo from existing BlockInfo and new block information. BlockInfo stores block hashes as a single
     * byte array, so we need to append or if full shift left and insert new block hash.
     *
     * @param lastBlockInfo The current block info
     * @param justFinishedBlockNumber The new block number
     * @param hashOfJustFinishedBlock The new block hash
     */
    @NonNull
    private BlockInfo infoOfJustFinished(
            @NonNull final BlockInfo lastBlockInfo,
            final long justFinishedBlockNumber,
            @NonNull final Bytes hashOfJustFinishedBlock) {
        // compute new block hashes bytes
        final byte[] blockHashesBytes = lastBlockInfo.blockHashes().toByteArray();
        byte[] newBlockHashesBytes;
        if (blockHashesBytes.length < numBlockHashesToKeepBytes) {
            // append new hash bytes to end
            newBlockHashesBytes = new byte[blockHashesBytes.length + HASH_SIZE];
            System.arraycopy(blockHashesBytes, 0, newBlockHashesBytes, 0, blockHashesBytes.length);
            hashOfJustFinishedBlock.getBytes(0, newBlockHashesBytes, newBlockHashesBytes.length - HASH_SIZE, HASH_SIZE);
        } else {
            // shift bytes left by HASH_SIZE and then set new hash bytes to at end HASH_SIZE bytes
            newBlockHashesBytes = blockHashesBytes;
            System.arraycopy(
                    newBlockHashesBytes, HASH_SIZE, newBlockHashesBytes, 0, newBlockHashesBytes.length - HASH_SIZE);
            hashOfJustFinishedBlock.getBytes(0, newBlockHashesBytes, newBlockHashesBytes.length - HASH_SIZE, HASH_SIZE);
        }

        return new BlockInfo(
                justFinishedBlockNumber,
                lastBlockInfo.firstConsTimeOfCurrentBlock(),
                Bytes.wrap(newBlockHashesBytes),
                lastBlockInfo.consTimeOfLastHandledTxn(),
                lastBlockInfo.migrationRecordsStreamed(),
                null);
    }

    private void putLastBlockInfo(@NonNull final HederaState state) {
        final var states = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = states.<BlockInfo>getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY);
        blockInfoState.put(lastBlockInfo);
    }

    private void resetRoundsUntilNextBlock() {
        roundsUntilNextBlock = numRoundsInBlock;
    }

    private void updateRunningHashesInState(@NonNull final HederaState state) {
        // We get the latest running hash from the BlockStreamProducer, blocking if needed for it to be computed.
        final var currentRunningHash = blockStreamProducer.getRunningHash();
        // Update running hashes in state with the latest running hash and the previous 3 running hashes.
        final var states = state.getWritableStates(BlockRecordService.NAME);
        final var runningHashesState =
                states.<RunningHashes>getSingleton(V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY);
        final var existingRunningHashes = runningHashesState.get();
        assert existingRunningHashes != null : "This cannot be null because genesis migration sets it";
        runningHashesState.put(new RunningHashes(
                currentRunningHash,
                existingRunningHashes.runningHash(),
                existingRunningHashes.nMinus1RunningHash(),
                existingRunningHashes.nMinus2RunningHash()));
        // Commit the changes to the merkle tree.
        ((WritableSingletonStateBase<RunningHashes>) runningHashesState).commit();
    }
}
