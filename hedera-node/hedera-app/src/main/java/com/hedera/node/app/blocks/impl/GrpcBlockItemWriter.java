/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Implements the bidirectional streaming RPC for the publishBlockStream rpc in BlockStreamService
 * See <a href="https://grpc.io/docs/languages/java/basics/">gRPC Basics</a>
 */
public class GrpcBlockItemWriter implements BlockItemWriter {

    private final BlockNodeConnectionManager connectionManager;

    public GrpcBlockItemWriter(BlockNodeConnectionManager connectionManager) {
        this.connectionManager = requireNonNull(connectionManager, "connectionManager must not be null");
    }

    @Override
    public void openBlock(long blockNumber) {
        connectionManager.openBlock(blockNumber);
    }

    @Override
    public BlockItemWriter writePbjItem(@NonNull Bytes bytes) {
        connectionManager.addPbjItem(bytes);
        return this;
    }

    @Override
    public BlockItemWriter writeItem(@NonNull byte[] bytes) {
        throw new UnsupportedOperationException("writeItem is not supported in this implementation");
    }

    @Override
    public BlockItemWriter writeItems(@NonNull BufferedData data) {
        throw new UnsupportedOperationException("writeItems is not supported in this implementation");
    }

    @Override
    public void closeBlock() {
        // At this point the block proof has been written to the BlockNodeConnectionManager
        connectionManager.closeBlock();
    }
}
