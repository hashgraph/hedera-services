/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.records.impl.producers;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Produces a stream of blocks. This is used by the {@link BlockStreamManagerImpl}.
 *
 * <p>A BlockStreamProducer is only responsible for delegating serialization of data it's passed from write methods and
 *    computing running hashes from the serialized data.
 */
public interface BlockStreamProducer extends AutoCloseable {

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
     * Called to close the current open block. This may be called when an error occurs, and we need to clean up
     * resources or when we are not asynchronously closing the block via a {@link BlockEnder}.
     */
    void endBlock();

    /**
     * Write ConsensusEvent to the block stream. It must be in exact consensus time order! This must only be called
     * after any state updates have been committed and are 100% done. So, it is called exactly once per consensus event
     * in a round, before handling the transactions in the round. Each call is for a consensus event that preface a
     * complete set of transactions that represent system transactions or a single user transaction and its
     * pre-transactions and child-transactions.
     *
     * @param consensusEvent the Platform ConsensusEvent to write
     */
    void writeConsensusEvent(@NonNull final ConsensusEvent consensusEvent);

    /**
     * Write system transaction to the block stream. It must be in exact consensus time order! This must only be called
     * after any state updates have been committed and are 100% done. So, it is called exactly once per system
     * transaction in a round. Each call is for a handled system transaction.
     *
     * @param systemTxn the record stream items to write
     */
    void writeSystemTransaction(@NonNull final ConsensusTransaction systemTxn);

    /**
     * Write user transactions to the block stream. They must be in exact consensus time order! This must only be called
     * after the user transaction has been committed to state and is 100% done. So, it is called exactly once per user
     * transaction at the end, after it has been committed to state. Each call is for a complete set of transactions
     * that represent a single user transaction and its pre-transactions and child-transactions.
     *
     * @param items the user transaction items to write
     */
    void writeUserTransactionItems(@NonNull final ProcessUserTransactionResult items);

    /**
     * Write state changes to the block stream. Each call is for a complete set of transactions that represent a single
     * user transaction and its pre-transactions and child-transactions or each call may represent system transaction
     * state changes or state changes that happened outside a transaction occurring such as when doing a migration or
     * cleanup.
     *
     * @param stateChanges the StateChanges that are to be written to the block stream
     */
    void writeStateChanges(@NonNull final StateChanges stateChanges);
}


