package com.hedera.node.app.state.merkle;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.state.RecordCache;

public class MerkleRecordCache implements RecordCache {
    @Override
    public boolean isReceiptPresent(TransactionID txnId) {
        // TODO Delegate to the mono repo RecordCache
        return false;
    }

    @Override
    public void addPreConsensus(TransactionID transactionID) {
        // TODO Delegate to the mono repo RecordCache
    }
}
