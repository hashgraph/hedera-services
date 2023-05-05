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

import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Interface for BlockRecordManager which is responsible for managing blocks and writing
 * the record file stream. It manages:
 * <ul>
 *     <li>Packaging transaction records into files and sending for writing</li>
 *     <li>Updating block number</li>
 *     <li>Computing running hashes</li>
 *     <li>Updating State for blocks and running hashes</li>
 * </ul>
 * This API is used exclusively by {@link com.hedera.node.app.workflows.handle.HandleWorkflow}
 *
 * <p>This is closeable so it can wait for all inflight threads to finish and leave things in
 * a good state.</p>
 */
public interface BlockRecordManager extends BlockRecordInfo, AutoCloseable {

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
     * @param state         The state to read BlockInfo from and update when new blocks are created
     */
    void startUserTransaction(Instant consensusTime, HederaState state);

    /**
     * Add a user transactions records to the record stream. They must be in exact consensus time order! This must only be called
     * after the user transaction has been committed to state and is 100% done. It must include the record of the user transaction
     * along with all preceding child transactions and any child or system transactions after. IE. all transactions in the user
     * transactions 1000ns window.
     *
     * @param recordStreamItems Stream of records produced while handling the user transaction
     * @param state             The state to read BlockInfo from
     */
    void endUserTransaction(@NonNull final Stream<SingleTransactionRecord> recordStreamItems, HederaState state);

    /**
     * Called at the end of a round to make sure running hash and block information is up-to-date in state.
     *
     * @param state The state to update
     */
    void endRound(HederaState state);
}
