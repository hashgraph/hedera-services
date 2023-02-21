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

package grpc;

import com.hedera.node.app.grpc.GrpcServiceBuilder;
import com.hedera.node.app.spi.fixtures.TestBase;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import io.grpc.ManagedChannelBuilder;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.MethodDescriptor;
import io.helidon.grpc.server.ServiceDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;

/**
 * Base class for testing the gRPC handling engine. This implementation is not suitable for general
 * integration testing, but is tailored for testing the gRPC engine itself. Specifically, it does
 * not use real workflow implementations, but allows subclasses to mock them instead to test various
 * failure scenarios.
 *
 * <p>Our use of gRPC deals in bytes -- we do not ask the gRPC system to serialize and deserialize
 * our protobuf objects. Because of this, we *can* actually test using any type of byte[] payload
 * (including strings!) rather than protobuf objects.
 */
abstract class GrpcTestBase extends TestBase {
    /** Used as a dependency to the {@link Metrics} system. */
    private static final ScheduledExecutorService METRIC_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    /** A built-in {@link IngestWorkflow} which succeeds and does nothing. */
    protected static final IngestWorkflow NOOP_INGEST_WORKFLOW = (session, requestBuffer, responseBuffer) -> {};
    /** A built-in {@link QueryWorkflow} which succeeds and does nothing. */
    protected static final QueryWorkflow NOOP_QUERY_WORKFLOW = (session, requestBuffer, responseBuffer) -> {};

    /**
     * This {@link GrpcServer} is used to handle the wire protocol tasks and delegate to our gRPC
     * handlers
     */
    private GrpcServer grpcServer;

    /**
     * The {@link GrpcServiceClient}s to use for making different calls to the server. Each
     * different gRPC service has its own client. The key in this map is the service name.
     */
    private Map<String, GrpcServiceClient> clients = new HashMap<>();

    /**
     * The registered services. These must be created through {@link
     * #registerService(GrpcServiceBuilder)} <b>BEFORE</b> the server is started to take any effect.
     * These services will be registered on the server <b>AND</b> on the client.
     */
    private Set<ServiceDescriptor> services = new HashSet<>();

    /**
     * The set of services to be registered <b>ON THE CLIENT ONLY</b>. The server won't know about
     * these. This allows us to test cases where either the method or service is known to the client
     * but not known to the server.
     */
    private Set<ServiceDescriptor> clientOnlyServices = new HashSet<>();

    /**
     * Represents "this node" in our tests.
     */
    private final NodeId nodeSelfId = new NodeId(false, 7);

    /**
     * The gRPC system has extensive metrics. This object allows us to inspect them and make sure
     * they are being set correctly for different types of calls.
     */
    protected Metrics metrics =
            new DefaultMetrics(nodeSelfId, new MetricKeyRegistry(), METRIC_EXECUTOR, new DefaultMetricsFactory());

    /** The host of our gRPC server. */
    protected String host = "127.0.0.1";

    /** The port our server is running on. We use an ephemeral port, so it is dynamic */
    protected int port;

    /**
     * Registers the given service with this test server and client. This method must be called
     * before the server is started.
     *
     * @param builder builds the service
     */
    protected void registerService(GrpcServiceBuilder builder) {
        services.add(builder.build(metrics));
    }

    protected void registerServiceOnClientOnly(GrpcServiceBuilder builder) {
        clientOnlyServices.add(builder.build(metrics));
    }

    /** Starts the grpcServer and sets up the clients. */
    protected void startServer() {
        final var latch = new CountDownLatch(1);

        final var routingBuilder = GrpcRouting.builder();
        services.forEach(routingBuilder::register);
        grpcServer =
                GrpcServer.create(GrpcServerConfiguration.builder().port(port).build(), routingBuilder.build());

        grpcServer.start().thenAccept(server -> latch.countDown());

        // Block this main thread until the server starts
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assertions.fail("GRPC Server did not startup", e);
        }

        // Get the host and port dynamically now that the server is running.
        host = "127.0.0.1"; // InetAddress.getLocalHost().getHostName();
        port = grpcServer.port();

        final var channel =
                ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

        // Collect the full set of services and method descriptors for the client side
        //noinspection rawtypes
        final var clientDescriptors = new HashMap<String, Set<MethodDescriptor>>();
        services.forEach(s -> clientDescriptors.put(s.name(), new HashSet<>(s.methods())));
        clientOnlyServices.forEach(s -> {
            final var existingMethods = clientDescriptors.get(s.name());
            if (existingMethods == null) {
                clientDescriptors.put(s.name(), new HashSet<>(s.methods()));
            } else {
                existingMethods.addAll(s.methods());
            }
        });

        // Setup the client side
        clientDescriptors.forEach((serviceName, methods) -> {
            final var builder = io.grpc.ServiceDescriptor.newBuilder(serviceName);
            methods.forEach(method -> builder.addMethod(method.descriptor()));
            final var clientServiceDescriptor = builder.build();
            final var client = GrpcServiceClient.builder(
                            channel,
                            ClientServiceDescriptor.builder(clientServiceDescriptor)
                                    .build())
                    .build();

            clients.put(serviceName, client);
        });
    }

    @AfterEach
    void tearDown() {
        grpcServer.shutdown();
        clients.clear();
        services.clear();
    }

    /**
     * Called to invoke a service's function that had been previously registered with {@link
     * #registerService(GrpcServiceBuilder)}, using the given payload and receiving the given
     * response. Since the gRPC code only deals in bytes, we can test everything with just strings,
     * no protobuf encoding required.
     *
     * @param service The service to invoke
     * @param function The function on the service to invoke
     * @param payload The payload to send to the function on the service
     * @return The response from the service function.
     */
    protected String send(String service, String function, String payload) {
        final var client = clients.get(service);
        assert client != null;
        final var bb = DataBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8));
        final DataBuffer res = client.blockingUnary(function, bb);
        final var rb = new byte[(int) res.getRemaining()];
        res.readBytes(rb);
        return new String(rb, StandardCharsets.UTF_8);
    }
}
