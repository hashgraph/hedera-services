package com.hedera.node.blocknode.core;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.core.grpc.BlockNodeService;
import com.hedera.node.blocknode.core.grpc.impl.BlockNodeNettyServerManager;
import com.hedera.node.blocknode.core.services.BlockNodeServicesRegistryImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

public class BlockNodeMain {
    private static final Logger logger = LogManager.getLogger(BlockNodeMain.class);

    private final BlockNodeServicesRegistryImpl servicesRegistry;

    private static BlockNodeNettyServerManager serverManager;

    private static BlockNodeService BLOCK_NODE_SERVICE;
    public BlockNodeMain() {
        // Create all the service implementations
        logger.info("Registering services");
        BLOCK_NODE_SERVICE = new BlockNodeService();
        this.servicesRegistry = new BlockNodeServicesRegistryImpl();

        Set.of(
                      BLOCK_NODE_SERVICE)
                .forEach(service -> servicesRegistry.registerService("Block Node", service));
        ConfigProvider configProvider = new ConfigProvider();
        serverManager = new BlockNodeNettyServerManager(configProvider, servicesRegistry);
    }

    public static void main(String[] args) {
        new BlockNodeMain();
        serverManager.start();
    }
}
