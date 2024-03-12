package com.hedera.node.blocknode.core.test.grpc.impl;

import com.hedera.node.blocknode.core.grpc.impl.BlockNodeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BlockNodeServiceTest {
    private final BlockNodeService subject = new BlockNodeService();

    @Test
    void expectBlockServiceNotNull() {
        assertNotNull(subject);
    }
}
