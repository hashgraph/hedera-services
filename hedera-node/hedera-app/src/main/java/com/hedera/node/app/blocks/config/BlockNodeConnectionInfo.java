package com.hedera.node.app.blocks.config;

import java.util.List;

/**
 * Configuration for block stream connections.
 */
public record BlockNodeConnectionInfo (
        List<BlockNodeConfig> nodes,
        int nodeReselectionInterval,
        int maxSimultaneousConnections) {

    /**
     * Creates a new BlockStreamConnectionConfig with default values.
     */
    public BlockNodeConnectionInfo(List<BlockNodeConfig> nodes, int nodeReselectionInterval, int maxSimultaneousConnections) {
        this.nodes = nodes;
        this.nodeReselectionInterval = nodeReselectionInterval;
        this.maxSimultaneousConnections = maxSimultaneousConnections;
    }
} 