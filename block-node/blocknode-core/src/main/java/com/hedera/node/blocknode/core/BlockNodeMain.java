/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.blocknode.core;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.core.grpc.BlockNodeService;
import com.hedera.node.blocknode.core.grpc.impl.BlockNodeNettyServerManager;
import com.hedera.node.blocknode.core.services.BlockNodeServicesRegistryImpl;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

        Set.of(BLOCK_NODE_SERVICE).forEach(service -> servicesRegistry.registerService("Block Node", service));
        ConfigProvider configProvider = new ConfigProvider();
        serverManager = new BlockNodeNettyServerManager(configProvider, servicesRegistry);
    }

    public static void main(String[] args) {
        new BlockNodeMain();
        serverManager.start();
    }
}
