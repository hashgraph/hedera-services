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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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

    private BlockStreamServiceGrpc.BlockStreamServiceStub asyncStub;
    private StreamObserver<PublishStreamRequest> requestObserver;

    /** The state of this writer */
    private GrpcBlockItemWriter.State state = GrpcBlockItemWriter.State.UNINITIALIZED;

    private enum State {
        UNINITIALIZED,
        OPEN,
        CLOSED
    }

    @Override
    public void openBlock(long blockNumber) {
        if (state != GrpcBlockItemWriter.State.UNINITIALIZED)
            throw new IllegalStateException("Cannot initialize a GrpcBlockItemWriter twice");

        if (blockNumber < 0) throw new IllegalArgumentException("Block number must be non-negative");

        asyncStub = createBlockNodeAsyncStub();

        requestObserver =
                asyncStub.publishBlockStream(new StreamObserver<PublishStreamResponse>() {
                    @Override
                    public void onNext(PublishStreamResponse value) {
                        // TODO (driley) Handle EndofStream responses, and if Single Item Ack is enabled via config then
                        // implement that as well.
                        // However that would require keeping track of the hashes of the items sent and then verifying
                        // before
                        // some timeout that the item_hash was received.
                        // Also it is possible a BlockNode could have and EndOfStream response which would indicate the
                        // consensus
                        // node needs to restart the stream and send a previous Block again
                        logger.info("PublishStreamResponse received: " + value.toString());
                    }

                    @Override
                    public void onError(Throwable t) {
                        // TODO
                        t.printStackTrace();
                    }

                    @Override
                    public void onCompleted() {
                        // TODO
                        logger.info("PublishStreamResponse completed");
                    }
                });

        this.state = GrpcBlockItemWriter.State.OPEN;
    }

    @Override
    public void writeItem(@NonNull Bytes serializedItem) {
        if (state != GrpcBlockItemWriter.State.OPEN) {
            throw new IllegalStateException("Cannot write to a GrpcBlockItemWriter that is not open");
        }

        // TODO toByteArray may be inefficient here. Consider using a different method.
        try {
            // Get the current time in milliseconds
            long currentTimeMillis = System.currentTimeMillis();
            long seconds = TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis);
            int nanos = (int) (TimeUnit.MILLISECONDS.toNanos(currentTimeMillis) - TimeUnit.SECONDS.toNanos(seconds));

            PublishStreamRequest request = PublishStreamRequest.newBuilder()
                    .setBlockItem(BlockItem.parseFrom(serializedItem.toByteArray()))
                    .setRequestTimestamp(Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build())
                    .build();
            requestObserver.onNext(request);
        } catch (InvalidProtocolBufferException e) {
            // TODO
            throw new RuntimeException(e);
        }
    }

    @Override
    public void closeBlock() {
        requestObserver.onCompleted();
        this.state = GrpcBlockItemWriter.State.CLOSED;
    }

    private BlockStreamServiceGrpc.BlockStreamServiceStub createBlockNodeAsyncStub() {
        var host = "localhost";
        var port = 9090;
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        return BlockStreamServiceGrpc.newStub(channel);
    }

    @Override
    public boolean isClosed() {
        return state == GrpcBlockItemWriter.State.CLOSED;
    }
}