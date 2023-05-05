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

package com.hedera.node.app.records;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.files.*;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import com.swirlds.common.stream.Signer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.FileSystem;
import java.time.Instant;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BlockRecordManager is a singleton facility that records transaction records into the record stream. Block &amp; Record management is built of 3 layers:
 * <ul>
 *     <li><b>BlockRecordManager</b> - Responsible for:
 *     <ul>
 *         <li>Packaging transaction records into files and sending for writing</li>
 *         <li>Manages block number</li>
 *         <li>Manages Running Hashes</li>
 *         <li>Manages Record State</li>
 *     </ul>
 *     </li>
 *     <li><b>StreamFileProducer ({@link StreamFileProducerBase} and {@link StreamFileProducerConcurrent})</b> - Responsible for managing
 *     production record and sidecar streams in a multi-threaded efficient way.</li>
 *     <li><b>File Writers({@link RecordFileWriter}, {@link SidecarFileWriter} and {@link SignatureFileWriter})</b> - Responsible for writing the actual files
 *     in the correct format.</li>
 * </ul>
 * <p>
 *     NOTE: It expects all methods to be called on handle transaction thread.
 * </p>
 * <p>
 *     NOTE: Any state that needs to persist across restarts is stored in the {@link BlockInfo} state.
 * </p>
 */
@SuppressWarnings("DuplicatedCode")
@Singleton
public final class BlockRecordManagerImpl implements BlockRecordManager {
    private static final Logger log = LogManager.getLogger(BlockRecordManagerImpl.class);
    /** The hash size in bytes, normally 48 for SHA384 */
    private static final int HASH_SIZE = DigestType.SHA_384.digestLength();

    /** Number of blocks to keep multiplied by hash size */
    private final int numBlockHashesToKeepBytes;
    /** The number of milliseconds of consensus time in a block period, from configuration */
    private final long blockPeriodInMilliSeconds;
    /** The configuration provider */
    private final ConfigProvider configProvider;
    /** The stream file producer we are using */
    private final StreamFileProducerBase streamFileProducer;
    /** The working state accessor, for accessing state adhoc */
    private final WorkingStateAccessor workingStateAccessor;
    /** Ture when we have completed event recovery */
    private boolean eventRecoveryCompleted = false;
    /** The number of the current block. "provisional" because the block is not yet complete. */
    private long provisionalCurrentBlockNumber;
    /** The consensus time of the first transaction in the current block. "provisional" because the block is not yet complete. */
    private Instant provisionalCurrentBlockFirstTransactionTime = null;

    /**
     * Construct BlockRecordManager
     *
     * @param configProvider The configuration provider
     * @param nodeInfo The node info
     * @param signer The signer for signing files
     * @param fileSystem the file system to use, needed for testing to be able to use a non-standard file
     *                   system. If null default is used.
     * @param workingStateAccessor The working state accessor, for accessing state adhoc
     */
    @Inject
    public BlockRecordManagerImpl(
            @NonNull final ConfigProvider configProvider,
            @NonNull final NodeInfo nodeInfo,
            @NonNull final Signer signer,
            @Nullable final FileSystem fileSystem,
            @NonNull final WorkingStateAccessor workingStateAccessor) {
        this.configProvider = requireNonNull(configProvider);
        this.workingStateAccessor = requireNonNull(workingStateAccessor);
        this.streamFileProducer = new StreamFileProducerConcurrent(
                configProvider,
                requireNonNull(nodeInfo),
                requireNonNull(signer),
                fileSystem,
                ForkJoinPool.commonPool());
        // check if we were started in event recover mode and if event recovery needs to be completed before we write
        // any new records to stream
        this.eventRecoveryCompleted = !nodeInfo.wasStartedInEventStreamRecovery();
        // get configuration needed, this is configuration that is assumed not to change while the node is running
        BlockRecordStreamConfig recordStreamConfig =
                configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        this.blockPeriodInMilliSeconds = recordStreamConfig.logPeriod() * 1000L;
        this.numBlockHashesToKeepBytes = recordStreamConfig.numOfBlockHashesInState() * HASH_SIZE;
    }

    // =======================================================================================================================
    // Shutdown methods

    /**
     * Closes this BlockRecordManager and wait for any threads to finish.
     */
    @Override
    public void close() throws Exception {
        streamFileProducer.close();
    }

    // =======================================================================================================================
    // Update methods

