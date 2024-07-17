/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.workflows.handle.record.StreamManager;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link StreamManager} primarily responsible for managing state ({@link RunningHashes} and
 * {@link BlockInfo}), and delegating to a {@link BlockRecordStreamProducer} for writing to the stream file, hashing,
 * and performing other duties, possibly on background threads. All APIs of {@link StreamManager} can only be
 * called from the "handle" thread!
 */
@Singleton
public final class BlockRecordManagerImpl extends AbstractBlockManagerImpl {
    private static final Logger logger = LogManager.getLogger(BlockRecordManagerImpl.class);

    /**
     * The number of seconds of consensus time in a block period, from configuration. This is computed based on the
     * {@link BlockRecordStreamConfig#logPeriod()} setting. This setting is computed once at startup and used
     * throughout.
     */
    private final long blockPeriodInSeconds;

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
        super(configProvider, state, streamFileProducer);

        // Get static configuration that is assumed not to change while the node is running
        final var recordStreamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        this.blockPeriodInSeconds = recordStreamConfig.logPeriod();
    }

    // =================================================================================================================
    // BlockRecordManager implementation

    /**
     * {@inheritDoc}
     */
    public boolean startUserTransaction(
            @NonNull final Instant consensusTime,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState) {
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
            ((BlockRecordStreamProducer) this.streamFileProducer).switchBlocks(-1, 0, consensusTime);
            return true;
        }

        // Check to see if we are at the boundary between blocks and should create a new one. Each block is covered
        // by some period. We'll compute the period of the current provisional block and the period covered by the
        // given consensus time, and if they are different, we'll close out the current block and start a new one.
        final var currentBlockPeriod = getBlockPeriod(lastBlockInfo.firstConsTimeOfCurrentBlock());
        final var newBlockPeriod = getBlockPeriod(consensusTime);

        // Also check to see if this is the first transaction we're handling after a freeze restart. If so, we also
        // start a new block.
        final var isFirstTransactionAfterFreezeRestart = platformState.getFreezeTime() != null
                && platformState.getFreezeTime().equals(platformState.getLastFrozenTime());
        if (isFirstTransactionAfterFreezeRestart) {
            platformState.setFreezeTime(null);
        }
        // Now we test if we need to start a new block. If so, create the new block
        if (newBlockPeriod > currentBlockPeriod || isFirstTransactionAfterFreezeRestart) {
            // Compute the state for the newly completed block. The `lastBlockHashBytes` is the running hash after
            // the last transaction
            final var lastBlockHashBytes = streamFileProducer.getRunningHash();
            final var justFinishedBlockNumber = lastBlockInfo.lastBlockNumber() + 1;
            lastBlockInfo =
                    infoOfJustFinished(lastBlockInfo, justFinishedBlockNumber, lastBlockHashBytes, consensusTime);

            // Update BlockInfo state
            putLastBlockInfo(state);

            // log end of block if needed
            if (logger.isDebugEnabled()) {
                logger.debug(
                        """
                                --- BLOCK UPDATE ---
                                  Finished: #{} (started @ {}) with hash {}
                                  Starting: #{} @ {}""",
                        justFinishedBlockNumber,
                        lastBlockInfo.firstConsTimeOfCurrentBlock(),
                        new Hash(lastBlockHashBytes.toByteArray(), DigestType.SHA_384),
                        justFinishedBlockNumber + 1,
                        consensusTime);
            }

            switchBlocksAt(consensusTime);
            return true;
        }
        return false;
    }

    @Override
    public void markMigrationTransactionsStreamed() {
        lastBlockInfo =
                lastBlockInfo.copyBuilder().migrationRecordsStreamed(true).build();
    }

    /**
     * We need this to preserve unit test expectations written that assumed a bug in the original implementation,
     * in which the first consensus time of the current block was not in state.
     *
     * @param consensusTime the consensus time at which to switch to the current block
     */
    @VisibleForTesting
    public void switchBlocksAt(@NonNull final Instant consensusTime) {
        ((BlockRecordStreamProducer) streamProducer()).switchBlocks(
                lastBlockInfo.lastBlockNumber(), lastBlockInfo.lastBlockNumber() + 1, consensusTime);
    }

    // ========================================================================================================
    // BlockRecordInfo Implementation

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Bytes lastBlockHash() {
        return BlockRecordInfoUtils.lastBlockHash(lastBlockInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(final long blockNo) {
        return BlockRecordInfoUtils.blockHashByBlockNumber(lastBlockInfo, blockNo);
    }

    // ========================================================================================================
    // Private Methods

    /**
     * Get the block period from consensus timestamp. Based on
     * {@link LinkedObjectStreamUtilities#getPeriod(Instant, long)} but updated to work on {@link Instant}.
     *
     * @param consensusTimestamp The consensus timestamp
     * @return The block period from epoch the consensus timestamp is in
     */
    private long getBlockPeriod(@Nullable final Instant consensusTimestamp) {
        if (consensusTimestamp == null) return 0;
        return consensusTimestamp.getEpochSecond() / blockPeriodInSeconds;
    }

    private long getBlockPeriod(@Nullable final Timestamp consensusTimestamp) {
        if (consensusTimestamp == null) return 0;
        return consensusTimestamp.seconds() / blockPeriodInSeconds;
    }

    /**
     * Create a new updated BlockInfo from existing BlockInfo and new block information. BlockInfo stores block hashes as a single
     * byte array, so we need to append or if full shift left and insert new block hash.
     *
     * @param lastBlockInfo The current block info
     * @param justFinishedBlockNumber The new block number
     * @param hashOfJustFinishedBlock The new block hash
     */
    private BlockInfo infoOfJustFinished(
            @NonNull final BlockInfo lastBlockInfo,
            final long justFinishedBlockNumber,
            @NonNull final Bytes hashOfJustFinishedBlock,
            @NonNull final Instant currentBlockFirstTransactionTime) {
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
                new Timestamp(
                        currentBlockFirstTransactionTime.getEpochSecond(), currentBlockFirstTransactionTime.getNano()));
    }
}
