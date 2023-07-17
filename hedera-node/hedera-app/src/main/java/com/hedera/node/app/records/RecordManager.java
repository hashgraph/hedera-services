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

import com.hedera.node.app.spi.records.SingleTransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * RecordManager is a singleton facility that records transaction records into the record stream. It is responsible for:
 *
 * <ul>
 *     <li>Packages transaction records into files and sending for writing</li>
 *     <li>Manages block number</li>
 *     <li>Manages Running Hashes</li>
 *     <li>Manages Record State</li>
 * </ul>
 */
@Singleton
public class RecordManager {

    @Inject
    public RecordManager() {}

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
     */
    public void startUserTransaction(Instant consensusTime) {}

    /**
     * Add a user transactions records to the record stream. They must be in exact consensus time order! This must only be called
     * after the user transaction has been committed to state and is 100% done.
     *
     * @param recordStreamItems Stream of records produced while handling the user transaction
     */
    public void endUserTransaction(@NonNull final Stream<SingleTransactionRecord> recordStreamItems) {}
}
