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

package com.hedera.node.app.blocks.impl;

import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.protoc.BlockItemSet;
import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.internal.network.BlockNodeConfig;
import com.hedera.node.internal.network.BlockNodeConnectionInfo;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Manages connections to block nodes, including connection lifecycle, node selection,
 * and error handling with exponential backoff.
 */
public class BlockNodeConnectionManager {
    private static final Logger logger = LogManager.getLogger(BlockNodeConnectionManager.class);
    private static final String GRPC_END_POINT =
            BlockStreamServiceGrpc.getPublishBlockStreamMethod().getBareMethodName();
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;

    private final List<BlockNodeConfig> allNodes;
    private final int maxSimultaneousConnections;
    private final Duration nodeReselectionInterval;
    private final Map<BlockNodeConfig, BlockNodeConnection> activeConnections;
    private final Map<BlockNodeConfig, Integer> retryAttempts;
    private final Map<BlockNodeConfig, Instant> nextRetryTime;
    private final Set<BlockNodeConfig> nodesInBackoff;
    private final ScheduledExecutorService scheduler;
    private int availableNonPreferredSlots;

    private final ReentrantLock connectionLock = new ReentrantLock();

    // Track the current block state
    private static class BlockState {
        final long blockNumber;
        final List<Bytes> itemBytes;

        BlockState(long blockNumber) {
            this.blockNumber = blockNumber;
            this.itemBytes = new ArrayList<>();
        }
    }

