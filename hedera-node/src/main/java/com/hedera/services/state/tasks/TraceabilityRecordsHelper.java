package com.hedera.services.state.tasks;

import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.logic.RecordStreaming;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.TransactionSidecarRecord;

import javax.inject.Singleton;
import java.util.List;

import static com.hedera.services.records.TxnAwareRecordsHistorian.timestampSidecars;
import static com.hedera.services.state.expiry.ExpiryRecordsHelper.baseRecordWith;
import static com.hedera.services.state.expiry.ExpiryRecordsHelper.finalizeAndStream;
import static com.hedera.services.utils.EntityNum.fromLong;

@Singleton
public class TraceabilityRecordsHelper {
    private final RecordStreaming recordStreaming;
    private final RecordsHistorian recordsHistorian;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final ConsensusTimeTracker consensusTimeTracker;

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
            final long contractNum,
            final List<TransactionSidecarRecord.Builder> sidecars) {
        final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();

        final var txnId = recordsHistorian.computeNextSystemTransactionId();
        final var memo = "Traceability export for contract 0.0." + contractNum;
        final var expirableTxnRecord = baseRecordWith(eventTime, txnId).setMemo(memo);
        final var synthBody = syntheticTxnFactory.synthNoopContractUpdate(fromLong(contractNum));

        timestampSidecars(sidecars, eventTime);
        finalizeAndStream(expirableTxnRecord, synthBody, eventTime, recordStreaming, sidecars);
    }
}
