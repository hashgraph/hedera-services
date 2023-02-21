/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.node.app.grpc.GrpcServiceBuilder;
import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.workflows.ingest.IngestWorkflowImpl;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/** Main class for the Hedera Consensus Node. */
public final class Hedera {
    private static final int MAX_SIGNED_TXN_SIZE = 6144;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public Hedera() {}

    public void start(ServicesApp app, int port) {
        final var metrics = createMetrics(app.nodeId());

        // Create the Ingest workflow. While we are in transition, some required facilities come
        // from `hedera-app`, and some from `mono-service`. Eventually we'll transition all
        // facilities to be from the app module.
        // TODO Real values will be added to make this usable with #4825
        final var ingestWorkflow =
                new IngestWorkflowImpl(app.nodeInfo(), app.platformStatus(), null, null, null, null, null, null);

        // Create the query workflow; fully qualified import to appease javadoc Gradle task
        final var queryWorkflow = com.hedera.node.app.components.DaggerQueryComponent.factory()
                .create(app.bootstrapProps(), MAX_SIGNED_TXN_SIZE, app.platform())
                .queryWorkflow();

        // Setup and start the grpc server.
        // At some point I'd like to somehow move the metadata for which transactions are supported
        // by a service to the service, instead of having them all hardcoded here. It isn't clear
        // yet what that API would look like, so for now we do it this way. Maybe we should have
        // a set of annotations that generate the metadata, or maybe we have some code. Whatever
        // we do should work also with workflows.
        final var grpcServer = GrpcServer.create(
                GrpcServerConfiguration.builder().port(port).build(),
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
        try {
            shutdownLatch.await();
        } catch (InterruptedException ignored) {
            // An interrupt on this thread means we want to shut down the server.
            shutdown();
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        shutdownLatch.countDown();
    }

    private static Metrics createMetrics(NodeId nodeId) {
        // This is a stub implementation, to be replaced by a real implementation in #4293
        final var metricService = Executors.newSingleThreadScheduledExecutor(
                getStaticThreadManager().createThreadFactory("metrics", "MetricsWriter"));
        return new DefaultMetrics(nodeId, new MetricKeyRegistry(), metricService, new DefaultMetricsFactory());
    }
}
