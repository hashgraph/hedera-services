package com.hedera.services.utils.forensics;

import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.time.Instant;

public record RecordStreamEntry(TxnAccessor accessor, TransactionRecord txnRecord, Instant consensusTime) {
    public ResponseCodeEnum finalStatus() {
        return txnRecord.getReceipt().getStatus();
    }
}
