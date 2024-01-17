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

package com.hedera.node.app.state.recordcache;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableQueueStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;

/**
 * A service representing this record cache facility. Many services are "big S" services like Hedera Consensus Service.
 * Facilities like Record Cache are "little s" services. They have state and schemas, but are not big ticket services
 * marketed to the world.
 */
public class RecordCacheService implements Service {
    /** The record cache service name */
    public static final String NAME = "RecordCache";
    /** The name of the queue that stores the transaction records */
    static final String TXN_RECORD_QUEUE = "TransactionRecordQueue";

    private List<ExpirableTxnRecord> fromRecs;

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    public void setFromState(List<ExpirableTxnRecord> fromRecs) {
        this.fromRecs = fromRecs;
    }

    /** {@inheritDoc} */
    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry, final SemanticVersion version) {
        // This is the genesis schema for this service, and simply creates the queue state that stores the
        // transaction records.
        // We intentionally ignore the given (i.e. passed-in) version in this method
        registry.register(new Schema(RELEASE_045_VERSION) {
            @NonNull
            @Override
            public SemanticVersion getVersion() {
                return super.getVersion();
            }

            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                return Set.of(StateDefinition.queue(TXN_RECORD_QUEUE, TransactionRecordEntry.PROTOBUF));
            }
        });

        registry.register(new Schema(RELEASE_MIGRATION_VERSION) {
            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                if (fromRecs != null) {
                    System.out.println("BBM: running expirable record (cache) migration...");

                    var toState = ctx.newStates().<TransactionRecordEntry>getQueue(TXN_RECORD_QUEUE);

                    for (ExpirableTxnRecord fromRec : fromRecs) {
                        var fromTxnId = fromRec.getTxnId();
                        var fromTransactionValidStart = fromTxnId.getValidStart();

                        // Note: fromRec.getExpiry() isn't needed because RecordCacheImpl uses its own mechanism to
                        // expire
                        // its entries
                        var toTxnValidStart = Timestamp.newBuilder()
                                .seconds(fromTransactionValidStart.getSeconds())
                                .nanos(fromTransactionValidStart.getNanos());
                        var toTxnId = TransactionID.newBuilder()
                                .accountID(fromTxnId.getPayerAccount().toPbjAccountId())
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

                    fromRecs = null;

                    System.out.println("BBM: finished expirable record (cache) migration");
                }
            }
        });
    }
}
