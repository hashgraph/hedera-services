package com.hedera.node.config.data;

import com.hedera.node.config.NodeProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

/**
 * @param port The port for plain grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral port should be
 *             automatically selected by the computer. Must not be the same value as {@link #tlsPort()} unless both are
 *             0. Must be a value between 0 and 65535, inclusive.
 * @param tlsPort The port for tls-encrypted grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral
 *                port should be automatically selected by the computer. Must not be the same value as {@link #port()}
 *                unless both are 0. Must be a value between 0 and 65535, inclusive.
 */
@ConfigData("block-node")
public record BlockNodeConfig(
        @ConfigProperty(defaultValue = "50311") @Min(0) @Max(65535) @NodeProperty int port,
        @ConfigProperty(defaultValue = "50312") @Min(0) @Max(65535) @NodeProperty int tlsPort) {

    public BlockNodeConfig {
        if (port == tlsPort && port != 0) {
            throw new IllegalArgumentException("grpc.port and grpc.tlsPort must be different");
        }
    }
}
