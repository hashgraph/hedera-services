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

import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.protoc.BlockItemSet;
import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.internal.network.BlockNodeConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final Map<BlockNodeConfig, BlockNodeConnection> activeConnections;
    private final Set<BlockNodeConfig> nodesInBackoff;
    private BlockNodeConfigExtractor blockNodeConfigurations;

    private final ReentrantLock connectionLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService streamingExecutor = Executors.newSingleThreadExecutor();
    private int availableNonPreferredSlots = 0;

    /**
     * Creates a new BlockNodeConnectionManager with the given configuration from disk.
     * @param configProvider the configuration provider
     */
    public BlockNodeConnectionManager(@NonNull final ConfigProvider configProvider) {
        requireNonNull(configProvider);
        this.activeConnections = new ConcurrentHashMap<>();
        this.nodesInBackoff = ConcurrentHashMap.newKeySet();

        final var blockStreamConfig = configProvider.getConfiguration().getConfigData(BlockStreamConfig.class);
        if (!blockStreamConfig.streamToBlockNodes()) {
            return;
        }
        this.blockNodeConfigurations = new BlockNodeConfigExtractor(blockStreamConfig.blockNodeConnectionFileDir());
        final Duration nodeReselectionInterval = blockNodeConfigurations.getNodeReselectionInterval();

        // Schedule periodic node reselection
        scheduler.scheduleAtFixedRate(
                this::performNodeReselection,
                nodeReselectionInterval.toSeconds(),
                nodeReselectionInterval.toSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * Attempts to establish connections to block nodes based on priority and configuration.
     */
    private void establishConnections() {
        logger.info(
                "Establishing connections to block nodes... (Available non-preferred slots: {})",
                availableNonPreferredSlots);

        // First, connect to all preferred nodes that we haven't connected to yet
        blockNodeConfigurations.getAllNodes().stream()
                .filter(this::preferredNode)
                .filter(node -> !activeConnections.containsKey(node))
                .filter(node -> !nodesInBackoff.contains(node))
                .forEach(this::connectToNode);

        // Then connect to non-preferred nodes by priority, respecting max connections limit
        if (availableNonPreferredSlots > 0) {
            // Get all non-preferred nodes we haven't connected to yet
            List<BlockNodeConfig> availableNodes = blockNodeConfigurations.getAllNodes().stream()
                    .filter(node -> !preferredNode(node))
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
                .filter(node -> !preferredNode(node))
                .toList();

        // Disconnect from non-preferred nodes
        for (BlockNodeConfig node : nonPreferredConnections) {
            disconnectFromNode(node);
        }
        // Reset available slots for non-preferred nodes
        availableNonPreferredSlots = blockNodeConfigurations.getMaxSimultaneousConnections();

        // Establish new connections
        establishConnections();
    }

    private void connectToNode(@NonNull BlockNodeConfig node) {
        logger.info("Connecting to block node {}:{}", node.address(), node.port());
        try {
            GrpcClient client = GrpcClient.builder()
                    .tls(Tls.builder().enabled(false).build())
                    .baseUri(new URI("http://" + node.address() + ":" + node.port()))
                    .protocolConfig(GrpcClientProtocolConfig.builder()
                            .abortPollTimeExpired(false)
                            .build())
                    .keepAlive(true)
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
                nodesInBackoff.remove(node);
            } finally {
                connectionLock.unlock();
            }
            logger.info("Successfully connected to block node {}:{}", node.address(), node.port());
        } catch (URISyntaxException | RuntimeException e) {
            logger.error("Failed to connect to block node {}:{}", node.address(), node.port(), e);
        }
    }

    private synchronized void disconnectFromNode(@NonNull BlockNodeConfig node) {
        BlockNodeConnection connection = activeConnections.remove(node);
        if (connection != null) {
            connection.close();
            if (!preferredNode(node)) {
                availableNonPreferredSlots++;
            }
            logger.info("Disconnected from block node {}:{}", node.address(), node.port());
        }
        nodesInBackoff.remove(node);
    }

    private void streamBlockToConnections(@NonNull BlockState block) {
        long blockNumber = block.blockNumber();
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
        final int blockItemBatchSize = blockNodeConfigurations.getBlockItemBatchSize();
        for (int i = 0; i < block.itemBytes().size(); i += blockItemBatchSize) {
            int end = Math.min(i + blockItemBatchSize, block.itemBytes().size());
            List<Bytes> batch = block.itemBytes().subList(i, end);
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
            BlockItemSet itemSet =
                    BlockItemSet.newBuilder().addAllBlockItems(protocBlockItems).build();

            batchRequests.add(
                    PublishStreamRequest.newBuilder().setBlockItems(itemSet).build());
        }

        // Stream prepared batches to each connection
        for (BlockNodeConnection connection : connectionsToStream) {
            final var connectionNodeConfig = connection.getNodeConfig();
            try {
                for (PublishStreamRequest request : batchRequests) {
                    connection.sendRequest(request);
                }
                logger.info(
                        "Successfully streamed block {} to {}:{}",
                        blockNumber,
                        connectionNodeConfig.address(),
                        connectionNodeConfig.port());
            } catch (Exception e) {
                logger.error(
                        "Failed to stream block {} to {}:{}",
                        blockNumber,
                        connectionNodeConfig.address(),
                        connectionNodeConfig.port(),
                        e);
            }
        }
    }

    private boolean preferredNode(@NonNull BlockNodeConfig node) {
        // priority equals 1 means the node is preferred
        return node.priority() == 1;
    }

    /**
     * Initiates the streaming of a block to all active connections.
     *
     * @param block the block to be streamed
     */
    public void startStreamingBlock(@NonNull BlockState block) {
        streamingExecutor.execute(() -> streamBlockToConnections(block));
    }

    /**
     * Handles connection errors from a BlockNodeConnection by removing the failed connection
     * and initiating the reconnection process.
     *
     * @param node the node configuration for the failed connection
     */
    public void handleConnectionError(@NonNull BlockNodeConfig node) {
        connectionLock.lock();
        try {
            activeConnections.remove(node); // Remove the failed connection
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Shuts down the connection manager, closing all active connections.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            boolean awaitTermination = streamingExecutor.awaitTermination(10, TimeUnit.SECONDS);
            if (!awaitTermination) {
                logger.error("Failed to shut down streaming executor within 10 seconds");
            } else {
                logger.info("Successfully shut down streaming executor");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        for (BlockNodeConfig node : new ArrayList<>(activeConnections.keySet())) {
            disconnectFromNode(node);
        }
    }

    /**
     * Waits for at least one active connection to be established, with timeout.
     * @param timeout maximum time to wait
     * @return true if at least one connection was established, false if timeout occurred
     */
    public boolean waitForConnection(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        establishConnections();
        while (Instant.now().isBefore(deadline)) {
            if (!activeConnections.isEmpty()) {
                return true;
            }
            try {
                Thread.sleep(1000); // Wait 1 second between attempts
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * @param node the node that failed to connect and rescheduled for retry
     */
    public void addNodeInBackoff(@NonNull BlockNodeConfig node) {
        nodesInBackoff.add(node);
    }

    /**
     * @return the gRPC endpoint for publish block stream
     */
    public String getGrpcEndPoint() {
        return GRPC_END_POINT;
    }
}
