package com.hedera.node.app.service.token.records;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

public interface ParentRecordFinalizer {
    void finalizeParentRecord(@NonNull HandleContext context, List<SingleTransactionRecordBuilder> childRecords);
}
