package com.hedera.node.blocknode.core;

import com.hedera.node.blocknode.core.grpc.BlockNodeServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNode {
    private static final Logger logger = LogManager.getLogger(BlockNode.class);

    public static void main(final String... args) {
        logger.info("BlockNode - Main");
        BlockNodeServer grpcServer = new BlockNodeServer();

        grpcServer.start();
    }
}
