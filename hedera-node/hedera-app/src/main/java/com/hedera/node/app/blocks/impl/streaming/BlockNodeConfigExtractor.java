/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.impl.streaming;

import com.hedera.node.internal.network.BlockNodeConfig;
import com.hedera.node.internal.network.BlockNodeConnectionInfo;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Extracts block node configuration from a JSON configuration file.
 */
public class BlockNodeConfigExtractor {
    private static final Logger logger = LogManager.getLogger(BlockNodeConfigExtractor.class);
    private final List<BlockNodeConfig> allNodes;
    private final int maxSimultaneousConnections;
    private final Duration nodeReselectionInterval;
    private final int blockItemBatchSize;

    /**
     * @param blockNodeConfigPath the path to the block node configuration file
     */
    public BlockNodeConfigExtractor(String blockNodeConfigPath) {
        final var configPath = Paths.get(blockNodeConfigPath, "block-nodes.json");

        try {
            byte[] jsonConfig = Files.readAllBytes(configPath);
            BlockNodeConnectionInfo protoConfig = BlockNodeConnectionInfo.JSON.parse(Bytes.wrap(jsonConfig));

            // Convert proto config to internal config objects
            this.allNodes = protoConfig.nodes().stream()
                    .map(node -> new BlockNodeConfig(node.priority(), node.address(), node.port()))
                    .collect(Collectors.toList());

            logger.info("Loaded block node configuration from {}", configPath);
            logger.info("Block node configuration: {}", allNodes);

            this.maxSimultaneousConnections = protoConfig.maxSimultaneousConnections();
            this.nodeReselectionInterval = Duration.ofSeconds(protoConfig.nodeReselectionInterval());
            this.blockItemBatchSize = protoConfig.blockItemBatchSize();

        } catch (IOException | ParseException e) {
            logger.error("Failed to read block node configuration from {}", configPath, e);
            throw new RuntimeException("Failed to read block node configuration from " + configPath, e);
        }

        // Sort nodes by priority (lowest number = highest priority)
        allNodes.sort(Comparator.comparingInt(BlockNodeConfig::priority));
    }

    /**
     * @return the list of all block node configurations
     */
    public List<BlockNodeConfig> getAllNodes() {
        return allNodes;
    }

    /**
     * @return the maximum number of simultaneous connections to block nodes
     */
    public int getMaxSimultaneousConnections() {
        return maxSimultaneousConnections;
    }

    /**
     * @return the interval of node reselection
     */
    public Duration getNodeReselectionInterval() {
        return nodeReselectionInterval;
    }

    /**
     * @return the block items batch size to send to the block nodes
     */
    public int getBlockItemBatchSize() {
        return blockItemBatchSize;
    }
}
