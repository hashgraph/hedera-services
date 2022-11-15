package com.hedera.node.app.grpc;

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.swirlds.common.metrics.Metrics;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Handles gRPC duties for processing {@link com.hederahashgraph.api.proto.java.Transaction} gRPC calls. A single
 * instance of this class is used by all transaction ingest threads in the node.
 */
@ThreadSafe
final class TransactionMethod extends MethodBase {
    /** The pipeline contains all the steps needed for handling the ingestion of a transaction. */
    private final IngestWorkflow workflow;

    /**
     *
     * @param serviceName a non-null reference to the service name
     * @param methodName a non-null reference to the method name
     * @param workflow a non-null {@link IngestWorkflow}
     */
    TransactionMethod(
            @Nonnull final String serviceName,
            @Nonnull final String methodName,
            @Nonnull final IngestWorkflow workflow,
            @Nonnull final Metrics metrics) {
        super(serviceName, methodName, metrics);
        this.workflow = Objects.requireNonNull(workflow);
    }

    @Override
    protected void handle(
            @Nonnull SessionContext session,
            @Nonnull ByteBuffer requestBuffer,
            @Nonnull ByteBuffer responseBuffer) {
        workflow.handleTransaction(session, requestBuffer, responseBuffer);
    }
}