    private final Map<Long, BlockState> blockStates = new ConcurrentHashMap<>();
    private final ReentrantLock blockStateLock = new ReentrantLock();
    private volatile BlockState currentBlock;
    private final int blockItemBatchSize;
    private final ExecutorService streamingExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new BlockNodeConnectionManager with the given configuration from disk.
     */
    public BlockNodeConnectionManager(@NonNull final ConfigProvider configProvider) {
        requireNonNull(configProvider);
        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        final var configPath = Paths.get(blockStreamConfig.blockNodeConnectionFileDir(), "block-nodes.json");

        try {
            byte[] jsonConfig = Files.readAllBytes(configPath);
            BlockNodeConnectionInfo protoConfig = BlockNodeConnectionInfo.JSON.parse(Bytes.wrap(jsonConfig));

            // Convert proto config to internal config objects
            this.allNodes = protoConfig.nodes().stream()
                    .map(node -> new BlockNodeConfig(node.priority(), node.address(), node.port(), node.preferred()))
                    .collect(Collectors.toList());

            logger.info("Loaded block node configuration from {}", configPath);
            logger.info("Block node configuration: {}", allNodes);

            this.maxSimultaneousConnections = protoConfig.maxSimultaneousConnections();
            this.nodeReselectionInterval = Duration.ofSeconds(protoConfig.nodeReselectionInterval());
            this.blockItemBatchSize = protoConfig.blockItemBatchSize();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read block node configuration from " + configPath, e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        this.activeConnections = new ConcurrentHashMap<>();
        this.retryAttempts = new ConcurrentHashMap<>();
        this.nextRetryTime = new ConcurrentHashMap<>();
        this.nodesInBackoff = ConcurrentHashMap.newKeySet();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.availableNonPreferredSlots = maxSimultaneousConnections;

        // Sort nodes by priority (lowest number = highest priority)
        allNodes.sort(Comparator.comparingInt(BlockNodeConfig::priority));

        // Schedule periodic node reselection
        scheduler.scheduleAtFixedRate(
                this::performNodeReselection,
                nodeReselectionInterval.toSeconds(),
                nodeReselectionInterval.toSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * Gets all active block node connections.
     * @return List of active connections
     */
    public List<BlockNodeConnection> getActiveConnections() {
        return new ArrayList<>(activeConnections.values());
    }

    /**
     * Attempts to establish connections to block nodes based on priority and configuration.
     */
    public void establishConnections() {
        logger.info(
                "Establishing connections to block nodes... (Available non-preferred slots: {})",
                availableNonPreferredSlots);

        // First, connect to all preferred nodes that we haven't connected to yet
        allNodes.stream()
                .filter(BlockNodeConfig::preferred)
                .filter(node -> !activeConnections.containsKey(node))
                .filter(node -> !nodesInBackoff.contains(node))
                .forEach(this::connectToNode);

        // Then connect to non-preferred nodes by priority, respecting max connections limit
        if (availableNonPreferredSlots > 0) {
            // Get all non-preferred nodes we haven't connected to yet
            List<BlockNodeConfig> availableNodes = allNodes.stream()
                    .filter(node -> !node.preferred())
                    .filter(node -> !activeConnections.containsKey(node))
                    .filter(node -> !nodesInBackoff.contains(node))
                    .collect(Collectors.toList());

            // Shuffle the list
            Collections.shuffle(availableNodes);

            // Take up to availableNonPreferredSlots nodes
            availableNodes.stream().limit(availableNonPreferredSlots).forEach(node -> {
                connectToNode(node);
                availableNonPreferredSlots--;
            });
        }
    }

    private void performNodeReselection() {
        // Don't replace preferred nodes
        List<BlockNodeConfig> nonPreferredConnections = activeConnections.keySet().stream()
                .filter(node -> !node.preferred())
                .toList();

        // Disconnect from non-preferred nodes
        for (BlockNodeConfig node : nonPreferredConnections) {
            disconnectFromNode(node);
        }

        // Reset available slots for non-preferred nodes
        availableNonPreferredSlots = maxSimultaneousConnections;

        // Establish new connections
        establishConnections();
    }

    private void connectToNode(BlockNodeConfig node) {
        logger.info("Connecting to block node {}:{}", node.address(), node.port());

        // Check if we're still in backoff period
        connectionLock.lock();
        try {
            Instant now = Instant.now();
            if (nextRetryTime.containsKey(node) && now.isBefore(nextRetryTime.get(node))) {
                return;
            }
        } finally {
            connectionLock.unlock();
        }

        try {
            GrpcClient client = GrpcClient.builder()
                    .tls(Tls.builder().enabled(false).build())
                    .baseUri(new URI(null, null, node.address(), node.port(), null, null, null))
                    .protocolConfig(GrpcClientProtocolConfig.builder()
                            .abortPollTimeExpired(false)
                            .build())
                    .build();

            GrpcServiceClient grpcServiceClient = client.serviceClient(GrpcServiceDescriptor.builder()
                    .serviceName(BlockStreamServiceGrpc.SERVICE_NAME)
                    .putMethod(
                            GRPC_END_POINT,
                            GrpcClientMethodDescriptor.bidirectional(
                                            BlockStreamServiceGrpc.SERVICE_NAME, GRPC_END_POINT)
                                    .requestType(PublishStreamRequest.class)
                                    .responseType(PublishStreamResponse.class)
                                    .build())
                    .build());

            BlockNodeConnection connection = new BlockNodeConnection(node, grpcServiceClient, this);

            connectionLock.lock();
            try {
                activeConnections.put(node, connection);
                retryAttempts.remove(node);
                nextRetryTime.remove(node);
                nodesInBackoff.remove(node);
            } finally {
                connectionLock.unlock();
            }

            logger.info("Successfully connected to block node {}:{}", node.address(), node.port());
        } catch (URISyntaxException | RuntimeException e) {
            handleConnectionFailure(node);
            logger.error("Failed to connect to block node {}:{}", node.address(), node.port(), e);
        }
    }

    /**
     * Handles connection errors from a BlockNodeConnection by removing the failed connection
     * and initiating the reconnection process.
     *
     * @param node the node configuration for the failed connection
     */
    public void handleConnectionError(BlockNodeConfig node) {
        connectionLock.lock();
        try {
            activeConnections.remove(node); // Remove the failed connection
            handleConnectionFailure(node); // Schedule reconnection attempt
        } finally {
            connectionLock.unlock();
        }
    }

    private void handleConnectionFailure(BlockNodeConfig node) {
        connectionLock.lock();
        try {
            int attempts = retryAttempts.getOrDefault(node, 0) + 1;
            retryAttempts.put(node, attempts);
            nodesInBackoff.add(node);

            if (attempts <= MAX_RETRY_ATTEMPTS) {
                // Calculate next retry time with exponential backoff
                long delayMillis =
                        (long) (INITIAL_RETRY_DELAY.toMillis() * Math.pow(RETRY_BACKOFF_MULTIPLIER, attempts - 1));
                Instant nextRetry = Instant.now().plusMillis(delayMillis);
                nextRetryTime.put(node, nextRetry);

                logger.info(
                        "Scheduling retry attempt {} for node {}:{} in {} ms",
                        attempts,
                        node.address(),
                        node.port(),
                        delayMillis);

                // Schedule the retry attempt
                scheduler.schedule(
                        () -> {
                            logger.info("Attempting retry {} for node {}:{}", attempts, node.address(), node.port());
                            connectToNode(node);
                        },
                        delayMillis,
                        TimeUnit.MILLISECONDS);
            } else {
                logger.error(
                        "Max retry attempts ({}) reached for node {}:{}. Removing from connection pool.",
                        MAX_RETRY_ATTEMPTS,
                        node.address(),
                        node.port());
                retryAttempts.remove(node);
                nextRetryTime.remove(node);
                nodesInBackoff.remove(node);
                availableNonPreferredSlots++; // Free up the slot for another node
                establishConnections(); // Try to establish connection with another node
            }
        } finally {
            connectionLock.unlock();
        }
    }

    private synchronized void disconnectFromNode(BlockNodeConfig node) {
        BlockNodeConnection connection = activeConnections.remove(node);
        if (connection != null) {
            connection.close();
            if (!node.preferred()) {
                availableNonPreferredSlots++;
            }
            logger.info("Disconnected from block node {}:{}", node.address(), node.port());
        }

        // Also clean up any retry state
        retryAttempts.remove(node);
        nextRetryTime.remove(node);
        nodesInBackoff.remove(node);
    }

    public void shutdown() {
        scheduler.shutdown();
        streamingExecutor.shutdown();
        for (BlockNodeConfig node : new ArrayList<>(activeConnections.keySet())) {
            disconnectFromNode(node);
        }
    }

    /**
     * Represents a connection to a block node, managing the gRPC bidirectional stream.
     */
    public static class BlockNodeConnection {
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
            requestObserver = grpcServiceClient.bidi(GRPC_END_POINT, new StreamObserver<PublishStreamResponse>() {
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
                    logger.error("Error in block node stream {}:{}: {}", config.address(), config.port(), status, t);
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

    public void openBlock(long blockNumber) {
        blockStateLock.lock();
        try {
            currentBlock = new BlockState(blockNumber);
            blockStates.put(blockNumber, currentBlock);
            logger.info("Started new block in BlockNodeConnectionManager {}", blockNumber);
        } finally {
            blockStateLock.unlock();
        }
    }

    public void addPbjItem(Bytes bytes) {
        blockStateLock.lock();
        try {
            if (currentBlock == null) {
                throw new IllegalStateException("Received block item before opening block");
            }
            currentBlock.itemBytes.add(bytes);
        } finally {
            blockStateLock.unlock();
        }
    }

    public void closeBlock() {
        blockStateLock.lock();
        try {
            if (currentBlock == null) {
                throw new IllegalStateException("Received close block before opening block");
            }
            final long blockNumber = currentBlock.blockNumber;
            // Stream the block asynchronously
            streamingExecutor.execute(() -> streamBlockToConnections(blockNumber));

            logger.info("Closed block in BlockNodeConnectionManager {}", blockNumber);
            currentBlock = null;
        } finally {
            blockStateLock.unlock();
        }
    }

    private void streamBlockToConnections(long blockNumber) {
        BlockState block = blockStates.get(blockNumber);
        if (block == null) {
            logger.error("Could not find block state for block {}", blockNumber);
            return;
        }

        try {
            // Get currently active connections
            List<BlockNodeConnection> connectionsToStream;
            connectionLock.lock();
            try {
                connectionsToStream = activeConnections.values().stream()
                        .filter(BlockNodeConnection::isActive)
                        .toList();
            } finally {
                connectionLock.unlock();
            }

            if (connectionsToStream.isEmpty()) {
                logger.info("No active connections to stream block {}", blockNumber);
                return;
            }

            logger.info("Streaming block {} to {} active connections", blockNumber, connectionsToStream.size());

            // Create all batches once
            List<PublishStreamRequest> batchRequests = new ArrayList<>();
            for (int i = 0; i < block.itemBytes.size(); i += blockItemBatchSize) {
                int end = Math.min(i + blockItemBatchSize, block.itemBytes.size());
                List<Bytes> batch = block.itemBytes.subList(i, end);
                List<com.hedera.hapi.block.stream.protoc.BlockItem> protocBlockItems = new ArrayList<>();
                batch.forEach(batchItem -> {
                    try {
                        protocBlockItems.add(
                                com.hedera.hapi.block.stream.protoc.BlockItem.parseFrom(batchItem.toByteArray()));
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });

                // Create BlockItemSet by adding all items at once
                BlockItemSet itemSet = BlockItemSet.newBuilder()
                        .addAllBlockItems(protocBlockItems)
                        .build();

                batchRequests.add(PublishStreamRequest.newBuilder()
                        .setBlockItems(itemSet)
                        .build());
            }

            // Stream prepared batches to each connection
            for (BlockNodeConnection connection : connectionsToStream) {
                try {
                    for (PublishStreamRequest request : batchRequests) {
                        connection.sendRequest(request);
                    }
                    logger.info(
                            "Successfully streamed block {} to {}:{}",
                            blockNumber,
                            connection.getConfig().address(),
                            connection.getConfig().port());
                } catch (Exception e) {
                    logger.error(
                            "Failed to stream block {} to {}:{}",
                            blockNumber,
                            connection.getConfig().address(),
                            connection.getConfig().port(),
                            e);
                }
            }
        } finally {
            // Clean up the block state after streaming
            blockStates.remove(blockNumber);
        }
    }
}