    /**
     * Inform BlockRecordManager of the new consensus time at the beginning of new transaction. This should only be called for before user
     * transactions where the workflow knows 100% that any there will be no new transaction records for any consensus time prior to this one.
     * <p>
     * This allows BlockRecordManager to set up the correct block information for the user transaction that is about to be executed. So block
     * questions are answered correctly.
     * <p>
     * The BlockRecordManager may choose to close one or more files if consensus time threshold has passed.
     *
     * @param consensusTime The consensus time of the user transaction we are about to start executing. It must be the adjusted consensus time
     *                      not the platform assigned consensus time. Assuming the two are different.
     * @param state         The state to update when new blocks are created
     */
    public void startUserTransaction(Instant consensusTime, HederaState state) {
        final WritableStates writableStates = state.createWritableStates(BlockRecordService.NAME);
        final WritableSingletonState<BlockInfo> blockInfoWritableSingletonState =
                writableStates.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY);
        final BlockInfo blockInfo = blockInfoWritableSingletonState.get();
        if (blockInfo == null) {
            throw new IllegalStateException("BlockInfo should never be null");
        }
        // if this is the start of first transaction since node was started, then we need load the initial running hash
        // from state
        if (provisionalCurrentBlockFirstTransactionTime == null) {
            // start the StreamFileProducer off with current running hash
            this.streamFileProducer.setRunningHash(getLastBlockHash(blockInfo));
        }
        // get timestamp of first transaction in previous block from state
        final Timestamp firstConsTimeOfLastBlock = blockInfo.firstConsTimeOfLastBlock();
        if ((provisionalCurrentBlockFirstTransactionTime == null)
                && (firstConsTimeOfLastBlock == null || firstConsTimeOfLastBlock.seconds() == 0)) {
            // we are in genesis, so create a new block 1
            streamFileProducer.switchBlocks(0, 1, consensusTime);
            // set this transaction as the first transaction in the current block
            provisionalCurrentBlockFirstTransactionTime = consensusTime;
            // set block number as first block
            provisionalCurrentBlockNumber = 1;
        } else {
            // there has been at least one block before. Check if are at the boundary of creating a new block
            final long currentBlockPeriod = getBlockPeriod(provisionalCurrentBlockFirstTransactionTime);
            final long newBlockPeriod = getBlockPeriod(consensusTime);
            if (newBlockPeriod > currentBlockPeriod) {
                // we are in a new block, so close the previous one if we are not the first block after startup
                // otherwise load the block info from state for the last block before the node was started.
                final long lastBlockNo;
                if (provisionalCurrentBlockFirstTransactionTime == null) {
                    // last block was the last block saved in state before the node started, so load it from state
                    lastBlockNo = blockInfo.lastBlockNo();
                } else {
                    // last block has finished so compute newly closed block number
                    lastBlockNo = blockInfo.lastBlockNo() + 1;
                    // compute block hash of the newly closed block, this is the running hash after the last transaction
                    // record.
                    final Bytes lastBlockHashBytes = streamFileProducer.getRunningHash();
                    assert lastBlockHashBytes != null
                            : "lastBlockHashBytes should never be null, we only get here "
                                    + "if we have completed a block";
                    // update the first transaction time of the last block
                    final Instant lastBlockFirstTransactionTime = provisionalCurrentBlockFirstTransactionTime;
                    // update BlockInfo state
                    blockInfoWritableSingletonState.put(
                            updateBlockInfo(blockInfo, lastBlockNo, lastBlockFirstTransactionTime, lastBlockHashBytes));
                    // log end of block if needed
                    BlockRecordStreamConfig recordStreamConfig =
                            configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
                    if (recordStreamConfig.logEveryTransaction()) {
                        log.info(
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
                }
                // close all stream files for end of block and create signature files, then open new block record file
                streamFileProducer.switchBlocks(lastBlockNo, lastBlockNo + 1, consensusTime);
                // update the provisional current block number to the new block number
                provisionalCurrentBlockNumber = lastBlockNo + 1;
                // update current block first transaction time, as we are starting the first transaction in the new
                // block
                provisionalCurrentBlockFirstTransactionTime = consensusTime;
            }
        }
    }

    /**
     * Add a user transactions records to the record stream. They must be in exact consensus time order! This must only be called
     * after the user transaction has been committed to state and is 100% done. It must include the record of the user transaction
     * along with all preceding child transactions and any child or system transactions after. IE. all transactions in the user
     * transactions 1000ns window.
     *
     * @param recordStreamItems Stream of records produced while handling the user transaction
     * @param state The hedera state to read block info from
     */
    public void endUserTransaction(
            @NonNull final Stream<SingleTransactionRecord> recordStreamItems, @NonNull final HederaState state) {
        // check if we need to run event recovery before we can write any new records to stream
        if (!this.eventRecoveryCompleted) {
            // FUTURE create event recovery class and call it here
            // FUTURE should this be in startUserTransaction()?
            this.eventRecoveryCompleted = true;
        }
        // pass to record stream writer to handle
        streamFileProducer.writeRecordStreamItems(
                provisionalCurrentBlockNumber, provisionalCurrentBlockFirstTransactionTime, recordStreamItems);
    }

    /**
     * Called at the end of a round to make sure running hash and block information is up-to-date in state.
     *
     * @param hederaState The state to update
     */
    @Override
    public void endRound(HederaState hederaState) {
        // We get the latest running hash from the StreamFileProducer blocking if needed
        // for it to be computed.
        final Bytes currentRunningHash = streamFileProducer.getRunningHash();
        // update running hashes in state with the latest running hash and the previous 3
        // running hashes.
        final WritableStates states = hederaState.createWritableStates(BlockRecordService.NAME);
        final WritableSingletonState<RunningHashes> runningHashesState =
                states.getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY);
        final RunningHashes existingRunningHashes = runningHashesState.get();
        if (existingRunningHashes == null) {
            runningHashesState.put(new RunningHashes(currentRunningHash, null, null, null));
        } else if (currentRunningHash != null
                && !currentRunningHash.equals(
                        existingRunningHashes
                                .runningHash())) { // only update if running hash has changed and is not null
            runningHashesState.put(new RunningHashes(
                    currentRunningHash,
                    existingRunningHashes.runningHash(),
                    existingRunningHashes.nMinus1RunningHash(),
                    existingRunningHashes.nMinus2RunningHash()));
        }
    }

