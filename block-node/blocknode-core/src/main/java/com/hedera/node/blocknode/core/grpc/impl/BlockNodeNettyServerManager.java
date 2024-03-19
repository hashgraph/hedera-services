package com.hedera.node.blocknode.core.grpc.impl;

import com.hedera.node.blocknode.config.data.BlockNodeGrpcConfig;
import com.hedera.node.blocknode.core.services.BlockNodeServicesRegistryImpl;
import com.hedera.node.blocknode.core.GrpcBlockNodeServerManager;
import com.hedera.node.config.data.NettyConfig;
import com.hedera.node.config.types.Profile;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.grpc.ServiceDescriptor;
import io.grpc.netty.NettyServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import com.hedera.node.blocknode.config.ConfigProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import static java.util.Objects.requireNonNull;


/**
 * An implementation of {@link GrpcBlockNodeServerManager} based on Helidon gRPC.
 *
 * <p>This implementation uses two different ports for gRPC and gRPC+TLS. If the TLS server cannot be started, then
 * a warning is logged, but we continue to function without TLS. This is useful during testing and local development
 * where TLS may not be available.
 */

public class BlockNodeNettyServerManager implements GrpcBlockNodeServerManager{
    /**
     * The logger instance for this class.
     */
    private static final Logger logger = LogManager.getLogger(BlockNodeNettyServerManager.class);
    /**
     * The supported ciphers for TLS
     */
    private static final List<String> SUPPORTED_CIPHERS = List.of(
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_AES_256_GCM_SHA384");
    /**
     * The supported protocols for TLS
     */
    private static final List<String> SUPPORTED_PROTOCOLS = List.of("TLSv1.2", "TLSv1.3");

    /**
     * The set of {@link ServiceDescriptor}s for services that the gRPC server will expose
     */
    private final BlockNodeServicesRegistryImpl services;
    /**
     * The configuration provider, so we can figure out ports and other information.
     */
    private final ConfigProvider configProvider;
    /**
     * The gRPC server listening on the plain (non-tls) port
     */
    private Server plainServer;
    /**
     * The gRPC server listening on the plain TLS port
     */
    private Server tlsServer;

    public BlockNodeNettyServerManager(
            @NonNull final ConfigProvider configProvider,
            @NonNull final BlockNodeServicesRegistryImpl blockNodeRegistry
    ){
        this.configProvider = requireNonNull(configProvider);
        this.services = blockNodeRegistry;
    }
    @Override
    public void start() {
        if (isRunning()) {
            logger.error("Cannot start gRPC servers, they have already been started!");
            throw new IllegalStateException("Server already started");
        }
        logger.info("Starting gRPC servers");
        final var grpcConfig = configProvider.getConfiguration().getConfigData(BlockNodeGrpcConfig.class);
        final var port = grpcConfig.port();
//        final var profile = configProvider
//                .getConfiguration()
//                .getConfigData(BlockNodeConfig.class)
//                .activeProfile();

        // Start the plain-port server
        logger.info("Starting gRPC server on port {}", port);
        var nettyBuilder = NettyServerBuilder.forPort(port);
        plainServer = startServerWithRetry(nettyBuilder, 3, 1000);
        blockUntilShutdown(plainServer);
        logger.info("gRPC server listening on port {}", plainServer.getPort());
    }

    private void blockUntilShutdown(Server server) {
        if (server != null) {
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    @Override
    public synchronized void stop() {
        // Do not attempt to shut down if we have already done so
        if (plainServer != null && !plainServer.isTerminated()) {
            logger.info("Shutting down gRPC server on port {}", plainServer.getPort());
            terminateServer(plainServer);
        } else {
            logger.info("Cannot shut down an already stopped gRPC server");
        }

        if (tlsServer != null && !tlsServer.isTerminated()) {
            logger.info("Shutting down TLS gRPC server on port {}", tlsServer.getPort());
            terminateServer(tlsServer);
        } else {
            logger.info("Cannot shut down an already stopped gRPC server");
        }
    }

    @Override
    public int port() {
        return plainServer == null || plainServer.isTerminated() ? -1 : plainServer.getPort();
    }

    @Override
    public int tlsPort() {
        return tlsServer == null ? -1 : tlsServer.getPort();
    }

    @Override
    public boolean isRunning() {
        return plainServer != null && !plainServer.isShutdown();
    }


    /**
     * Attempts to start the server. It will retry {@code startRetries} times until it finally gives up with
     * {@code startRetryIntervalMs} between attempts.
     *
     * @param nettyBuilder The builder used to create the server to start
     * @param startRetries The number of times to retry, if needed. Non-negative (enforced by config).
     * @param startRetryIntervalMs The time interval between retries. Positive (enforced by config).
     */
    Server startServerWithRetry(
            @NonNull final NettyServerBuilder nettyBuilder, final int startRetries, final long startRetryIntervalMs) {

        requireNonNull(nettyBuilder);

        // Setup the GRPC Routing, such that all grpc services are registered
        //services.forEach(nettyBuilder::addService);
        final var server = nettyBuilder.build();

        var remaining = startRetries;
        while (remaining > 0) {
            try {
                server.start();
                return server;
            } catch (IOException e) {
                remaining--;
                if (remaining == 0) {
                    throw new RuntimeException("Failed to start gRPC server");
                }
                logger.info("Still trying to start server... {} tries remaining", remaining, e);

                // Wait a bit before retrying. In the FUTURE we should consider removing this functionality, it isn't
                // clear that it is actually helpful, and it complicates the code. But for now we will keep it so as
                // to remain as compatible as we can with previous non-modular releases.
                try {
                    Thread.sleep(startRetryIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry server start", ie);
                }
            }
        }

        throw new RuntimeException("Failed to start gRPC server");
    }

    /**
     * Utility for setting up various shared configuration settings between both servers
     */
//    private NettyServerBuilder builderFor(
//            final int port, @NonNull final NettyConfig config, @NonNull final Profile activeProfile) {
//        NettyServerBuilder builder = null;
//        try {
//            builder = withConfigForActiveProfile(NettyServerBuilder.forPort(port), config, activeProfile)
//                    .channelType(EpollServerSocketChannel.class)
//                    .bossEventLoopGroup(new EpollEventLoopGroup())
//                    .workerEventLoopGroup(new EpollEventLoopGroup());
//            logger.info("Using Epoll for gRPC server");
//        } catch (final UnsatisfiedLinkError | NoClassDefFoundError ignored) {
//            // If we can't use Epoll, then just use NIO
//            logger.info("Epoll not available, using NIO");
//            builder = withConfigForActiveProfile(NettyServerBuilder.forPort(port), config, activeProfile);
//        } catch (final Exception unexpected) {
//            logger.info("Unexpected exception initializing Netty", unexpected);
//        }
//        return builder;
//    }

    private NettyServerBuilder withConfigForActiveProfile(
            @NonNull final NettyServerBuilder builder,
            @NonNull final NettyConfig config,
            @NonNull final Profile activeProfile) {
        if (activeProfile != Profile.DEV) {
            builder.keepAliveTime(config.prodKeepAliveTime(), TimeUnit.SECONDS)
                    .permitKeepAliveTime(config.prodKeepAliveTime(), TimeUnit.SECONDS)
                    .keepAliveTimeout(config.prodKeepAliveTimeout(), TimeUnit.SECONDS)
                    .maxConnectionAge(config.prodMaxConnectionAge(), TimeUnit.SECONDS)
                    .maxConnectionAgeGrace(config.prodMaxConnectionAgeGrace(), TimeUnit.SECONDS)
                    .maxConnectionIdle(config.prodMaxConnectionIdle(), TimeUnit.SECONDS)
                    .maxConcurrentCallsPerConnection(config.prodMaxConcurrentCalls())
                    .flowControlWindow(config.prodFlowControlWindow());
        }
        return builder.directExecutor();
    }

    /**
     * Terminates the given server
     *
     * @param server the server to terminate
     */
    private void terminateServer(@Nullable final Server server) {
        if (server == null) {
            return;
        }

        //final var nettyConfig = configProvider.getConfiguration().getConfigData(NettyConfig.class);
        final var terminationTimeout = 100000;

        try {
            server.shutdownNow();
            server.awaitTermination(terminationTimeout, TimeUnit.SECONDS);
            logger.info("gRPC server stopped");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for gRPC to terminate!", ie);
        } catch (Exception e) {
            logger.warn("Exception while waiting for gRPC to terminate!", e);
        }
    }
}
