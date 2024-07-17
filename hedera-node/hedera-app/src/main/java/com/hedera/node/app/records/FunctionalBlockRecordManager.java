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

package com.hedera.node.app.records;

import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.node.app.records.streams.ProcessUserTransactionResult;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.swirlds.state.HederaState;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.spi.info.NodeInfo;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * {@link FunctionalBlockRecordManager} is responsible for managing blocks and writing the block record stream. It manages:
 * <ul>
 *     <li>Packaging transaction records into files and sending for writing</li>
 *     <li>Updating block number</li>
 *     <li>Computing running hashes</li>
 *     <li>Updating State for blocks and running hashes</li>
 * </ul>
 *
 * <p>This API is used exclusively by {@link com.hedera.node.app.workflows.handle.HandleWorkflow}
 *
 * <p>This is {@link AutoCloseable} so it can wait for all inflight threads to finish and leave things in
 * a good state.
 *
 * <p>The {@link FunctionalBlockRecordManager} operates on the principle that the consensus time on user transactions
 * <b>ALWAYS</b> increase with time. Transaction TX_2 will always have a higher consensus time than TX_1, and
 * TX_3 will be higher than TX_2, even if TX_2 were to fail, or be a duplicate. Likewise, we know for certain that
 * the entire set of user transaction, preceding transactions, and child transactions of TX_1 will have a consensus
 * time that comes before every preceding, user, and child transaction of TX_2.
 *
 * <p>This property allows us to make some assumptions that radically simplify the API and implementation.
 *
 * <p>While we currently produce block records on a fixed-second boundary (for example, every 2 seconds), it is possible
 * that some transactions have a consensus time that lies outside that boundary. This is OK, because it is not possible
 * to take the consensus time of a transaction and map back to which block it came from. Blocks use auto-incrementing
 * numbers, and if the network were idle for the duration of a block, there may be no block generated for that slice
 * of time. Thus, since you cannot map consensus time to block number, it doesn't matter if some preceding transactions
 * may have a consensus time that lies outside the "typical" block boundary.
 */
public interface FunctionalBlockRecordManager extends BlockRecordManager, BlockRecordInfo, AutoCloseable {

    /**
     * Process a round of consensus events. This method is called by the
     * {@link com.hedera.node.app.workflows.handle.HandleWorkflow}. It is responsible for processing a round of
     * consensus events.
     *
     * <p>We may want to provide a callback to let the Platform know that a round has been completed and persisted to
     *    disk. To do that, we allow Platform to provide a promise to be fulfilled by the BlockStreamProducer.
     * @param state the state of the node
     * @param round the round to process
     * @param runnable the callback to run for processing the round
     * @param persistedBlock the promise to complete when the block is complete with the BlockProof.
     */
    void processRound(
            @NonNull HederaState state,
            @NonNull Round round,
            @NonNull CompletableFuture<BlockProof> persistedBlock,
            @NonNull Runnable runnable);

    void processUserTransaction(
            @NonNull Instant consensusTime,
            @NonNull HederaState state,
            @NonNull ConsensusTransaction platformTxn,
            @NonNull Supplier<ProcessUserTransactionResult> callable);

    void advanceConsensusClock(@NonNull Instant consensusTime, @NonNull HederaState state);

    @Override
    void close();

    void processConsensusEvent(
            @NonNull HederaState state, @NonNull ConsensusEvent platformEvent, @NonNull Runnable runnable);

    void processSystemTransaction(
            @NonNull HederaState state,
            @NonNull NodeInfo creator,
            @NonNull ConsensusTransaction systemTxn,
            @NonNull Runnable runnable);
}
