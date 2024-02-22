package com.hedera.storage.core;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class BlockNodeServer {
    private static final Logger logger = LogManager.getLogger(BlockNodeServer.class);
    private final int port;
    private final Server server;

    public BlockNodeServer(ServerBuilder builder, int port) {
        this.port = port;
        this.server = builder
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
        BlockNodeServer server = new BlockNodeServer(ServerBuilder.forPort(50601), 50601);
        try {
            server.start();
            server.blockUntilShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}