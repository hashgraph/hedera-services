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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final Logger log = LogManager.getLogger(RecordManager.class);
    private final QueueThread<SingleTransactionRecord> queueThread;

    /**
     * Construct RecordManager and start background thread
     */
    @Inject
    public RecordManager() {
        queueThread = new QueueThreadConfiguration<SingleTransactionRecord>(getStaticThreadManager())
                .setThreadName("RecordManager")
                .setExceptionHandler((t, e) -> log.error("Exception in RecordManager", e))
                .setHandler(this::handleTransactionRecord)
                .setCapacity(10)
                .build(true);
    }

    /**
     * Add a single transaction record to queue for recording into the record stream.
     *
     * @param singleTransactionRecord The record to record.
     */
    public void recordTransaction(SingleTransactionRecord singleTransactionRecord) {
        queueThread.add(singleTransactionRecord);
    }

    /**
     * Called on background thread to handle a single transaction record.
     *
     * @param singleTransactionRecord The record to handle.
     */
    private void handleTransactionRecord(SingleTransactionRecord singleTransactionRecord) {
        log.info("RecordManager received transaction record: {}", singleTransactionRecord);
    }
}
