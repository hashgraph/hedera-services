package com.hedera.node.app.workflows.ingest;

import com.hedera.node.app.SessionContext;
import com.hederahashgraph.api.proto.java.TransactionResponse;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

/**
 * An implementation of the ingestion pipeline. An implementation of this interface is threadsafe, a single
 * instance of it can be used to execute concurrent transaction ingestion.
 */
public interface IngestWorkflow {
    /**
     * Called to handle a single transaction during the ingestion flow. The call terminates in
     * a {@link TransactionResponse} being returned to the client (for both successful and unsuccessful calls).
     * There are no unhandled exceptions (even Throwable is handled).
     *
     * @param session The per-request {@link SessionContext}.
     * @param requestBuffer The raw protobuf transaction bytes. Must be a transaction object.
     * @param responseBuffer The raw protobuf response bytes.
     */
    void handleTransaction(
            @Nonnull SessionContext session,
            @Nonnull ByteBuffer requestBuffer,
            @Nonnull ByteBuffer responseBuffer);
}
