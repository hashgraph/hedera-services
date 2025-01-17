package com.hedera.node.app.blocks.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import com.hedera.hapi.block.protoc.PublishStreamResponse.Acknowledgement;
import com.hedera.hapi.block.protoc.PublishStreamResponse.EndOfStream;
import com.hedera.hapi.block.protoc.PublishStreamResponseCode;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.checkerframework.checker.nullness.qual.NonNull;

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
    private final Set<BlockNodeConfig> nodesInBackoff;
    private final ScheduledExecutorService scheduler;
    private int availableNonPreferredSlots;  // Renamed to clarify this only applies to non-preferred nodes

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
                .map(node -> new BlockNodeConfig(
                    node.priority(),
                    node.address(),
                    node.port(),
                    node.preferred(),
                    node.blockItemBatchSize()))
                .collect(Collectors.toList());

            logger.info("Loaded block node configuration from {}", configPath);
            logger.info("Block node configuration: {}", allNodes);
            
            this.maxSimultaneousConnections = protoConfig.maxSimultaneousConnections();
            this.nodeReselectionInterval = Duration.ofSeconds(protoConfig.nodeReselectionInterval());
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
        logger.info("Establishing connections to block nodes... (Available non-preferred slots: {})", availableNonPreferredSlots);
        
        // First, connect to all preferred nodes (no slot limit)
        List<BlockNodeConfig> preferredNodes = allNodes.stream()
                .filter(BlockNodeConfig::preferred)
                .filter(node -> !activeConnections.containsKey(node))
                .filter(node -> !nodesInBackoff.contains(node))
                .toList();

        for (BlockNodeConfig node : preferredNodes) {
            connectToNode(node);
        }

        // Then connect to other nodes by priority, respecting max connections limit
        Map<Integer, List<BlockNodeConfig>> nodesByPriority = allNodes.stream()
                .filter(node -> !node.preferred()) // Skip preferred nodes as they're already handled
                .filter(node -> !activeConnections.containsKey(node))
                .filter(node -> !nodesInBackoff.contains(node))
                .collect(Collectors.groupingBy(BlockNodeConfig::priority));

        for (Entry<Integer, List<BlockNodeConfig>> entry : nodesByPriority.entrySet()) {
            if (availableNonPreferredSlots <= 0) break;

            List<BlockNodeConfig> nodes = new ArrayList<>(entry.getValue());
            Collections.shuffle(nodes);
            
            for (BlockNodeConfig node : nodes) {
                if (availableNonPreferredSlots <= 0) break;
                connectToNode(node);
                if (!node.preferred()) {
                    availableNonPreferredSlots--;
                }
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

        // Reset available slots for non-preferred nodes
        availableNonPreferredSlots = maxSimultaneousConnections;

        // Establish new connections
        establishConnections();
    }

    private void connectToNode(BlockNodeConfig node) {
        logger.info("Connecting to block node {}:{}", node.address(), node.port());
        
        // Synchronize access to retry state
        synchronized (this) {
            // Check if we're still in backoff period
            Instant now = Instant.now();
            if (nextRetryTime.containsKey(node) && now.isBefore(nextRetryTime.get(node))) {
                return;
            }
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

            BlockNodeConnection connection = new BlockNodeConnection(node, grpcServiceClient, this);
            
            // Synchronize updates to connection and retry state
            synchronized (this) {
                activeConnections.put(node, connection);
                
                // Reset retry state on successful connection
                retryAttempts.remove(node);
                nextRetryTime.remove(node);
                nodesInBackoff.remove(node);
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
    public synchronized void handleConnectionError(BlockNodeConfig node) {
        activeConnections.remove(node);  // Remove the failed connection
        handleConnectionFailure(node);   // Schedule reconnection attempt
    }

    private synchronized void handleConnectionFailure(BlockNodeConfig node) {
        int attempts = retryAttempts.getOrDefault(node, 0) + 1;
        retryAttempts.put(node, attempts);
        nodesInBackoff.add(node);

        if (attempts <= MAX_RETRY_ATTEMPTS) {
            // Calculate next retry time with exponential backoff
            long delayMillis = (long) (INITIAL_RETRY_DELAY.toMillis() * Math.pow(RETRY_BACKOFF_MULTIPLIER, attempts - 1));
            Instant nextRetry = Instant.now().plusMillis(delayMillis);
            nextRetryTime.put(node, nextRetry);

            logger.info("Scheduling retry attempt {} for node {}:{} in {} ms", 
                    attempts, node.address(), node.port(), delayMillis);

            // Schedule the retry attempt
            scheduler.schedule(() -> {
                logger.info("Attempting retry {} for node {}:{}", attempts, node.address(), node.port());
                connectToNode(node);
            }, delayMillis, TimeUnit.MILLISECONDS);
        } else {
            logger.error("Max retry attempts ({}) reached for node {}:{}. Removing from connection pool.", 
                    MAX_RETRY_ATTEMPTS, node.address(), node.port());
            retryAttempts.remove(node);
            nextRetryTime.remove(node);
            nodesInBackoff.remove(node);
            availableNonPreferredSlots++; // Free up the slot for another node
            establishConnections(); // Try to establish connection with another node
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
                BlockNodeConfig config, 
                GrpcServiceClient grpcServiceClient,
                BlockNodeConnectionManager manager) {
            this.config = config;
            this.grpcServiceClient = grpcServiceClient;
            this.manager = manager;
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
                    manager.handleConnectionError(config);  // Notify manager of error
                }

                @Override
                public void onCompleted() {
                    logger.info("Stream completed for block node {}:{}", 
                            config.address(), config.port());
                    isActive = false;
                    manager.handleConnectionError(config);  // Also handle normal stream completion as an error
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