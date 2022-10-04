/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.tasks;

import static com.hedera.services.records.TxnAwareRecordsHistorian.timestampSidecars;
import static com.hedera.services.state.expiry.ExpiryRecordsHelper.baseRecordWith;
import static com.hedera.services.state.expiry.ExpiryRecordsHelper.finalizeAndStream;
import static com.hedera.services.utils.EntityNum.fromLong;

import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.logic.RecordStreaming;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TraceabilityRecordsHelper {
    private final RecordStreaming recordStreaming;
    private final RecordsHistorian recordsHistorian;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final ConsensusTimeTracker consensusTimeTracker;

    @Inject
    public TraceabilityRecordsHelper(
            final RecordStreaming recordStreaming,
            final RecordsHistorian recordsHistorian,
            final SyntheticTxnFactory syntheticTxnFactory,
            final ConsensusTimeTracker consensusTimeTracker) {
        this.recordStreaming = recordStreaming;
        this.recordsHistorian = recordsHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.consensusTimeTracker = consensusTimeTracker;
    }

    public boolean canExportNow() {
        return consensusTimeTracker.hasMoreStandaloneRecordTime();
    }

    public void exportSidecarsViaSynthUpdate(
            final long contractNum, final List<TransactionSidecarRecord.Builder> sidecars) {
        final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();
        final var txnId = recordsHistorian.computeNextSystemTransactionId();
        final var memo = "Traceability export for contract 0.0." + contractNum;

        final var expirableTxnRecord = baseRecordWith(eventTime, txnId).setMemo(memo);
        final var synthBody = syntheticTxnFactory.synthNoopContractUpdate(fromLong(contractNum));

        timestampSidecars(sidecars, eventTime);
        finalizeAndStream(expirableTxnRecord, synthBody, eventTime, recordStreaming, sidecars);
    }
}
