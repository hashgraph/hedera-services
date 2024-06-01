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

package com.hedera.node.app.state.recordcache.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.swirlds.platform.state.spi.WritableQueueStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V0490RecordCacheSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V0490RecordCacheSchema.class);

    /** The name of the queue that stores the transaction records */
    public static final String TXN_RECORD_QUEUE = "TransactionRecordQueue";
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    private static List<ExpirableTxnRecord> fromRecs;

    public V0490RecordCacheSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.queue(V0490RecordCacheSchema.TXN_RECORD_QUEUE, TransactionRecordEntry.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        if (fromRecs != null) {
            log.info("BBM: running expirable record (cache) migration...");

            var toState = ctx.newStates().<TransactionRecordEntry>getQueue(V0490RecordCacheSchema.TXN_RECORD_QUEUE);

            for (ExpirableTxnRecord fromRec : fromRecs) {
                var fromTxnId = fromRec.getTxnId();
                var fromTransactionValidStart = fromTxnId.getValidStart();

                // Note: fromRec.getExpiry() isn't needed because RecordCacheImpl uses its own mechanism to
                // expire its entries
                var toTxnValidStart = Timestamp.newBuilder()
                        .seconds(fromTransactionValidStart.getSeconds())
                        .nanos(fromTransactionValidStart.getNanos());
                var toTxnId = TransactionID.newBuilder()
                        .accountID(fromTxnId.getPayerAccount().toPbjAccountId())
                        .scheduled(fromTxnId.isScheduled())
                        .nonce(fromTxnId.getNonce())
                        .transactionValidStart(toTxnValidStart)
                        .build();
                var toConsensusTime = Timestamp.newBuilder()
                        .seconds(fromRec.getConsensusTime().getSeconds())
                        .nanos(fromRec.getConsensusTime().getNanos())
                        .build();
                var toRec = TransactionRecordEntry.newBuilder()
                        .transactionRecord(TransactionRecord.newBuilder()
                                .receipt(TransactionReceipt.newBuilder()
                                        .status(PbjConverter.toPbj(
                                                fromRec.getReceipt().getEnumStatus()))
                                        .build())
                                .consensusTimestamp(toConsensusTime)
                                .transactionID(toTxnId)
                                .build())
                        .nodeId(fromRec.getSubmittingMember())
                        .payerAccountId(fromTxnId.getPayerAccount().toPbjAccountId())
                        .build();
                toState.add(toRec);
            }
            ((WritableQueueStateBase) toState).commit();

            log.info("BBM: finished expirable record (cache) migration");
        } else {
            log.warn("BBM: no record cache 'from' state found");
        }

        fromRecs = null;
    }

    public static void setFromRecs(List<ExpirableTxnRecord> fromRecs) {
        V0490RecordCacheSchema.fromRecs = fromRecs;
    }
}
