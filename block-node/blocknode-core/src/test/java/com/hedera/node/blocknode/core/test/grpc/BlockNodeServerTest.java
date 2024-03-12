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

package com.hedera.node.blocknode.core.test.grpc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.node.blocknode.core.grpc.BlockNodeServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    void expectPortToNotBeNull() {
        assertNotEquals(grpcServer.getPort(), 0);
    }
}
