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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNodeConnection {
    private static final Logger logger = LogManager.getLogger(BlockNodeConnection.class);
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final BlockNodeConfig node;
    private final GrpcServiceClient grpcServiceClient;
    private final BlockNodeConnectionManager manager;
    private StreamObserver<PublishStreamRequest> requestObserver;

    private volatile boolean isActive = true;
    private int retryAttempts = 0;
    private Instant nextRetryTime = Instant.now();

    public BlockNodeConnection(
            BlockNodeConfig nodeConfig, GrpcServiceClient grpcServiceClient, BlockNodeConnectionManager manager) {
        this.node = nodeConfig;
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
                        logger.error("Error in block node stream {}:{}: {}", node.address(), node.port(), status, t);
                        handleStreamFailure();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("Stream completed for block node {}:{}", node.address(), node.port());
                        handleStreamFailure();
                    }
                });
    }

    private void handleStreamFailure() {
        isActive = false;
        removeFromActiveConnections(node);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (retryAttempts < MAX_RETRY_ATTEMPTS) {
            long delayMillis =
                    (long) (INITIAL_RETRY_DELAY.toMillis() * Math.pow(RETRY_BACKOFF_MULTIPLIER, retryAttempts));
            nextRetryTime = Instant.now().plusMillis(delayMillis);
            retryAttempts++;

            logger.info(
                    "Scheduling retry attempt {} for node {}:{} in {} ms",
                    retryAttempts,
                    node.address(),
                    node.port(),
                    delayMillis);
            scheduler.schedule(this::reconnect, delayMillis, TimeUnit.MILLISECONDS);
        } else {
            logger.error(
                    "Max retry attempts ({}) reached for node {}:{}. Giving up.",
                    MAX_RETRY_ATTEMPTS,
                    node.address(),
                    node.port());
            addNodeInBackoff(node);
        }
    }

    private void reconnect() {
        if (Instant.now().isAfter(nextRetryTime)) {
            logger.info("Attempting retry {} for node {}:{}", retryAttempts, node.address(), node.port());
            establishStream();
        }
    }

    private void addNodeInBackoff(BlockNodeConfig node) {
        manager.addNodeInBackoff(node);
    }

    private void removeFromActiveConnections(BlockNodeConfig node) {
        manager.handleConnectionError(node);
    }

    public void sendRequest(PublishStreamRequest request) {
        if (isActive) {
            requestObserver.onNext(request);
        }
    }

    public void close() {
        if (isActive) {
            isActive = false;
            requestObserver.onCompleted();
            scheduler.shutdown();
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public BlockNodeConfig getNodeConfig() {
        return node;
    }
}