    // ========================================================================================================
    // Running Hash Getter Methods

    /**
     * Get the runningHash of all RecordStreamObject. This will block if the running hash has not yet
     * been computed for the most recent user transaction.
     *
     * @return the runningHash of all RecordStreamObject, or null if there are no running hashes yet
     */
    @Nullable
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
    // Block Info Getter Methods

    /**
     * Get the last block number, this is the last completed immutable block.
     *
     * @return the current block number, 0 of there is no blocks yet
     */
    @Override
    public long lastBlockNo() {
        final var hederaState = workingStateAccessor.getHederaState();
        if (hederaState == null) {
            throw new RuntimeException("HederaState is null. This can only happen very early during bootstrapping");
        }
        final var states = hederaState.createReadableStates(BlockRecordService.NAME);
        final ReadableSingletonState<BlockInfo> blockInfoState =
                states.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY);
        final BlockInfo blockInfo = blockInfoState.get();
        if (blockInfo != null) {
            return blockInfo.lastBlockNo();
        } else {
            return 0;
        }
    }

    /**
     * Get the consensus time of the first transaction of the last block, this is the last completed immutable block.
     *
     * @return the consensus time of the first transaction of the last block, null if there was no previous block
     */
    @Nullable
    @Override
    public Instant firstConsTimeOfLastBlock() {
        final var hederaState = workingStateAccessor.getHederaState();
        if (hederaState == null) {
            throw new RuntimeException("HederaState is null. This can only happen very early during bootstrapping");
        }
        final var states = hederaState.createReadableStates(BlockRecordService.NAME);
        final ReadableSingletonState<BlockInfo> blockInfoState =
                states.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY);
        final BlockInfo blockInfo = blockInfoState.get();
        if (blockInfo != null) {
            final Timestamp firstConsTimeOfLastBlock = blockInfo.firstConsTimeOfLastBlock();
            if (firstConsTimeOfLastBlock != null) {
                return Instant.ofEpochSecond(firstConsTimeOfLastBlock.seconds(), firstConsTimeOfLastBlock.nanos());
            }
        }
        return null;
    }

    /**
     * Gets the hash of the last block
     *
     * @return the last block hash, null if no blocks have been created
     */
    @Nullable
    @Override
    public Bytes lastBlockHash() {
        final var hederaState = workingStateAccessor.getHederaState();
        if (hederaState == null) {
            throw new RuntimeException("HederaState is null. This can only happen very early during bootstrapping");
        }
        final var states = hederaState.createReadableStates(BlockRecordService.NAME);
        final ReadableSingletonState<BlockInfo> blockInfoState =
                states.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY);
        return getLastBlockHash(blockInfoState.get());
    }

    /**
     * Returns the hash of the given block number, or {@code null} if unavailable.
     *
     * @param blockNo the block number of interest, must be within range of (current_block - 1) -> (current_block - 254)
     * @return its hash, if available otherwise null
     */
    @Nullable
    @Override
    public Bytes blockHashByBlockNumber(long blockNo) {
        // get block
        final var hederaState = workingStateAccessor.getHederaState();
        if (hederaState == null) {
            throw new RuntimeException("HederaState is null. This can only happen very early during bootstrapping");
        }
        final var states = hederaState.createReadableStates(BlockRecordService.NAME);
        final ReadableSingletonState<BlockInfo> blockInfoState =
                states.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY);
        final BlockInfo blockInfo = blockInfoState.get();
        if (blockInfo == null) {
            throw new RuntimeException("HederaState BlockInfo is null. This can only happen very early during genesis");
        }
        final Bytes blockHashes = blockInfo.blockHashes();
        final long blocksAvailable = blockHashes.length() / HASH_SIZE;
        if (blockNo < 0) {
            return null;
        }
        final long lastBlockNo = blockInfo.lastBlockNo();
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
     * Get the block period from consensus timestamp. Based on {@link LinkedObjectStreamUtilities#getPeriod(Instant, long)}
     * but updated to work on {@link Instant}.
     *
     * @param consensusTimestamp The consensus timestamp
     * @return The block period from epoc the consensus timestamp is in
     */
    private long getBlockPeriod(Instant consensusTimestamp) {
        if (consensusTimestamp == null) return 0;
        long nanos = consensusTimestamp.getEpochSecond() * 1000000000L + (long) consensusTimestamp.getNano();
        return nanos / 1000000L / blockPeriodInMilliSeconds;
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
