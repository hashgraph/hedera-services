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

package com.hedera.node.app.workflows.handle.record;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.spi.records.OngoingBlockInfo;
import com.hedera.node.app.spi.workflows.record.SingleTransaction;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * {@link StreamManager} is responsible for managing the ongoing blockchain data, and for writing the output stream of objects. It manages:
 * <ul>
 *     <li>Packaging transactions and results into bytes, and sending for writing</li>
 *     <li>Updating block number</li>
 * </ul>
 *
 * <p>This API is used exclusively by {@link com.hedera.node.app.workflows.handle.HandleWorkflow}
 *
 * <p>This is {@link AutoCloseable} so it can wait for all inflight threads to finish and leave things in
 * a good state.
 */
public interface StreamManager extends OngoingBlockInfo, AutoCloseable {

    /**
     * "Advances the consensus clock" by updating the latest consensus timestamp that the node has handled. This should
     * be called early on in the transaction handling process in order to avoid assigning the same consensus timestamp
     * to multiple transactions.
     * @param consensusTime the most recent consensus timestamp that the node has <b>started</b> to handle
     */
    void advanceConsensusClock(@NonNull Instant consensusTime, @NonNull HederaState state);

    /**
     * Inform {@link StreamManager} of the new consensus time at the beginning of a new transaction. This should
     * only be called before <b>user transactions</b> because the workflow knows 100% that there can not be ANY user
     * transactions that proceed this one in consensus time.
     *
     * <p>This allows {@link StreamManager} to set up the correct block information for the user transaction that
     * is about to be executed. So block questions are answered correctly.
     *
     * <p>The BlockRecordManager may choose to close one or more files if the consensus time threshold has passed.
     *
     * @param consensusTime The consensus time of the user transaction we are about to start executing. It must be the
     *                      adjusted consensus time, not the platform assigned consensus time. Assuming the two are
     *                      different.
     * @param state         The state to read BlockInfo from and update when new blocks are created
     * @param platformState The platform state, which contains the last freeze time
     * @return               true if a new block was created, false otherwise
     */
    boolean startUserTransaction(
            @NonNull final Instant consensusTime,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState);

    /**
     * Add a user transaction's records to the record stream. They must be in exact consensus time order! This must only
     * be called after the user transaction has been committed to state and is 100% done. It must include the record of
     * the user transaction along with all preceding child transactions and any child or transactions after. System
     * transactions are treated as though they were user transactions, calling
     * {@link #startUserTransaction(Instant, HederaState, PlatformState)} and this method.
     *
     * @param singleTransactions Stream of records produced while handling the user transaction
     * @param state             The state to read {@link BlockInfo} from
     */
    void endUserTransaction(@NonNull Stream<SingleTransaction> singleTransactions, @NonNull HederaState state);

    /**
     * Called at the end of a round to make sure the running hash and block information is up-to-date in state.
     * This should be called <b>AFTER</b> the last end user transaction in that round has been passed to
     * {@link #endUserTransaction(Stream, HederaState)}.
     *
     * @param state The state to update
     */
    void endRound(@NonNull HederaState state);

    /**
     * Closes this manager and waits for any threads to finish.
     */
    @Override
    void close();

    /**
     * Notifies the stream manager that any startup migration transactions have been streamed.
     */
    void markMigrationTransactionsStreamed();

    /**
     * Get the consensus time of the latest handled transaction, or EPOCH if no transactions have been handled yet
     */
    @NonNull
    Instant consTimeOfLastHandledTxn();
}
