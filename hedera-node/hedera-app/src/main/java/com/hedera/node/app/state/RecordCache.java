package com.hedera.node.app.state;

import com.hedera.hapi.node.base.TransactionID;

public interface RecordCache {
    boolean isReceiptPresent(TransactionID txnId);

    void addPreConsensus(TransactionID transactionID);
}
