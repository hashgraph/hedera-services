package com.hedera.node.app.workflows.query;

import com.hedera.node.app.SessionContext;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

/**
 * Dummy implementation. To be implemented by
 * <a href="https://github.com/hashgraph/hedera-services/issues/4208">#4208</a>.
 */
public final class QueryWorkflowImpl implements QueryWorkflow {
    @Override
    public void handleQuery(
            @Nonnull final SessionContext session,
            @Nonnull final ByteBuffer requestBuffer,
            @Nonnull final ByteBuffer responseBuffer) {
        // To be implemented by Issue #4208
    }
}
