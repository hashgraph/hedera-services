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

package com.hedera.node.app.blocks.impl;

import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.hapi.block.protoc.PublishStreamResponseCode;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.node.app.blocks.config.BlockStreamGrpcConfig;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the bidirectional streaming RPC for the publishBlockStream rpc in BlockStreamService
 * See <a href="https://grpc.io/docs/languages/java/basics/">gRPC Basics</a>
 */
public class GrpcBlockItemWriter implements BlockItemWriter {

    private static final Logger logger = LogManager.getLogger(GrpcBlockItemWriter.class);
    private static final String INVALID_MESSAGE = "Invalid protocol buffer converting %s from PBJ to protoc for %s";

    private BlockStreamServiceGrpc.BlockStreamServiceStub asyncStub;
    private StreamObserver<PublishStreamRequest> requestObserver;

    private final String address;
    private final int port;
    private long blockNumber;

    /** The state of this writer */
    private State state;

    private enum State {
        UNINITIALIZED,
        OPEN,
        CLOSED
    }

    /**
     * @param configProvider the configuration provider
     */
    public GrpcBlockItemWriter(@NonNull final ConfigProvider configProvider) {
        requireNonNull(configProvider, "The supplied argument 'configProvider' cannot be null!");
        final var config = configProvider.getConfiguration();
        final var blockStreamConfig = config.getConfigData(BlockStreamGrpcConfig.class);
        this.address = blockStreamConfig.address();
        this.port = blockStreamConfig.port();
    }

    @Override
    public void openBlock(long blockNumber) {
        if (state != State.UNINITIALIZED) throw new IllegalStateException("GrpcBlockItemWriter initialized twice");

        if (blockNumber < 0) throw new IllegalArgumentException("Block number must be non-negative");
        this.blockNumber = blockNumber;
        asyncStub = createBlockNodeAsyncStub();

        requestObserver = asyncStub.publishBlockStream(new StreamObserver<>() {
            @Override
            public void onNext(PublishStreamResponse value) {
                if (value.getStatus().equals(PublishStreamResponseCode.STREAM_ITEMS_UNKNOWN)) {
                    closeBlock();
                }
                // Block Node build EndOfStream response in different scenarios:
                // https://github.com/hashgraph/hedera-block-node/blob/simulator-grpc-client2/server/src/main/java/com/hedera/block/server/producer/ProducerBlockItemObserver.java#L213-L221
                logger.info("PublishStreamResponse received: " + value.toString());
            }

            @Override
            public void onError(Throwable t) {
                // TODO: What should be the approach when error occurs in the communication?
                // Maybe this should be considered in this case:
                // https://github.com/hashgraph/hedera-services/issues/15530

                logger.error("onError method invoked with an exception: ", t);
            }

            @Override
            public void onCompleted() {
                // TODO: Is there anything we should do?
                logger.info("PublishStreamResponse completed");
            }
        });

        this.state = State.OPEN;
    }

    @Override
    public BlockItemWriter writeItem(@NonNull Bytes serializedItem) {
        if (state != State.OPEN) {
            throw new IllegalStateException(
                    "Cannot write to a GrpcBlockItemWriter that is not open for block: " + this.blockNumber);
        }

        PublishStreamRequest request = PublishStreamRequest.newBuilder().build();
        try {
            request = PublishStreamRequest.newBuilder()
                    .setBlockItem(BlockItem.parseFrom(serializedItem.toByteArray()))
                    .build();
            requestObserver.onNext(request);
        } catch (InvalidProtocolBufferException e) {
            final String message = INVALID_MESSAGE.formatted("SubscribeStreamResponse", request);
            /*final EndOfStream endOfStream =
                    EndOfStream.newBuilder()
                            .setStatus(PublishStreamResponseCode.STREAM_ITEMS_UNKNOWN)
                            .build();
            requestObserver.onNext(
                    PublishStreamResponse.newBuilder().setStatus(endOfStream).build()
            );*/
            logger.error(message);
            throw new RuntimeException(message, e);
        }
        return this;
    }

    @Override
    public void closeBlock() {
        requestObserver.onCompleted();
        this.state = State.CLOSED;
    }

    private BlockStreamServiceGrpc.BlockStreamServiceStub createBlockNodeAsyncStub() {
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
        return BlockStreamServiceGrpc.newStub(channel);
    }
}
