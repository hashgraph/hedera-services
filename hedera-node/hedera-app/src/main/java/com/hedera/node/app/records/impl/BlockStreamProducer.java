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

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.stream.Stream;

/**
 * Produces a stream of blocks. This is used by the {@link BlockStreamManagerImpl}
 */
public interface BlockStreamProducer extends AutoCloseable {

    /**
     * Write a stream of Block Items to the block stream.
     *
     * @param blockItems The record stream items to write.
     */
    void writeBlockItems(@NonNull final Stream<BlockItem> blockItems);

    /**
     * Initialize the current running hash of record stream items. This is called only once to initialize the running
     * hash on startup. This is called on the handle transaction thread. It is loaded from a saved state. At genesis,
     * there is no saved state, so this will be called with null.
     *
     * @param runningHashes The initial running hashes, all future running hashes produced will be
     *                                    computed based on this hash.
     * @param lastBlockNumber The last block number that was committed to state.
     */
    void initFromLastBlock(@NonNull final RunningHashes runningHashes, final long lastBlockNumber);

    /**
     * Get the current running hash of block items. This is called on the handle transaction thread. It will
     * block if a background thread is still hashing. It will always return the running hash after the last block item
     * is added. Hence, any block items not yet committed via write methods called before
     * {@link BlockStreamProducer#endBlock} will not be included.
     *
     * @return The current running hash upto and including the last record stream item sent in writeRecordStreamItems().
     */
    @NonNull
    Bytes getRunningHash();

    /**
     * Get the previous, previous, previous runningHash of all RecordStreamObjects. This will block if
     * the running hash has not yet been computed for the most recent user transaction.
     *
     * @return the previous, previous, previous runningHash of all RecordStreamObject
     */
    @Nullable
    Bytes getNMinus3RunningHash();

    /**
     * Called at the beginning of a block.
     *
     * <p>Begins a new block. It is an error to begin a new block when the previous block has not been closed.
     */
    void beginBlock();

    /**
     * Called to close the current open block.
     */
    void endBlock();

    void writeSystemTransaction(ConsensusTransaction platformTxn);
}
