package com.hedera.node.app.blocks.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Configuration for a single block node connection.
 */
public record BlockNodeConfig(
        int priority,
        String address,
        int port,
        boolean preferred,
        int blockItemBatchSize) {
    
    public BlockNodeConfig {
        if (priority < 1) {
            throw new IllegalArgumentException("Priority must be greater than 0");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (blockItemBatchSize < 1) {
            throw new IllegalArgumentException("Block item batch size must be greater than 0");
        }
    }

    /**
     * Creates a new BlockNodeConfig with default batch size.
     */
    public BlockNodeConfig(int priority, String address, int port, boolean preferred) {
        this(priority, address, port, preferred, 32);
    }
} 