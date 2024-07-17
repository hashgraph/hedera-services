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

package com.hedera.node.app.spi.records;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Interface for reading recent block information.
 */
public interface OngoingBlockInfo {

    // ========================================================================================================
    // Running Hash Methods

    /**
     * // todo: is this still true?
     * Get the runningHash of all objects. This will block if the running hash has not yet
     * been computed for the most recent user transaction.
     *
     * @return the runningHash of all blocks, or null if there are no running hashes yet
     */
    @Nullable
    Bytes getRunningHash();

    /**
     * Get the previous, previous, previous runningHash of all RecordStreamObject. This will block if
     * the running hash has not yet been computed for the most recent user transaction.
     *
     * @return the previous, previous, previous runningHash of all RecordStreamObject
     */
    @Nullable
    Bytes getNMinus3RunningHash();

    // ========================================================================================================
    // Block Methods

    /**
     * Get the last block number, this is the last completed immutable block.
     *
     * @return the most recent immutable block number, 0 of there is no blocks yet, since block numbers start with 1.
     */
    long lastBlockNo();

    /**
     * Get the number of the current block.
     *
     * @return the number of the current block
     */
    default long blockNo() {
        return lastBlockNo() + 1;
    }

    /**
     * Get the consensus time of the first transaction of the last block, this is the last completed immutable block.
     *
     * @return the consensus time of the first transaction of the last block, null if there was no previous block
     */
    @Nullable
    Instant firstConsTimeOfLastBlock();

    /**
     * The current block timestamp. Its seconds is the value returned by {@code block.timestamp} for a contract
     * executing * in this block).
     *
     * @return the current block timestamp
     */
    @NonNull
    Timestamp currentBlockTimestamp();

    /**
     * Gets the hash of the last block
     *
     * @return the last block hash, null if no blocks have been created
     */
    @Nullable
    Bytes lastBlockHash();

    /**
     * Returns the hash of the given block number, or {@code null} if unavailable.
     *
     * @param blockNo the block number of interest, must be within range of (current_block - 1) -> (current_block - 254)
     * @return its hash, if available otherwise null. If the {@code blockNo} is negative, then null is also returned.
     */
    @Nullable
    Bytes blockHashByBlockNumber(final long blockNo);
}
