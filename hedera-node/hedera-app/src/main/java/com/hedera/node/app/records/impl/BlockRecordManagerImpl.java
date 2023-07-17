/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.BlockRecordStreamConfig;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link BlockRecordManager} primarily responsible for managing state ({@link RunningHashes} and
 * {@link BlockInfo}), and delegating to a {@link BlockRecordStreamProducer} for writing to the stream file, hashing,
 * and performing other duties, possibly on background threads. All APIs of {@link BlockRecordManager} can only be
 * called from the "handle" thread!
 */
@Singleton
public final class BlockRecordManagerImpl implements BlockRecordManager {
    private static final Logger logger = LogManager.getLogger(BlockRecordManagerImpl.class);
    /** The hash size in bytes, normally 48 for SHA384 */
    private static final int HASH_SIZE = DigestType.SHA_384.digestLength();

    /**
     * The number of blocks to keep multiplied by hash size. This is computed based on the
     * {@link BlockRecordStreamConfig#numOfBlockHashesInState()} setting multiplied by the size of each hash. This
     * setting is computed once at startup and used throughout.
     */
    private final int numBlockHashesToKeepBytes;
    /**
     * The number of milliseconds of consensus time in a block period, from configuration. This is computed based on the
     * {@link BlockRecordStreamConfig#logPeriod()} setting, multiplied by 1000 to convert from seconds to milliseconds.
     * This setting is computed once at startup and used throughout.
     */
    private final long blockPeriodInMilliSeconds;
    /**
     * The stream file producer we are using. This is set once during startup, and used throughout the execution of the
     * node. It would be nice to allow this to be a dynamic property, but it just isn't convenient to do so at this
     * time.
     */
    private final BlockRecordStreamProducer streamFileProducer;
    /**
     * A {@link BlockInfo} of the most recently completed block. This is actually available in state, but there
     * is no reason for us to read it from state every time we need it, we can just recompute and cache this every
     * time we finish a provisional block.
     */
    private BlockInfo lastBlockInfo;
    /** The number of the current provisional block. "provisional" because the block is not yet complete. */
    private long provisionalCurrentBlockNumber;
    /**
     * The consensus time of the first transaction in the current block. "provisional" because the block is not yet
     * complete.
     */
    private Instant provisionalCurrentBlockFirstTransactionTime = null;
    /** True when we have completed event recovery. This is not yet implemented properly. */
    private boolean eventRecoveryCompleted = false;

    /**
     * Construct BlockRecordManager
     *
     * @param configProvider The configuration provider
     * @param state The current hedera state
     * @param streamFileProducer The stream file producer
     */
    @Inject
    public BlockRecordManagerImpl(
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaState state,
            @NonNull final BlockRecordStreamProducer streamFileProducer) {

        requireNonNull(state);
        requireNonNull(configProvider);
        this.streamFileProducer = requireNonNull(streamFileProducer);

        // FUTURE: check if we were started in event recover mode and if event recovery needs to be completed before we
        // write any new records to stream
        this.eventRecoveryCompleted = false;

        // Get static configuration that is assumed not to change while the node is running
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        this.blockPeriodInMilliSeconds = recordStreamConfig.logPeriod() * 1000L;
        this.numBlockHashesToKeepBytes = recordStreamConfig.numOfBlockHashesInState() * HASH_SIZE;

        // Initialize the last block info and provisional block info.
        // NOTE: State migration happens BEFORE dagger initialization, and this object is managed by dagger. So we are
        // guaranteed that the state exists PRIOR to this call.
        final var states = state.createReadableStates(BlockRecordService.NAME);
        final var blockInfoState = states.<BlockInfo>getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY);
        this.lastBlockInfo = blockInfoState.get();
        assert this.lastBlockInfo != null : "Cannot be null, because this state is created at genesis";
        this.provisionalCurrentBlockNumber = lastBlockInfo.lastBlockNumber() + 1; // We know what this will be
        this.provisionalCurrentBlockFirstTransactionTime = null; // We do not know what this will be yet

