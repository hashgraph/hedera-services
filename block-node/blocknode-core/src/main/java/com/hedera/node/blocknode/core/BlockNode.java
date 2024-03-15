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
import com.hedera.node.blocknode.config.data.BlockNodeConfig;
import com.hedera.node.blocknode.core.downloader.block.BlockDownloader;
import com.hedera.node.blocknode.core.grpc.BlockNodeServer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNode {
    private static final Logger logger = LogManager.getLogger(BlockNode.class);
    private static ExecutorService fixedThreadPool;
    private static ScheduledExecutorService scheduledThreadPool;

    public static void main(final String... args) {
        logger.info("BlockNode - Main");
        final var configProvider = new ConfigProvider();
        final var blockNodeConfig = configProvider.configuration.getConfigData(BlockNodeConfig.class);
        fixedThreadPool = Executors.newFixedThreadPool(blockNodeConfig.fixedThreads());
        scheduledThreadPool = Executors.newScheduledThreadPool(blockNodeConfig.scheduledThreads());
        BlockNodeServer grpcServer = new BlockNodeServer();
        BlockDownloader downloader = new BlockDownloader();
        Runnable grpcServerTask = grpcServer::start;
        Runnable downloadTask = downloader::download;
        fixedThreadPool.submit(grpcServerTask);
        scheduledThreadPool.scheduleAtFixedRate(downloadTask, 0, blockNodeConfig.duration(), TimeUnit.MILLISECONDS);
        logger.info("main");
    }
}
