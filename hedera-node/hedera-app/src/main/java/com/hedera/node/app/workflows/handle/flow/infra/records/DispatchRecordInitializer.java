package com.hedera.node.app.workflows.handle.flow.infra.records;

import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;

public interface DispatchRecordInitializer {

    void initializeUserRecord(SingleTransactionRecordBuilderImpl recordBuilder, TransactionInfo txnInfo);

}
