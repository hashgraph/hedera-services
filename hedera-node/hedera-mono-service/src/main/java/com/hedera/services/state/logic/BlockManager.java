/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD;
import static com.hedera.services.state.merkle.MerkleNetworkContext.ethHashFrom;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.utility.Units;
import java.time.Instant;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the block-related fields in the {@link MerkleNetworkContext}, based on 2-second "periods"
 * in consensus time. Whenever a user transaction's consensus timestamp is the first in a new
 * period, starts a new block by recording the running hash of the last transaction in the current
 * period and incrementing the block number via a call to {@link
 * MerkleNetworkContext#finishBlock(org.hyperledger.besu.datatypes.Hash, Instant)}.
 */
@Singleton
public class BlockManager {
    private static final int UNKNOWN_BLOCK_NO = 0;
    private static final Logger log = LogManager.getLogger(BlockManager.class);

    private final long blockPeriodMs;
    private final boolean logEveryTransaction;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final Supplier<RecordsRunningHashLeaf> runningHashLeaf;

    // The block number for the current transaction; UNKNOWN_BLOCK_NO if not yet computed
    private long provisionalBlockNo = UNKNOWN_BLOCK_NO;
    // Whether the current transaction starts a new block; always false if not yet computed
    private boolean provisionalBlockIsNew = false;
    // The hash of the just-finished block if provisionalBlockIsNew == true; null otherwise
    @Nullable private org.hyperledger.besu.datatypes.Hash provisionalFinishedBlockHash;

    @Inject
    public BlockManager(
            final BootstrapProperties bootstrapProperties,
            final Supplier<MerkleNetworkContext> networkCtx,
            final Supplier<RecordsRunningHashLeaf> runningHashLeaf) {
        this.networkCtx = networkCtx;
        this.runningHashLeaf = runningHashLeaf;
        this.blockPeriodMs =
                bootstrapProperties.getLongProperty(HEDERA_RECORD_STREAM_LOG_PERIOD)
                        * Units.SECONDS_TO_MILLISECONDS;
        this.logEveryTransaction =
                bootstrapProperties.getBooleanProperty(HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION);
    }

    /** Clears all provisional block metadata for the current transaction. */
    public void reset() {
        provisionalBlockIsNew = false;
        provisionalBlockNo = UNKNOWN_BLOCK_NO;
        provisionalFinishedBlockHash = null;
    }

    /**
     * Provides the current block number in a form suitable for use in stream alignment. Only
     * different from {@code networkCtx.getBlockNo()} immediately after the 0.26 upgrade, before the
     * first block re-numbering transaction has been handled.
     *
     * @return the current block number
     */
    public long getAlignmentBlockNumber() {
        return networkCtx.get().getAlignmentBlockNo();
    }

    /**
     * Given the consensus timestamp of a user transaction, manages the block-related fields in the
     * {@link MerkleNetworkContext}, finishing the current block if appropriate.
     *
     * @param now the latest consensus timestamp of a user transaction
     * @return the new block number, taking this timestamp into account
     */
    public long updateAndGetAlignmentBlockNumber(@NotNull final Instant now) {
        ensureProvisionalBlockMeta(now);
        return provisionalBlockIsNew
                ? networkCtx.get().finishBlock(provisionalFinishedBlockHash, now)
                : provisionalBlockNo;
    }

    /**
     * Accepts the {@link RunningHash} that, <i>if</i> the corresponding user transaction is the
     * last in this 2-second period, will be the "block hash" for the current block.
     *
     * <p>Note the {@code runningHashLeaf} value is not synchronously fetched except at the start of
     * a new block; at that point, it contains the hash of the <i>just-finished</i> block.
     *
     * @param runningHash the latest candidate for the current block hash
     */
    public void updateCurrentBlockHash(final RunningHash runningHash) {
        runningHashLeaf.get().setRunningHash(runningHash);
    }

    /**
     * Returns the block metadata (hash, number, and timestamp) given the consensus time of the
     * active transaction and an applicable gas limit.
     *
     * @param now the consensus time of the active contract operation
     * @param gasLimit the gas limit of the operation
     * @return the block metadata for the operation
     */
    public HederaBlockValues computeBlockValues(@NotNull final Instant now, final long gasLimit) {
        ensureProvisionalBlockMeta(now);
        if (provisionalBlockIsNew) {
            return new HederaBlockValues(
                    gasLimit, provisionalBlockNo, Instant.ofEpochSecond(now.getEpochSecond()));
        } else {
            return new HederaBlockValues(
                    gasLimit, provisionalBlockNo, networkCtx.get().firstConsTimeOfCurrentBlock());
        }
    }

    /**
     * Returns the expected hash for the given block number. (If the hash for requested block number
     * was just computed in {@code computeProvisionalBlockMetadata()}---i.e., it is not yet in
     * state---we treat it as provisional, out of an abundance of caution.)
     *
     * @param blockNo a block number
     * @return the expected hash of that block
     */
    public org.hyperledger.besu.datatypes.Hash getBlockHash(final long blockNo) {
        assertProvisionalValuesAreComputed();
        // We don't update the network context state with the hash of a just-finished block until
        // right
        // before we stream the record that will cause mirror nodes to _also_ finish that block; so
        // we
        // need to handle this case ourselves---any other number we can delegate to the network
        // context
        if (provisionalBlockIsNew && blockNo == provisionalBlockNo - 1) {
            return provisionalFinishedBlockHash;
        }
        return networkCtx.get().getBlockHashByNumber(blockNo);
    }

    public boolean shouldLogEveryTransaction() {
        return this.logEveryTransaction;
    }

    // --- Internal helpers ---
    void ensureProvisionalBlockMeta(final Instant now) {
        if (provisionalBlockNo == UNKNOWN_BLOCK_NO) {
            computeProvisionalBlockMeta(now);
        }
    }

    private void computeProvisionalBlockMeta(final Instant now) {
        final var curNetworkCtx = networkCtx.get();
        provisionalBlockIsNew = willCreateNewBlock(now);
        if (provisionalBlockIsNew) {
            try {
                provisionalFinishedBlockHash =
                        ethHashFrom(runningHashLeaf.get().currentRunningHash());
            } catch (InterruptedException e) {
                provisionalBlockIsNew = false;
                // This is almost certainly fatal, hence the ERROR log level
                log.error(
                        "Interrupted when computing hash for block #{}",
                        curNetworkCtx::getAlignmentBlockNo);
                Thread.currentThread().interrupt();
            }
        }
        provisionalBlockNo = curNetworkCtx.getAlignmentBlockNo() + (provisionalBlockIsNew ? 1 : 0);
    }

    private boolean willCreateNewBlock(@NotNull final Instant timestamp) {
        final var curNetworkCtx = networkCtx.get();
        final var firstBlockTime = curNetworkCtx.firstConsTimeOfCurrentBlock();
        return firstBlockTime == null || !inSamePeriod(firstBlockTime, timestamp);
    }

    private boolean inSamePeriod(@NotNull final Instant then, @NotNull final Instant now) {
        return getPeriod(now, blockPeriodMs) == getPeriod(then, blockPeriodMs);
    }

    private void assertProvisionalValuesAreComputed() {
        if (provisionalBlockNo == UNKNOWN_BLOCK_NO) {
            throw new IllegalStateException(
                    "No block information is available until provisional values computed");
        }
    }
}
