package com.hedera.node.app.workflows.ingest;

import com.hedera.node.app.SessionContext;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

/**
 * Dummy implementation. To be implemented by
 * <a href="https://github.com/hashgraph/hedera-services/issues/4209">#4209</a>.
 */
public final class IngestWorkflowImpl implements IngestWorkflow {
    @Override
    public void handleTransaction(
            @Nonnull final SessionContext session,
            @Nonnull final ByteBuffer requestBuffer,
            @Nonnull final ByteBuffer responseBuffer) {
        // Implementation to be completed by Issue #4209
    }
}
