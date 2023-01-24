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

/** Main class for the Hedera Consensus Node. */
public final class Hedera {
    public static void main(String[] args) throws InterruptedException {
        final var shutdownLatch = new CountDownLatch(1);

        // TODO: These need to be replaced with appropriate setup code
        final var ingestWorkflow =
                new IngestWorkflowImpl(null, null, null, null, null, null, null, null);
        final var queryWorkflow = new QueryWorkflowImpl();

        final var metrics = createMetrics();

        // Setup and start the server
        final var grpcServer =
                GrpcServer.create(
                        GrpcServerConfiguration.builder().port(8080).build(),
                        GrpcRouting.builder()
                                .register(
                                        new GrpcServiceBuilder(
                                                        "proto.ConsensusService",
                                                        ingestWorkflow,
                                                        queryWorkflow)
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
        final var metricService =
                Executors.newSingleThreadScheduledExecutor(
                        getStaticThreadManager().createThreadFactory("metrics", "MetricsWriter"));
        return new DefaultMetrics(metricService, new DefaultMetricsFactory());
    }
}
