package com.hedera.node.app.workflows.query;

import com.hedera.node.app.SessionContext;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

/**
 * A workflow for processing queries.
 */
public interface QueryWorkflow {
    void handleQuery(
            @Nonnull SessionContext session,
            @Nonnull ByteBuffer requestBuffer,
            @Nonnull ByteBuffer responseBuffer);
}
