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

import static io.grpc.Status.fromThrowable;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.hapi.block.protoc.PublishStreamResponse.Acknowledgement;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the bidirectional streaming RPC for the publishBlockStream rpc in BlockStreamService
 * See <a href="https://grpc.io/docs/languages/java/basics/">gRPC Basics</a>
 */
public class GrpcBlockItemWriter implements BlockItemWriter {

    private static final Logger logger = LogManager.getLogger(GrpcBlockItemWriter.class);
    private static final String INVALID_MESSAGE = "Invalid protocol buffer converting %s from PBJ to protoc for %s";

    @Nullable
    private ManagedChannel channel;

    private final BlockStreamServiceGrpc.BlockStreamServiceStub asyncStub;
    private StreamObserver<PublishStreamRequest> requestObserver;
    private long blockNumber;

    /** The state of this writer */
    private State state = State.UNINITIALIZED;

    /**
     * The current state of the gRPC writer.
     */
    public enum State {
        /**
         * The gRPC client still not initialized
         */
        UNINITIALIZED,
        /**
         * The gRPC client is currently open
         */
        OPEN,
        /**
         * The gRPC client is already closed
         */
        CLOSED
    }

    /**
     * @param blockStreamConfig the block stream configuration
     */
    public GrpcBlockItemWriter(@NonNull final BlockStreamConfig blockStreamConfig) {
        requireNonNull(blockStreamConfig, "The supplied argument 'blockStreamConfig' cannot be null!");

        channel = ManagedChannelBuilder.forAddress(blockStreamConfig.address(), blockStreamConfig.port())
                .usePlaintext()
                .build();
        asyncStub = BlockStreamServiceGrpc.newStub(channel);
    }

    @Override
    public void openBlock(long blockNumber) {
        if (state != State.UNINITIALIZED) throw new IllegalStateException("GrpcBlockItemWriter initialized twice");

        if (blockNumber < 0) throw new IllegalArgumentException("Block number must be non-negative");
        this.blockNumber = blockNumber;
        requestObserver = asyncStub.publishBlockStream(new StreamObserver<>() {
            @Override
            public void onNext(PublishStreamResponse streamResponse) {
                if (streamResponse.hasAcknowledgement()) {
                    final Acknowledgement acknowledgement = streamResponse.getAcknowledgement();
                    if (acknowledgement.hasBlockAck()) {
                        logger.info("PublishStreamResponse: a full block received: {}", acknowledgement.getBlockAck());
                    } else if (acknowledgement.hasItemAck()) {
                        logger.info(
                                "PublishStreamResponse: a single block item is received: {}",
                                acknowledgement.getItemAck());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                // Maybe this should be considered in this case:
                // https://github.com/hashgraph/hedera-services/issues/15530
                final Status status = fromThrowable(t);
                logger.error(status.toString());
            }

            @Override
            public void onCompleted() {
                try {
                    if (channel != null) {
                        channel.shutdown().awaitTermination(10, TimeUnit.MILLISECONDS);
                        channel = null;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
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
            final String message = INVALID_MESSAGE.formatted("PublishStreamResponse", request);
            throw new RuntimeException(message, e);
        }
        return this;
    }

    @Override
    public void closeBlock() {
        if (state.ordinal() < GrpcBlockItemWriter.State.OPEN.ordinal()) {
            throw new IllegalStateException("Cannot close a GrpcBlockItemWriter that is not open");
        } else if (state.ordinal() == GrpcBlockItemWriter.State.CLOSED.ordinal()) {
            throw new IllegalStateException("Cannot close a GrpcBlockItemWriter that is already closed");
        }

        requestObserver.onCompleted();
        this.state = State.CLOSED;
    }

    /**
     * @return the current state of the gRPC writer
     */
    @VisibleForTesting
    public long getBlockNumber() {
        return blockNumber;
    }

    /**
     * @return the current state of the gRPC writer
     */
    @VisibleForTesting
    public State getState() {
        return state;
    }

    /**
     * Close the existing channel.
     */
    @VisibleForTesting
    public void closeChannel() {
        try {
            if (channel != null) {
                channel.shutdown().awaitTermination(10, TimeUnit.MILLISECONDS);
                channel = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the stub of the gRPC writer
     */
    @VisibleForTesting
    public BlockStreamServiceGrpc.BlockStreamServiceStub getStub() {
        return asyncStub;
    }
}
