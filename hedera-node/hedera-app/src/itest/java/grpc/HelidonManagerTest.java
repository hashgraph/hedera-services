/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.grpc.GrpcServiceBuilder;
import com.hedera.node.app.grpc.HelidonGrpcServerManager;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.config.data.GrpcConfig;
import com.hedera.node.config.data.NettyConfig;
import com.hedera.pbj.runtime.RpcMethodDefinition;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannelBuilder;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.assertj.core.api.Assumptions;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class HelidonManagerTest {
    private static final ScheduledExecutorService METRIC_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final NodeId THIS_NODE = new NodeId(3);

    private Metrics createMetrics(@NonNull final Configuration config) {
        final MetricsConfig metricsConfig = config.getConfigData(MetricsConfig.class);
        return new DefaultMetrics(
                THIS_NODE, new MetricKeyRegistry(), METRIC_EXECUTOR, new DefaultMetricsFactory(), metricsConfig);
    }

    private Configuration createConfig(@NonNull final TestSource testConfig) {
        return ConfigurationBuilder.create()
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(GrpcConfig.class)
                .withConfigDataType(NettyConfig.class)
                .withSource(testConfig)
                .build();
    }

    private HelidonGrpcServerManager createServerManager(@NonNull final TestSource testConfig) {
        final var config = createConfig(testConfig);
        return new HelidonGrpcServerManager(
                () -> new VersionedConfigImpl(config, 1),
                Set::of,
                (req, res) -> {},
                (req, res) -> {},
                createMetrics(config));
    }

    /**
     * If the port number is set to 0, then we allow the computer to choose an "ephemeral" port for us automatically.
     * We won't know ahead of time what the port number is.
     */
    @Test
    @DisplayName("Ephemeral ports are supported")
    @Timeout(value = 5)
    void ephemeralPorts() {
        // Given a server with 0 as the port number for both port and tls port
        final var subject = createServerManager(new TestSource().withPort(0).withTlsPort(0));

        try {
            // When we start the server
            subject.start();

            // Then we find that the server has started
            assertThat(subject.isRunning()).isTrue();
            // And that the port numbers are no longer 0
            assertThat(subject.port()).isNotZero();
            assertThat(subject.tlsPort()).isNotZero();
            // And that the server is listening on the ports
            // FUTURE: I'm only testing the plain port and not the tls port because these tests do not yet support tls
            // properly. But when they do, we should check the tls port too.
            assertThat(isListening(subject.port())).isTrue();
        } finally {
            subject.stop();
        }
    }

    /**
     * Verifies that when actual port numbers are given, they are used. This test is inherently a little unstable,
     * because nothing can be listening on the port that we select. We will attempt to make it more stable by
     * selecting ports at random, and checking whether they are in use before proceeding.
     */
    @Test
    @DisplayName("Non-ephemeral ports are supported")
    @Timeout(value = 5)
    void nonEphemeralPorts() {
        // Given a server configured with actual port numbers
        final var subject = createServerManager(new TestSource().withFreePort().withFreeTlsPort());

        try {
            // When we start the server
            subject.start();

            // Then we find that the server has started
            assertThat(subject.isRunning()).isTrue();
            // And that it is listening on the ports
            // FUTURE: I'm only testing the plain port and not the tls port because these tests do not yet support tls
            // properly. But when they do, we should check the tls port too.
            assertThat(isListening(subject.port())).isTrue();
        } finally {
            subject.stop();
        }
    }

    @Test
    @DisplayName("Starting a server twice throws")
    @Timeout(value = 5)
    void startingTwice() {
        // Given a server with a configuration that will start
        final var subject = createServerManager(new TestSource());

        // When we start the server, we find that it starts. And when we start it again, it throws.
        try {
            subject.start();
            assertThat(subject.port()).isNotZero();
            assertThat(subject.isRunning()).isTrue();
            assertThat(isListening(subject.port())).isTrue();
            assertThatThrownBy(subject::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Server already started");
        } finally {
            subject.stop();
        }
    }

    @Test
    @DisplayName("Stopping a server")
    @Timeout(value = 5)
    void stoppingAStartedServer() {
        // Given a server with a configuration that will start
        final var subject = createServerManager(new TestSource());

        // When we start the server, we find that it starts, and when we stop it, we find that it stops again.
        try {
            subject.start();
            assertThat(subject.port()).isNotZero();
            assertThat(subject.isRunning()).isTrue();
            assertThat(isListening(subject.port())).isTrue();
        } finally {
            subject.stop();
        }

        assertThat(subject.isRunning()).isFalse();
        assertThat(subject.port()).isEqualTo(-1);
        assertThat(subject.tlsPort()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Stopping a server is idempotent")
    @Timeout(value = 5)
    void stoppingIsIdempotent() {
        // Given a server with a configuration that will start
        final var subject = createServerManager(new TestSource());

        // When we start the server, it starts.
        try {
            subject.start();
            assertThat(subject.port()).isNotZero();
        } finally {
            subject.stop();
        }

        // And if we stop it multiple times, this is OK
        assertThat(subject.isRunning()).isFalse();
        subject.stop();
        assertThat(subject.isRunning()).isFalse();
        assertThat(subject.port()).isEqualTo(-1);
        assertThat(subject.tlsPort()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Restarting a server")
    @Timeout(value = 50)
    void restart() {
        // Given a server with a configuration that will start
        final var subject = createServerManager(new TestSource());

        // We can cycle over start / stop / start / stop cycles, and it is all good
        for (int i = 0; i < 10; i++) {
            try {
                subject.start();
                assertThat(subject.port()).isNotZero();
                assertThat(subject.isRunning()).isTrue();
                assertThat(isListening(subject.port())).isTrue();
            } finally {
                subject.stop();
                assertThat(subject.isRunning()).isFalse();
                assertThat(subject.port()).isEqualTo(-1);
                assertThat(subject.tlsPort()).isEqualTo(-1);
            }
        }
    }

    @Test
    @Timeout(value = 5)
    @DisplayName("Starting a server with a port already in use but is then released")
    void portBecomesFreeEventually() throws Exception {
        // Given a server with a configuration that will start
        final var testConfig = new TestSource()
                .withFreePort()
                .withFreeTlsPort()
                .withStartRetries(10)
                .withStartRetryIntervalMs(10);
        final var subject = createServerManager(testConfig);
        // And a server socket listening on the port that the server intends to use
        try (final var serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(testConfig.port));
            assertThat(serverSocket.isBound()).isTrue();

            // When we start the gRPC server on that same port
            final LogCaptor logCaptor = new LogCaptor(LogManager.getLogger(HelidonGrpcServerManager.class));
            final var th = new Thread(subject::start);
            th.start();

            // Wait for the server to try again to startup. We wait until we've seen that the server actually tried
            // to start, and then we will proceed with the rest of the test (we want to make sure the port was occupied
            // when the server tried to start).
            while (true) {
                assertThat(subject.isRunning()).isFalse();
                final var logs = String.join("\n", logCaptor.infoLogs());
                System.out.println(logs);
                if (logs.contains("Still trying to start server... 9 tries remaining")) {
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }

            // And when we stop the socket that was using the port
            serverSocket.close();

            // Wait for the server to start
            while (!subject.isRunning()) {
                TimeUnit.MILLISECONDS.sleep(10);
            }

            // Then we find that the server finally started up!
            assertThat(subject.isRunning()).isTrue();
            // FUTURE: I'm only testing the plain port and not the tls port because these tests do not yet support tls
            // properly. But when they do, we should check the tls port too.
            assertThat(isListening(subject.port())).isTrue();
        } finally {
            subject.stop();
        }
    }

    /**
     * Start with a port that is already in use, and observe that after N retries and X millis per retry, the server
     * ultimately fails to start.
     *
     * <p>NOTE: The Helidon server appears to have its own 5-second timeout while starting. As such, I have to have a
     * longer timeout value here so the timeout will not expire prematurely.
     */
    @Test
    @Timeout(value = 50)
    @DisplayName("Starting a server with a port already in use")
    void portInUse() throws Exception {
        // Given a server with a configuration that will start
        final var testConfig = new TestSource()
                .withFreePort()
                .withFreeTlsPort()
                .withStartRetries(1)
                .withStartRetryIntervalMs(10);
        final var subject = createServerManager(testConfig);
        // And a server socket listening on the port that the server intends to use
        try (final var serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(testConfig.port));
            assertThat(serverSocket.isBound()).isTrue();

            // Start the gRPC server, trying to use the same port, which will eventually give up and throw
            assertThatThrownBy(subject::start)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to start gRPC server");
        } finally {
            subject.stop();
        }
    }

    @Test
    @Timeout(value = 5)
    @DisplayName("Transactions and Queries are routed")
    void requestsAreRouted() {
        // Given a server with a configuration that will start, and tx and query handlers that register they were
        // called, so we can make sure calls work.
        final var config = createConfig(new TestSource());
        final var txCounter = new AtomicInteger(0);
        final var qCounter = new AtomicInteger(0);

        final var testService = new Service() {
            @NonNull
            @Override
            public String getServiceName() {
                return "TestService";
            }

            @NonNull
            @Override
            public Set<RpcServiceDefinition> rpcDefinitions() {
                return Set.of(new RpcServiceDefinition() {
                    @NonNull
                    @Override
                    public String basePath() {
                        return "proto.TestService";
                    }

                    @NonNull
                    @Override
                    public Set<RpcMethodDefinition<?, ?>> methods() {
                        return Set.of(
                                new RpcMethodDefinition<>("tx", Transaction.class, TransactionResponse.class),
                                new RpcMethodDefinition<>("q", Query.class, Response.class));
                    }
                });
            }
        };

        final var metrics = createMetrics(config);
        final var subject = new HelidonGrpcServerManager(
                () -> new VersionedConfigImpl(config, 1),
                () -> Set.of(testService),
                (req, res) -> txCounter.incrementAndGet(),
                (req, res) -> qCounter.incrementAndGet(),
                metrics);

        // When we start the server, we can actually make requests to it!
        try {
            subject.start();

            final var channel = ManagedChannelBuilder.forAddress("localhost", subject.port())
                    .usePlaintext()
                    .build();

            final var sd = new GrpcServiceBuilder("proto.TestService", (req, res) -> {}, (req, res) -> {})
                    .transaction("tx")
                    .query("q")
                    .build(metrics);

            final var builder = io.grpc.ServiceDescriptor.newBuilder("proto.TestService");
            sd.methods().forEach(m -> builder.addMethod(m.descriptor()));
            final var clientServiceDescriptor = builder.build();
            final var client = GrpcServiceClient.builder(
                            channel,
                            ClientServiceDescriptor.builder(clientServiceDescriptor)
                                    .build())
                    .build();

            final var bb = BufferedData.wrap("anything".getBytes(StandardCharsets.UTF_8));
            client.blockingUnary("tx", bb);
            client.blockingUnary("q", bb);

            assertThat(txCounter.get()).isEqualTo(1);
            assertThat(qCounter.get()).isEqualTo(1);

        } finally {
            subject.stop();
        }
    }

    /**
     * Checks whether a server process is listening on the given port
     *
     * @param portNumber The port to check
     */
    private static boolean isListening(int portNumber) {
        try (final var socket = new Socket("localhost", portNumber)) {
            return socket.isConnected();
        } catch (ConnectException connect) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unexpected error while checking whether the port '" + portNumber + "' was free", e);
        }
    }

    /**
     * A config source used by this test to specify the config values
     */
    private static final class TestSource implements ConfigSource {
        private int port = 0;
        private int tlsPort = 0;
        private int startRetries = 3;
        private int startRetryIntervalMs = 100;

        @Override
        public int getOrdinal() {
            return 1000;
        }

        @NonNull
        @Override
        public Set<String> getPropertyNames() {
            return Set.of("grpc.port", "grpc.tlsPort", "netty.startRetryIntervalMs", "netty.startRetries");
        }

        @Nullable
        @Override
        public String getValue(@NonNull String s) throws NoSuchElementException {
            return switch (s) {
                case "grpc.port" -> String.valueOf(port);
                case "grpc.tlsPort" -> String.valueOf(tlsPort);
                case "netty.startRetryIntervalMs" -> String.valueOf(startRetryIntervalMs);
                case "netty.startRetries" -> String.valueOf(startRetries);
                default -> null;
            };
        }

        public TestSource withPort(final int value) {
            this.port = value;
            return this;
        }

        // Locates a free port on its own
        public TestSource withFreePort() {
            this.port = findFreePort();
            Assumptions.assumeThat(this.port).isGreaterThan(0);
            return this;
        }

        public TestSource withTlsPort(final int value) {
            this.tlsPort = value;
            return this;
        }

        // Locates a free port on its own
        public TestSource withFreeTlsPort() {
            this.tlsPort = findFreePort();
            Assumptions.assumeThat(this.tlsPort).isGreaterThan(0);
            return this;
        }

        public TestSource withStartRetries(final int value) {
            this.startRetries = value;
            return this;
        }

        public TestSource withStartRetryIntervalMs(final int value) {
            this.startRetryIntervalMs = value;
            return this;
        }

        private int findFreePort() {
            for (int i = 1024; i < 10_000; i++) {
                if (i != port && i != tlsPort && isPortFree(i)) {
                    return i;
                }
            }

            return -1;
        }

        /**
         * Checks whether the given port is free
         *
         * @param portNumber The port to check
         */
        private boolean isPortFree(int portNumber) {
            return !isListening(portNumber);
        }
    }
}
