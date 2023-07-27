package com.hedera.node.app.service.token.records;

import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface ChildRecordFinalizer {
    void finalizeChildRecord(@NonNull final HandleContext context);
}
