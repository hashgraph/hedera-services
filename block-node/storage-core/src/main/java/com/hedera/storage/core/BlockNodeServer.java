package com.hedera.storage.core;
import com.hedera.storage.config.ConfigProvider;
import com.hedera.storage.config.data.BlockNodeGrpcConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class BlockNodeServer {
    private static final Logger logger = LogManager.getLogger(BlockNodeServer.class);
    private final int port;
    private final Server server;

    public BlockNodeServer() {
        final var configProvider = new ConfigProvider();
        final var blockNodeConfig = configProvider.configuration.getConfigData(BlockNodeGrpcConfig.class);

        this.port = blockNodeConfig.port();
        this.server = ServerBuilder.forPort(port)
                .addService(new BlockNodeService())
                .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Block app started at port: " + port);
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(final String... args) {
        logger.info("BlockNode - Main");
        BlockNodeServer server = new BlockNodeServer();

        try {
            server.start();
            server.blockUntilShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}