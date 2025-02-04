/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.impl.streaming;

import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.node.internal.network.BlockNodeConfig;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.helidon.webclient.grpc.GrpcServiceClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNodeConnection {
    private static final Logger logger = LogManager.getLogger(BlockNodeConnection.class);
    private final BlockNodeConfig config;
    private final GrpcServiceClient grpcServiceClient;
    private final BlockNodeConnectionManager manager;
    private StreamObserver<PublishStreamRequest> requestObserver;
    private volatile boolean isActive = true;

    public BlockNodeConnection(
            BlockNodeConfig config, GrpcServiceClient grpcServiceClient, BlockNodeConnectionManager manager) {
        this.config = config;
        this.grpcServiceClient = grpcServiceClient;
        this.manager = manager;
        establishStream();
    }

    private void establishStream() {
        requestObserver =
                grpcServiceClient.bidi(manager.getGrpcEndPoint(), new StreamObserver<PublishStreamResponse>() {
                    @Override
                    public void onNext(PublishStreamResponse response) {
                        // if (response.hasAcknowledgement()) {
                        // handleAcknowledgement(response.getAcknowledgement());
                        // } else if (response.hasStatus()) {
                        // handleEndOfStream(response.getStatus());
                        // }
                    }

                    @Override
                    public void onError(Throwable t) {
                        Status status = Status.fromThrowable(t);
                        logger.error(
                                "Error in block node stream {}:{}: {}", config.address(), config.port(), status, t);
                        handleStreamFailure();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("Stream completed for block node {}:{}", config.address(), config.port());
                        handleStreamFailure();
                    }
                });
    }

    private void handleStreamFailure() {
        isActive = false;
        manager.handleConnectionError(config);
    }

    public void sendRequest(PublishStreamRequest request) {
        if (!isActive) return;
        requestObserver.onNext(request);
    }

    public void close() {
        if (isActive) {
            isActive = false;
            requestObserver.onCompleted();
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public BlockNodeConfig getConfig() {
        return config;
    }
}
