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

import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.HASH_SIZE;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.workflows.handle.record.StreamManager;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.spi.workflows.record.SingleTransaction;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.WritableSingletonStateBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An implementation of {@link StreamManager} primarily responsible for managing state ({@link RunningHashes} and
 * {@link BlockInfo}), and delegating to a {@link StreamProducer} for writing to the stream file, hashing,
 * and performing other duties, possibly on background threads. All APIs of {@link StreamManager} can only be
 * called from the "handle" thread!
 */
public abstract class AbstractBlockManagerImpl implements StreamManager {
    private static final Logger logger = LogManager.getLogger(AbstractBlockManagerImpl.class);

    /**
     * The number of blocks to keep multiplied by hash size. This is computed based on the
     * {@link BlockRecordStreamConfig#numOfBlockHashesInState()} setting multiplied by the size of each hash. This
     * setting is computed once at startup and used throughout.
     */
    protected final int numBlockHashesToKeepBytes;

    /**
     * A {@link BlockInfo} of the most recently completed block. This is actually available in state, but there
     * is no reason for us to read it from state every time we need it, we can just recompute and cache this every
     * time we finish a provisional block.
     */
    protected BlockInfo lastBlockInfo;

    /**
     * The {@link StreamProducer} responsible for writing the file outputs. This is set once during
     * startup, and used throughout the execution of the node. It would be nice to allow this to be
     * a dynamic property, but it just isn't convenient to do so at this time.
     */
    protected final StreamProducer streamFileProducer;

    /**
     * True when we have completed event recovery. This is not yet implemented properly.
     */
    private boolean eventRecoveryCompleted = false;

    /**
     * Construct BlockRecordManager
     *
     * @param configProvider The configuration provider
     * @param state The current hedera state
     * @param streamFileProducer The instance responsible for writing outputs
     */
    public AbstractBlockManagerImpl(
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaState state,
            @NonNull final StreamProducer streamFileProducer) {

        requireNonNull(state);
        requireNonNull(configProvider);
        this.streamFileProducer = requireNonNull(streamFileProducer);

        final var streamConfig = configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        this.numBlockHashesToKeepBytes = streamConfig.numOfBlockHashesInState() * HASH_SIZE;

        // FUTURE: check if we were started in event recover mode and if event recovery needs to be completed before we
        // write any new records to stream
        this.eventRecoveryCompleted = false;

        // Initialize the last block info and provisional block info.
        // NOTE: State migration happens BEFORE dagger initialization, and this object is managed by dagger. So we are
        // guaranteed that the state exists PRIOR to this call.
        final var states = state.getReadableStates(BlockRecordService.NAME);
        final var blockInfoState = states.<BlockInfo>getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY);
        this.lastBlockInfo = blockInfoState.get();
        assert this.lastBlockInfo != null : "Cannot be null, because this state is created at genesis";
        this.streamFileProducer.initRunningHash(
                states.<RunningHashes>getSingleton(V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY).get());

        // Initialize the stream file producer. NOTE, if the producer cannot be initialized, and a random exception is
        // thrown here, then startup of the node will fail. This is the intended behavior. We MUST be able to produce
        // record streams, or there really is no point to running the node!
        final var runningHashState =
                states.<RunningHashes>getSingleton(V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY);
        final var lastRunningHashes = runningHashState.get();
        assert lastRunningHashes != null : "Cannot be null, because this state is created at genesis";
        this.streamFileProducer.initRunningHash(lastRunningHashes);
    }

    protected StreamProducer streamProducer() {
        return streamFileProducer;
    }

    // =================================================================================================================
    // AutoCloseable implementation

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    public abstract boolean startUserTransaction(
            @NonNull final Instant consensusTime,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState);

    @Override
    public void markMigrationTransactionsStreamed() {
        lastBlockInfo =
                lastBlockInfo.copyBuilder().migrationRecordsStreamed(true).build();
    }

    protected void putLastBlockInfo(@NonNull final HederaState state) {
        final var states = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = states.<BlockInfo>getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY);
        blockInfoState.put(lastBlockInfo);
    }

    /**
     * {@inheritDoc}
     */
    public void endUserTransaction(
            @NonNull final Stream<SingleTransaction> streamItems, @NonNull final HederaState state) {
        // check if we need to run event recovery before we can write any new records to stream
        if (!this.eventRecoveryCompleted) {
            // FUTURE create event recovery class and call it here. Should this be in startUserTransaction()?
            this.eventRecoveryCompleted = true;
        }

        // todo: implement remaining functionality

        // pass to record stream writer to handle
        streamFileProducer.writeStreamItems(streamItems);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endRound(@NonNull final HederaState state) {
        // We get the latest running hash from the StreamFileProducer blocking if needed for it to be computed.
        final var currentRunningHash = streamFileProducer.getRunningHash();
        // Update running hashes in state with the latest running hash and the previous 3 running hashes.
        final var states = state.getWritableStates(BlockRecordService.NAME);
        final var runningHashesState =
                states.<RunningHashes>getSingleton(V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY);
        final var existingRunningHashes = runningHashesState.get();
        assert existingRunningHashes != null : "This cannot be null because genesis migration sets it";
        runningHashesState.put(new RunningHashes(
                currentRunningHash,
                existingRunningHashes.nMinus1RunningHash(),
                existingRunningHashes.nMinus2RunningHash(),
                existingRunningHashes.nMinus3RunningHash()));
        // Commit the changes to the merkle tree.
        ((WritableSingletonStateBase<RunningHashes>) runningHashesState).commit();
    }

    // ========================================================================================================
    // Running Hash Getter Methods

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bytes getRunningHash() {
        return streamFileProducer.getRunningHash();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    public Bytes getNMinus3RunningHash() {
        return streamFileProducer.getNMinus3RunningHash();
    }

    // ========================================================================================================
    // OngoingBlockInfo Implementation

    /**
     * {@inheritDoc}
     */
    @Override
    public long lastBlockNo() {
        return lastBlockInfo.lastBlockNumber();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Instant firstConsTimeOfLastBlock() {
        return BlockRecordInfoUtils.firstConsTimeOfLastBlock(lastBlockInfo);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Instant consTimeOfLastHandledTxn() {
        final var lastHandledTxn = lastBlockInfo.consTimeOfLastHandledTxn();
        return lastHandledTxn != null
                ? Instant.ofEpochSecond(lastHandledTxn.seconds(), lastHandledTxn.nanos())
                : Instant.EPOCH;
    }

    @Override
    public @NonNull Timestamp currentBlockTimestamp() {
        return lastBlockInfo.firstConsTimeOfCurrentBlockOrThrow();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract Bytes lastBlockHash();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    abstract public Bytes blockHashByBlockNumber(final long blockNo);

    /**
     * {@inheritDoc}
     */
    @Override
    public void advanceConsensusClock(@NonNull final Instant consensusTime, @NonNull final HederaState state) {
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
}
