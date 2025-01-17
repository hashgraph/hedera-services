package com.hedera.node.app.blocks.impl;

import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.hapi.block.protoc.PublishStreamResponse.Acknowledgement;
import com.hedera.hapi.block.protoc.PublishStreamResponse.EndOfStream;
import com.hedera.hapi.block.protoc.PublishStreamResponseCode;
import com.hedera.node.app.blocks.config.BlockNodeConfig;
import com.hedera.node.app.blocks.config.BlockNodeConnectionInfo;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages connections to block nodes, including connection lifecycle, node selection,
 * and error handling with exponential backoff.
 */
public class BlockNodeConnectionManager {
    private static final Logger logger = LogManager.getLogger(BlockNodeConnectionManager.class);
    private static final String GRPC_END_POINT = BlockStreamServiceGrpc.getPublishBlockStreamMethod().getBareMethodName();
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;

    private final List<BlockNodeConfig> allNodes;
    private final int maxSimultaneousConnections;
    private final Duration nodeReselectionInterval;
    private final Map<BlockNodeConfig, BlockNodeConnection> activeConnections;
    private final Map<BlockNodeConfig, Integer> retryAttempts;
    private final Map<BlockNodeConfig, Instant> nextRetryTime;
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new BlockNodeConnectionManager with the given configuration from disk.
     */
    public BlockNodeConnectionManager() {
        BlockNodeConfig blockNodeConfig = new BlockNodeConfig(1, "localhost", 8080, true);
        BlockNodeConnectionInfo blockNodeConnectionInfo = new BlockNodeConnectionInfo(
                Collections.singletonList(blockNodeConfig), 3000, 1);
        this.allNodes = new ArrayList<>(blockNodeConnectionInfo.nodes());
        this.maxSimultaneousConnections = blockNodeConnectionInfo.maxSimultaneousConnections();
        this.nodeReselectionInterval = Duration.ofSeconds(blockNodeConnectionInfo.nodeReselectionInterval());
        this.activeConnections = new ConcurrentHashMap<>();
        this.retryAttempts = new ConcurrentHashMap<>();
        this.nextRetryTime = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

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
        // Group nodes by priority
        Map<Integer, List<BlockNodeConfig>> nodesByPriority = allNodes.stream()
                .collect(Collectors.groupingBy(BlockNodeConfig::priority));

        int remainingSlots = maxSimultaneousConnections;

        // First, connect to all preferred nodes
        List<BlockNodeConfig> preferredNodes = allNodes.stream()
                .filter(BlockNodeConfig::preferred)
                .toList();

        for (BlockNodeConfig node : preferredNodes) {
            if (remainingSlots <= 0) break;
            if (!activeConnections.containsKey(node)) {
                connectToNode(node);
                remainingSlots--;
            }
        }

        // Then connect to other nodes by priority
        for (Entry<Integer, List<BlockNodeConfig>> entry : nodesByPriority.entrySet()) {
            if (remainingSlots <= 0) break;

            List<BlockNodeConfig> nodes = entry.getValue().stream()
                    .filter(node -> !node.preferred()) // Skip preferred nodes as they're already handled
                    .filter(node -> !activeConnections.containsKey(node))
                    .collect(Collectors.toList());

            // Randomly select nodes from this priority level
            Collections.shuffle(nodes);
            for (BlockNodeConfig node : nodes) {
                if (remainingSlots <= 0) break;
                connectToNode(node);
                remainingSlots--;
            }
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

        // Establish new connections
        establishConnections();
    }

    private void connectToNode(BlockNodeConfig node) {
        // Check if we're still in backoff period
        Instant now = Instant.now();
        if (nextRetryTime.containsKey(node) && now.isBefore(nextRetryTime.get(node))) {
            return;
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
                            GrpcClientMethodDescriptor.bidirectional(BlockStreamServiceGrpc.SERVICE_NAME, GRPC_END_POINT)
                                    .requestType(PublishStreamRequest.class)
                                    .responseType(PublishStreamResponse.class)
                                    .build())
                    .build());

            BlockNodeConnection connection = new BlockNodeConnection(node, grpcServiceClient);
            activeConnections.put(node, connection);
            retryAttempts.remove(node);
            nextRetryTime.remove(node);

            logger.info("Successfully connected to block node {}:{}", node.address(), node.port());
        } catch (URISyntaxException | RuntimeException e) {
            handleConnectionFailure(node);
            logger.error("Failed to connect to block node {}:{}", node.address(), node.port(), e);
        }
    }

    private void handleConnectionFailure(BlockNodeConfig node) {
        int attempts = retryAttempts.getOrDefault(node, 0) + 1;
        retryAttempts.put(node, attempts);

        if (attempts <= MAX_RETRY_ATTEMPTS) {
            // Calculate next retry time with exponential backoff
            long delayMillis = (long) (INITIAL_RETRY_DELAY.toMillis() * Math.pow(RETRY_BACKOFF_MULTIPLIER, attempts - 1));
            Instant nextRetry = Instant.now().plusMillis(delayMillis);
            nextRetryTime.put(node, nextRetry);

            logger.info("Scheduling retry attempt {} for node {}:{} at {}", 
                    attempts, node.address(), node.port(), nextRetry);
        } else {
            logger.error("Max retry attempts ({}) reached for node {}:{}. Removing from connection pool.", 
                    MAX_RETRY_ATTEMPTS, node.address(), node.port());
            retryAttempts.remove(node);
            nextRetryTime.remove(node);
        }
    }

    private void disconnectFromNode(BlockNodeConfig node) {
        BlockNodeConnection connection = activeConnections.remove(node);
        if (connection != null) {
            connection.close();
            logger.info("Disconnected from block node {}:{}", node.address(), node.port());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
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
        private StreamObserver<PublishStreamRequest> requestObserver;
        private volatile boolean isActive = true;

        public BlockNodeConnection(BlockNodeConfig config, GrpcServiceClient grpcServiceClient) {
            this.config = config;
            this.grpcServiceClient = grpcServiceClient;
            establishStream();
        }

        private void establishStream() {
            requestObserver = grpcServiceClient.bidi(GRPC_END_POINT, new StreamObserver<PublishStreamResponse>() {
                @Override
                public void onNext(PublishStreamResponse response) {
                    if (response.hasAcknowledgement()) {
                        handleAcknowledgement(response.getAcknowledgement());
                    } else if (response.hasStatus()) {
                        handleEndOfStream(response.getStatus());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Status status = Status.fromThrowable(t);
                    logger.error("Error in block node stream {}:{}: {}", 
                            config.address(), config.port(), status, t);
                    isActive = false;
                }

                @Override
                public void onCompleted() {
                    logger.info("Stream completed for block node {}:{}", 
                            config.address(), config.port());
                    isActive = false;
                }
            });
        }

        private void handleAcknowledgement(Acknowledgement ack) {
            if (ack.hasBlockAck()) {
                logger.info("Block acknowledged by node {}:{}: {}", 
                        config.address(), config.port(), ack.getBlockAck());
            } else if (ack.hasItemAck()) {
                logger.debug("Item acknowledged by node {}:{}: {}", 
                        config.address(), config.port(), ack.getItemAck());
            }
        }

        private void handleEndOfStream(EndOfStream endOfStream) {
            if (endOfStream.getStatus() == PublishStreamResponseCode.STREAM_ITEMS_UNKNOWN) {
                logger.error("Unknown stream items error from node {}:{} at block {}", 
                        config.address(), config.port(), endOfStream.getBlockNumber());
            }
        }

        public void writeRequest(PublishStreamRequest request) {
            if (isActive) {
                requestObserver.onNext(request);
            } else {
                logger.warn("Attempted to write to inactive stream for node {}:{}", 
                        config.address(), config.port());
            }
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
} 