        // Initialize the stream file producer. NOTE, if the producer cannot be initialized, and a random exception is
        // thrown here, then startup of the node will fail. This is the intended behavior. We MUST be able to produce
        // record streams, or there really is no point to running the node!
        final var runningHashState = states.<RunningHashes>getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY);
        final var lastRunningHashes = runningHashState.get();
        assert lastRunningHashes != null : "Cannot be null, because this state is created at genesis";
        this.streamFileProducer.initRunningHash(lastRunningHashes);
    }

    // =================================================================================================================
    // AutoCloseable implementation

    /** {@inheritDoc} */
    @Override
    public void close() {
        try {
            streamFileProducer.close();
        } catch (final Exception e) {
            // This is a fairly serious warning. This basically means we cannot guarantee that some records were
            // produced. However, since the {@link BlockRecordManager} is a singleton, this close method is only called
            // when the node is being shutdown anyway.
            logger.warn("Failed to close streamFileProducer properly", e);
        }
    }

    // =================================================================================================================
    // BlockRecordManager implementation

    /** {@inheritDoc} */
    public void startUserTransaction(@NonNull final Instant consensusTime, @NonNull final HederaState state) {
        // Is this the very first transaction since the node was started?
        final var restarted = provisionalCurrentBlockFirstTransactionTime == null;
        // Check to see if we are in Genesis. If we are, then we need to create a new provisional first block.
        if (lastBlockInfo.lastBlockNumber() == 0 && restarted) {
            // we are in genesis, so create a new block 1
            streamFileProducer.switchBlocks(0, 1, consensusTime);
            // set this transaction as the first transaction in the current block
            provisionalCurrentBlockFirstTransactionTime = consensusTime;
            // set block number as first block
            provisionalCurrentBlockNumber = 1;
        } else if (restarted) {
            // We are NOT in genesis, but we did just restart. So we will need to create a new block, but we don't
            // need to close out the old one.
            final var lastBlockNo = lastBlockInfo.lastBlockNumber();
            provisionalCurrentBlockNumber = lastBlockNo + 1;
            provisionalCurrentBlockFirstTransactionTime = consensusTime;
            streamFileProducer.switchBlocks(lastBlockNo, provisionalCurrentBlockNumber, consensusTime);
        } else {
            // Check to see if we are at the boundary between blocks and should create a new one. Each block is covered
            // by some period. We'll compute the period of the current provisional block and the period covered by the
            // given consensus time, and if they are different, we'll close out the current block and start a new one.
            final var currentBlockPeriod = getBlockPeriod(provisionalCurrentBlockFirstTransactionTime);
            final var newBlockPeriod = getBlockPeriod(consensusTime);
            if (newBlockPeriod > currentBlockPeriod) {
                // Compute the state for the newly completed block. The `lastBlockHashBytes` is the running hash after
                // the last transaction
                final var lastBlockNo = provisionalCurrentBlockNumber;
                final var lastBlockFirstTransactionTime = provisionalCurrentBlockFirstTransactionTime;
                final var lastBlockHashBytes = streamFileProducer.getRunningHash();
                lastBlockInfo =
                        updateBlockInfo(lastBlockInfo, lastBlockNo, lastBlockFirstTransactionTime, lastBlockHashBytes);

                // Update BlockInfo state
                final var states = state.createWritableStates(BlockRecordService.NAME);
                final var blockInfoState = states.<BlockInfo>getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY);
                blockInfoState.put(lastBlockInfo);

                // log end of block if needed
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            """
                                    --- BLOCK UPDATE ---
                                      Finished: #{} @ {} with hash {}
                                      Starting: #{} @ {}""",
                            lastBlockNo,
                            provisionalCurrentBlockFirstTransactionTime,
                            new Hash(lastBlockHashBytes.toByteArray(), DigestType.SHA_384),
                            lastBlockNo + 1,
                            consensusTime);
                }

                // close all stream files for end of block and create signature files, then open new block record file
                provisionalCurrentBlockNumber = lastBlockNo + 1;
                provisionalCurrentBlockFirstTransactionTime = consensusTime;
                streamFileProducer.switchBlocks(lastBlockNo, provisionalCurrentBlockNumber, consensusTime);
            }
        }
    }

    /** {@inheritDoc} */
    public void endUserTransaction(
            @NonNull final Stream<SingleTransactionRecord> recordStreamItems, @NonNull final HederaState state) {
        // check if we need to run event recovery before we can write any new records to stream
        if (!this.eventRecoveryCompleted) {
            // FUTURE create event recovery class and call it here. Should this be in startUserTransaction()?
            this.eventRecoveryCompleted = true;
        }
        // pass to record stream writer to handle
        streamFileProducer.writeRecordStreamItems(recordStreamItems);
    }

    /** {@inheritDoc} */
    @Override
    public void endRound(@NonNull final HederaState state) {
        // We get the latest running hash from the StreamFileProducer blocking if needed for it to be computed.
        final var currentRunningHash = streamFileProducer.getRunningHash();
        // Update running hashes in state with the latest running hash and the previous 3 running hashes.
        final var states = state.createWritableStates(BlockRecordService.NAME);
        final var runningHashesState = states.<RunningHashes>getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY);
        final var existingRunningHashes = runningHashesState.get();
        assert existingRunningHashes != null : "This cannot be null because genesis migration sets it";
        runningHashesState.put(new RunningHashes(
                currentRunningHash,
                existingRunningHashes.runningHash(),
                existingRunningHashes.nMinus1RunningHash(),
                existingRunningHashes.nMinus2RunningHash()));
    }

    // ========================================================================================================
    // Running Hash Getter Methods

    /**
     * Get the runningHash of all RecordStreamObject. This will block if the running hash has not yet
     * been computed for the most recent user transaction.
     *
     * @return the runningHash of all RecordStreamObject, or null if there are no running hashes yet
     */
    @NonNull
    @Override
    public Bytes getRunningHash() {
        return streamFileProducer.getRunningHash();
    }

    /**
     * Get the previous, previous, previous runningHash of all RecordStreamObject. This will block if
     * the running hash has not yet been computed for the most recent user transaction.
     *
     * @return the previous, previous, previous runningHash of all RecordStreamObject, or null if there is not one yet
     */
    @Nullable
    @Override
    public Bytes getNMinus3RunningHash() {
        return streamFileProducer.getNMinus3RunningHash();
    }

    // ========================================================================================================
    // BlockRecordInfo Implementation

    /**
     * Get the last block number, this is the last completed immutable block.
     *
     * @return the current block number, 0 of there is no blocks yet
     */
    @Override
    public long lastBlockNo() {
        return lastBlockInfo.lastBlockNumber();
    }

    /**
     * Get the consensus time of the first transaction of the last block, this is the last completed immutable block.
     *
     * @return the consensus time of the first transaction of the last block, null if there was no previous block
     */
    @Nullable
    @Override
    public Instant firstConsTimeOfLastBlock() {
        final var firstConsTimeOfLastBlock = lastBlockInfo.firstConsTimeOfLastBlock();
        return firstConsTimeOfLastBlock != null
                ? Instant.ofEpochSecond(firstConsTimeOfLastBlock.seconds(), firstConsTimeOfLastBlock.nanos())
                : null;
    }

    /**
     * Gets the hash of the last block
     *
     * @return the last block hash, null if no blocks have been created
     */
    @Nullable
    @Override
    public Bytes lastBlockHash() {
        return getLastBlockHash(lastBlockInfo);
    }

    /**
     * Returns the hash of the given block number, or {@code null} if unavailable.
     *
     * @param blockNo the block number of interest, must be within range of (current_block - 1) -> (current_block - 254)
     * @return its hash, if available otherwise null
     */
    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(final long blockNo) {
        final Bytes blockHashes = lastBlockInfo.blockHashes();
        final long blocksAvailable = blockHashes.length() / HASH_SIZE;
        if (blockNo < 0) {
            return null;
        }
        final long lastBlockNo = lastBlockInfo.lastBlockNumber();
        final long firstAvailableBlockNo = lastBlockNo - blocksAvailable + 1;
        // If blocksAvailable == 0, then firstAvailable == blockNo; and all numbers are
        // either less than or greater than or equal to blockNo, so we return unavailable
        if (blockNo < firstAvailableBlockNo || blockNo > lastBlockNo) {
            return null;
        } else {
            long offset = (blockNo - firstAvailableBlockNo) * HASH_SIZE;
            return blockHashes.slice(offset, HASH_SIZE);
        }
    }

    // ========================================================================================================
    // Private Methods

    /**
     * Get the block period from consensus timestamp. Based on
     * {@link LinkedObjectStreamUtilities#getPeriod(Instant, long)} but updated to work on {@link Instant}.
     *
     * @param consensusTimestamp The consensus timestamp
     * @return The block period from epoc the consensus timestamp is in
     */
    private long getBlockPeriod(@Nullable final Instant consensusTimestamp) {
        if (consensusTimestamp == null) return 0;
        final long nanos = consensusTimestamp.getEpochSecond() * 1_000_000_000L + consensusTimestamp.getNano();
        return nanos / 1_000_000L / blockPeriodInMilliSeconds;
    }

    /**
     * Get the last block hash from the block info. This is the last block hash in the block hashes byte array.
     *
     * @param blockInfo The block info
     * @return The last block hash, or null if there are no blocks yet
     */
    private Bytes getLastBlockHash(@Nullable final BlockInfo blockInfo) {
        if (blockInfo != null) {
            Bytes runningBlockHashes = blockInfo.blockHashes();
            if (runningBlockHashes != null && runningBlockHashes.length() >= HASH_SIZE) {
                return runningBlockHashes.slice(runningBlockHashes.length() - HASH_SIZE, HASH_SIZE);
            }
        }
        return null;
    }

    /**
     * Create a new updated BlockInfo from existing BlockInfo and new block information. BlockInfo stores block hashes as a single
     * byte array, so we need to append or if full shift left and insert new block hash.
     *
     * @param currentBlockInfo The current block info
     * @param newBlockNumber The new block number
     * @param blockFirstTransactionTime The new block first transaction time
     * @param blockHash The new block hash
     */
    private BlockInfo updateBlockInfo(
            BlockInfo currentBlockInfo, long newBlockNumber, Instant blockFirstTransactionTime, Bytes blockHash) {
        // compute new block hashes bytes
        final byte[] blockHashesBytes = currentBlockInfo.blockHashes().toByteArray();
        byte[] newBlockHashesBytes;
        if (blockHashesBytes.length < numBlockHashesToKeepBytes) {
            // append new hash bytes to end
            newBlockHashesBytes = new byte[blockHashesBytes.length + HASH_SIZE];
            System.arraycopy(blockHashesBytes, 0, newBlockHashesBytes, 0, blockHashesBytes.length);
            blockHash.getBytes(0, newBlockHashesBytes, newBlockHashesBytes.length - HASH_SIZE, HASH_SIZE);
        } else {
            // shift bytes left by HASH_SIZE and then set new hash bytes to at end HASH_SIZE bytes
            newBlockHashesBytes = blockHashesBytes;
            System.arraycopy(
                    newBlockHashesBytes, HASH_SIZE, newBlockHashesBytes, 0, newBlockHashesBytes.length - HASH_SIZE);
            blockHash.getBytes(0, newBlockHashesBytes, newBlockHashesBytes.length - HASH_SIZE, HASH_SIZE);
        }
        return new BlockInfo(
                newBlockNumber,
                new Timestamp(blockFirstTransactionTime.getEpochSecond(), blockFirstTransactionTime.getNano()),
                Bytes.wrap(newBlockHashesBytes));
    }
}
