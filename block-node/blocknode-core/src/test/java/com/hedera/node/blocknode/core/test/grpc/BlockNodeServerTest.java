package com.hedera.node.blocknode.core.test.grpc;

import com.hedera.node.blocknode.core.grpc.BlockNodeServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BlockNodeServerTest {
    private static BlockNodeServer grpcServer;
    @BeforeAll
    static void setup() {
        grpcServer = new BlockNodeServer();
    }
    @Test
    void expectBlockServerToNotBeNull() {
        assertNotNull(grpcServer);
        assertNotNull(grpcServer.getGrpcServer());
    }

    @Test
    void expectPortToNotBeNull(){
        assertNotEquals(grpcServer.getPort(), 0);
    }
}
