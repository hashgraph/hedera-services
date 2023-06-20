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

package com.hedera.node.app.grpc;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.GrpcConfig;
import com.hedera.node.config.data.NettyConfig;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceException;
import io.helidon.grpc.core.GrpcTlsDescriptor;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.ServiceDescriptor;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link GrpcServerManager} based on Helidon gRPC.
 *
 * <p>This implementation uses two different ports for gRPC and gRPC+TLS. If the TLS server cannot be started, then
 * a warning is logged, but we continue to function without TLS. This is useful during testing and local development
 * where TLS may not be available.
 */
@Singleton
public final class HelidonGrpcServerManager implements GrpcServerManager {
    /** The logger instance for this class. */
    private static final Logger logger = LogManager.getLogger(HelidonGrpcServerManager.class);
    /** The set of {@link ServiceDescriptor}s for services that the gRPC server will expose */
    private final Set<ServiceDescriptor> services;
    /** The configuration provider, so we can figure out ports and other information. */
    private final ConfigProvider configProvider;
    /** The gRPC server listening on the plain (non-tls) port */
    private GrpcServer server;
    /** The gRPC server listening on the plain TLS port */
    private GrpcServer tlsServer;

    /**
     * Create a new instance.
     *
     * @param configProvider The config provider, so we can figure out ports and other information.
     * @param servicesRegistry The set of all services registered with the system
     * @param ingestWorkflow The implementation of the {@link IngestWorkflow} to use for transaction rpc methods
     * @param queryWorkflow The implementation of the {@link QueryWorkflow} to use for query rpc methods
     * @param metrics Used to get/create metrics for each transaction and query method.
     */
    @Inject
    public HelidonGrpcServerManager(
            @NonNull final ConfigProvider configProvider,
            @NonNull final ServicesRegistry servicesRegistry,
            @NonNull final IngestWorkflow ingestWorkflow,
            @NonNull final QueryWorkflow queryWorkflow,
            @NonNull final Metrics metrics) {
        this.configProvider = requireNonNull(configProvider);
        requireNonNull(ingestWorkflow);
        requireNonNull(queryWorkflow);
        requireNonNull(metrics);

        // Convert the various RPC service definitions into transaction or query endpoints using the GrpcServiceBuilder.
        services = servicesRegistry.services().stream()
                .flatMap(s -> s.rpcDefinitions().stream())
                .map(d -> {
                    final var builder = new GrpcServiceBuilder(d.basePath(), ingestWorkflow, queryWorkflow);
                    d.methods().forEach(m -> {
                        if (Transaction.class.equals(m.requestType())) {
                            builder.transaction(m.path());
                        } else {
                            builder.query(m.path());
                        }
                    });
                    return builder.build(metrics);
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int port() {
        return server == null ? -1 : server.port();
    }

    @Override
    public int tlsPort() {
        return tlsServer == null ? -1 : tlsServer.port();
    }

    @Override
    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            logger.error("Cannot start gRPC servers, they have already been started!");
            throw new IllegalStateException("Server already started");
        }

        // Setup the GRPC Routing, such that all grpc services are registered
        final var grpcRoutingBuilder = GrpcRouting.builder();
        services.forEach(grpcRoutingBuilder::register);

        logger.info("Starting gRPC servers");
        final var nettyConfig = configProvider.getConfiguration().getConfigData(NettyConfig.class);
        final var startRetries = nettyConfig.startRetries();
        final var startRetryIntervalMs = nettyConfig.startRetryIntervalMs();

        final var grpcConfig = configProvider.getConfiguration().getConfigData(GrpcConfig.class);
        final var port = grpcConfig.port();

        // Start the plain-port server
        logger.debug("Starting Helidon gRPC server on port {}", port);
        server = GrpcServer.create(GrpcServerConfiguration.builder().port(port), grpcRoutingBuilder);
        startServerWithRetry(server, startRetries, startRetryIntervalMs);
        logger.debug("Helidon gRPC server listening on port {}", server.port());

        // Try to start the server listening on the tls port. If this doesn't start, then we just keep going. We should
        // rethink whether we want to have two ports per consensus node like this. We do expose both via the proxies,
        // but we could have either TLS or non-TLS only on the node itself and have the proxy manage making a TLS
        // connection or terminating it, as appropriate. But for now, we support both, with the TLS port being optional.
        try {
            final var tlsPort = grpcConfig.tlsPort();
            logger.debug("Starting Helidon TLS gRPC server on port {}", tlsPort);
            tlsServer = GrpcServer.create(
                    GrpcServerConfiguration.builder()
                            .port(tlsPort)
                            .tlsConfig(GrpcTlsDescriptor.builder()
                                    .enabled(true)
                                    .tlsCert(Resource.create(Path.of(nettyConfig.tlsCrtPath())))
                                    .tlsKey(Resource.create(Path.of(nettyConfig.tlsKeyPath())))
                                    .build())
                            .build(),
                    grpcRoutingBuilder);
            startServerWithRetry(tlsServer, startRetries, startRetryIntervalMs);
            logger.debug("Helidon TLS gRPC server listening on port {}", tlsServer.port());
        } catch (ResourceException e) {
            tlsServer = null;
            logger.warn("Could not start TLS server, will continue without it: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        logger.info("Shutting down gRPC servers");
        if (server != null) {
            logger.info("Shutting down Helidon gRPC server on port {}", server.port());
            terminateServer(server);
            server = null;
        }

        if (tlsServer != null) {
            logger.info("Shutting down Helidon TLS gRPC server on port {}", tlsServer.port());
            terminateServer(tlsServer);
            tlsServer = null;
        }
    }

    /**
     * Attempts to start the server. It will retry {@code startRetries} times until it finally gives up with
     * {@code startRetryIntervalMs} between attempts.
     *
     * @param server The server to start
     * @param startRetries The number of times to retry, if needed. Non-negative (enforced by config).
     * @param startRetryIntervalMs The time interval between retries. Positive (enforced by config).
     */
    void startServerWithRetry(
            @NonNull final GrpcServer server, final int startRetries, final long startRetryIntervalMs) {
        requireNonNull(server);

        var remaining = startRetries;
        while (remaining > 0) {
            try {
                server.start().toCompletableFuture().get(startRetryIntervalMs, TimeUnit.MILLISECONDS);
                if (server.isRunning()) return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting to for server to start", ie);
            } catch (ExecutionException | TimeoutException e) {
                remaining--;
                if (remaining == 0) {
                    throw new RuntimeException("Failed to start gRPC server");
                }
                logger.info("Still trying to start server... {} tries remaining", remaining);
            }
        }
    }

    /**
     * Terminates the given server
     *
     * @param server the server to terminate
     */
    private void terminateServer(@Nullable final GrpcServer server) {
        if (server == null) {
            return;
        }

        final var nettyConfig = configProvider.getConfiguration().getConfigData(NettyConfig.class);
        final var terminationTimeout = nettyConfig.terminationTimeout();

        try {
            server.shutdown().toCompletableFuture().get(terminationTimeout, TimeUnit.SECONDS);
            logger.info("Helidon gRPC server stopped");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for Helidon gRPC to terminate!", ie);
        } catch (Exception e) {
            logger.warn("Exception while waiting for Helidon gRPC to terminate!", e);
        }
    }
}
