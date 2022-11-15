package com.hedera.node.app;

import com.hedera.node.app.grpc.GrpcServiceBuilder;
import com.hedera.node.app.workflows.ingest.IngestWorkflowImpl;
import com.hedera.node.app.workflows.query.QueryWorkflowImpl;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

public final class Hedera {
    public static void main(String[] args) throws InterruptedException {
        final var shutdownLatch = new CountDownLatch(1);

        final var ingestWorkflow = new IngestWorkflowImpl();
        final var queryWorkflow = new QueryWorkflowImpl();

        final var metrics = createMetrics();

        // Setup and start the server
        final var grpcServer = GrpcServer.create(
                GrpcServerConfiguration.builder()
                        .port(8080)
                        .build(),
                GrpcRouting.builder()
                        .register(new GrpcServiceBuilder("proto.ConsensusService", ingestWorkflow, queryWorkflow)
                                .transaction("createTopic")
                                .transaction("updateTopic")
                                .transaction("deleteTopic")
                                .query("getTopicInfo")
                                .transaction("submitMessage")
                                .build(metrics))
                        .build());
        grpcServer.whenShutdown().thenAccept(server -> shutdownLatch.countDown());
        grpcServer.start();

        // Block this main thread until the server terminates.
        shutdownLatch.await();
    }

    private static Metrics createMetrics() {
        // This is a stub implementation, to be replaced by a real implementation in #4293
        final var metricService = Executors.newSingleThreadScheduledExecutor(
                getStaticThreadManager().createThreadFactory("metrics", "MetricsWriter"));
        return new DefaultMetrics(metricService, new DefaultMetricsFactory());
    }
}
