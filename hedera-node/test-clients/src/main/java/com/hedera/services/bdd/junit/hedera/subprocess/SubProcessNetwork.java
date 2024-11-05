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

package com.hedera.services.bdd.junit.hedera.subprocess;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.ADDRESS_BOOK;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.awaitStatus;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.hadCorrelatedBindException;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.classicMetadataFor;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.configTxtForLocal;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.CONFIG_TXT;
import static com.hedera.services.bdd.spec.TargetNetworkType.SUBPROCESS_NETWORK;
import static com.hedera.services.bdd.suites.utils.sysfiles.BookEntryPojo.asOctets;
import static com.swirlds.common.PlatformStatus.ACTIVE;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.toSet;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension;
import com.hedera.services.bdd.junit.hedera.AbstractGrpcNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNode.ReassignPorts;
import com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.TargetNetworkType;
import com.hedera.services.bdd.spec.infrastructure.HapiClients;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A network of Hedera nodes started in subprocesses and accessed via gRPC. Unlike
 * nodes in a remote or embedded network, its nodes support lifecycle operations like
 * stopping and restarting.
 */
public class SubProcessNetwork extends AbstractGrpcNetwork implements HederaNetwork {
    private static final Logger log = LogManager.getLogger(SubProcessNetwork.class);

    // We need 6 ports for each node in the network (gRPC, gRPC TLS, gRPC node operator, gossip, gossip TLS, prometheus)
    private static final int PORTS_PER_NODE = 6;
    private static final int MAX_PORT_REASSIGNMENTS = 3;
    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final int FIRST_CANDIDATE_PORT = 30000;
    private static final int LAST_CANDIDATE_PORT = 40000;

    private static final String SUBPROCESS_HOST = "127.0.0.1";
    private static final ByteString SUBPROCESS_ENDPOINT = asOctets(SUBPROCESS_HOST);
    private static final String SHARED_NETWORK_NAME = "SHARED_NETWORK";
    private static final GrpcPinger GRPC_PINGER = new GrpcPinger();
    private static final PrometheusClient PROMETHEUS_CLIENT = new PrometheusClient();

    // We initialize these randomly to reduce risk of port binding conflicts in CI runners
    private static int nextGrpcPort;
    private static int nextNodeOperatorPort;
    private static int nextGossipPort;
    private static int nextGossipTlsPort;
    private static int nextPrometheusPort;
    private static boolean nextPortsInitialized = false;

    private final AtomicReference<CompletableFuture<Void>> ready = new AtomicReference<>();

    private long maxNodeId;
    private String configTxt;
    private final String genesisConfigTxt;

    private SubProcessNetwork(@NonNull final String networkName, @NonNull final List<SubProcessNode> nodes) {
        super(networkName, nodes.stream().map(node -> (HederaNode) node).toList());
        this.maxNodeId =
                Collections.max(nodes.stream().map(SubProcessNode::getNodeId).toList());
        this.configTxt = configTxtForLocal(name(), nodes(), nextGossipPort, nextGossipTlsPort);
        this.genesisConfigTxt = configTxt;
    }

    /**
     * Creates a shared network of sub-process nodes with the given size.
     *
     * @param size the number of nodes in the network
     * @return the shared network
     */
    public static synchronized HederaNetwork newSharedNetwork(final int size) {
        if (NetworkTargetingExtension.SHARED_NETWORK.get() != null) {
            throw new UnsupportedOperationException("Only one shared network allowed per launcher session");
        }
        final var sharedNetwork = liveNetwork(SHARED_NETWORK_NAME, size);
        NetworkTargetingExtension.SHARED_NETWORK.set(sharedNetwork);
        return sharedNetwork;
    }

    /**
     * Returns the network type; for now this is always
     * {@link TargetNetworkType#SUBPROCESS_NETWORK}.
     *
     * @return the network type
     */
    @Override
    public TargetNetworkType type() {
        return SUBPROCESS_NETWORK;
    }

    /**
     * Starts all nodes in the network.
     */
    @Override
    public void start() {
        nodes.forEach(node -> node.initWorkingDir(configTxt).start());
    }

    /**
     * Forcibly stops all nodes in the network.
     */
    @Override
    public void terminate() {
        nodes.forEach(HederaNode::stopFuture);
    }

    /**
     * Waits for all nodes in the network to be ready within the given timeout.
     */
    @Override
    public void awaitReady(@NonNull final Duration timeout) {
        if (ready.get() == null) {
            final var future = runAsync(() -> {
                AssertionError error = null;
                var retries = MAX_PORT_REASSIGNMENTS;
                var bindException = false;
                do {
                    if (bindException) {
                        log.warn("Bind exception detected, retrying network initialization");
                        // Completely rebuild the network and try again
                        nodes.forEach(node -> {
                            node.stopFuture()
                                    .orTimeout(ProcessUtils.STOP_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                                    .join();
                            // Begins by deleting the working directory
                            node.initWorkingDir(configTxt);
                        });
                        assignNewMetadata(ReassignPorts.YES);
                        clients = null;
                        start();
                    }
                    final var deadline = Instant.now().plus(timeout);
                    try {
                        nodes.forEach(node -> awaitStatus(node, ACTIVE, Duration.between(Instant.now(), deadline)));
                        this.clients = HapiClients.clientsFor(this);
                    } catch (AssertionError e) {
                        error = e;
                        bindException = hadCorrelatedBindException(error);
                    }
                } while (clients == null && bindException && retries-- > 0);
                if (clients == null) {
                    throw error;
                }
            });
            if (!ready.compareAndSet(null, future)) {
                // We only need one thread to wait for readiness
                future.cancel(true);
            }
        }
        ready.get().join();
    }

    /**
     * Returns the genesis <i>config.txt</i> file for the network.
     * @return the genesis <i>config.txt</i> file
     */
    public String genesisConfigTxt() {
        return genesisConfigTxt;
    }

    /**
     * Assigns updated metadata to nodes from the current <i>config.txt</i>.
     * <p>Also reassigns ports and overwrites the existing <i>config.txt</i> file for each node in the
     * network with new ports if requested to avoid port binding issues in test environments.
     */
    public void assignNewMetadata(@NonNull final ReassignPorts reassignPorts) {
        requireNonNull(reassignPorts);
        if (reassignPorts == ReassignPorts.YES) {
            log.info("Reassigning ports for network '{}' starting from {}", name(), nextGrpcPort);
            reinitializePorts();
            log.info("  -> Network '{}' ports now starting from {}", name(), nextGrpcPort);
            nodes.forEach(node -> {
                final int nodeId = (int) node.getNodeId();
                configTxt = withReassignedPorts(
                        configTxt, nodeId, nextGossipPort + nodeId * 2, nextGossipTlsPort + nodeId * 2);
                ((SubProcessNode) node)
                        .reassignPorts(
                                nextGrpcPort + nodeId * 2,
                                nextNodeOperatorPort + nodeId,
                                nextGossipPort + nodeId * 2,
                                nextGossipTlsPort + nodeId * 2,
                                nextPrometheusPort + nodeId);
            });
        }
        nodes.forEach(node -> ((SubProcessNode) node).reassignNodeAccountIdFrom(memoOfNode(node.getNodeId())));
        refreshNodeConfigTxt();
        HapiClients.tearDown();
        this.clients = HapiClients.clientsFor(this);
    }

    /**
     * Removes the matching node from the network and updates the <i>config.txt</i> file for the remaining nodes
     * from the given source.
     *
     * @param selector the selector for the node to remove
     * @param upgradeConfigTxt the upgrade address book source
     */
    public void removeNode(@NonNull final NodeSelector selector, @NonNull final UpgradeConfigTxt upgradeConfigTxt) {
        requireNonNull(selector);
        requireNonNull(upgradeConfigTxt);
        final var node = getRequiredNode(selector);
        node.stopFuture();
        nodes.remove(node);
        configTxt = switch (upgradeConfigTxt) {
            case IMPLIED_BY_NETWORK_NODES -> configTxtForLocal(networkName, nodes, nextGossipPort, nextGossipTlsPort);
            case DAB_GENERATED -> consensusDabConfigTxt();};
        refreshNodeConfigTxt();
    }

    /**
     * Adds a node with the given id to the network and updates the <i>config.txt</i> file for the remaining nodes
     * from the given source.
     *
     * @param nodeId the id of the node to add
     * @param upgradeConfigTxt the upgrade address book source
     */
    public void addNode(final long nodeId, @NonNull final UpgradeConfigTxt upgradeConfigTxt) {
        requireNonNull(upgradeConfigTxt);
        final var i = Collections.binarySearch(
                nodes.stream().map(HederaNode::getNodeId).toList(), nodeId);
        if (i >= 0) {
            throw new IllegalArgumentException("Node with id " + nodeId + " already exists in network");
        }
        this.maxNodeId = Math.max(maxNodeId, nodeId);
        final var insertionPoint = -i - 1;
        nodes.add(
                insertionPoint,
                new SubProcessNode(
                        classicMetadataFor(
                                (int) nodeId,
                                name(),
                                SUBPROCESS_HOST,
                                SHARED_NETWORK_NAME.equals(name()) ? null : name(),
                                nextGrpcPort + (int) nodeId * 2,
                                nextNodeOperatorPort + (int) nodeId * 2,
                                nextGossipPort + (int) nodeId * 2,
                                nextGossipTlsPort + (int) nodeId * 2,
                                nextPrometheusPort + (int) nodeId),
                        GRPC_PINGER,
                        PROMETHEUS_CLIENT));
        configTxt = switch (upgradeConfigTxt) {
            case IMPLIED_BY_NETWORK_NODES -> configTxtForLocal(networkName, nodes, nextGossipPort, nextGossipTlsPort);
            case DAB_GENERATED -> consensusDabConfigTxt(node -> node.getNodeId() != nodeId);};
        ((SubProcessNode) nodes.get(insertionPoint)).initWorkingDir(configTxt);
        refreshNodeConfigTxt();
    }

    /**
     * Returns the gossip endpoints that can be automatically managed by this {@link SubProcessNetwork}
     * for the given node id.
     *
     * @return the gossip endpoints
     */
    public List<ServiceEndpoint> gossipEndpointsForNextNodeId() {
        final var nextNodeId = maxNodeId + 1;
        return List.of(
                endpointFor(nextGossipPort + (int) nextNodeId * 2),
                endpointFor(nextGossipTlsPort + (int) nextNodeId * 2));
    }

    /**
     * Returns the gRPC endpoint that can be automatically managed by this {@link SubProcessNetwork}
     * for the given node id.
     *
     * @return the gRPC endpoint
     */
    public ServiceEndpoint grpcEndpointForNextNodeId() {
        final var nextNodeId = maxNodeId + 1;
        return endpointFor(nextGrpcPort + (int) nextNodeId * 2);
    }

    @Override
    protected HapiPropertySource networkOverrides() {
        return WorkingDirUtils.hapiTestStartupProperties();
    }

    /**
     * Creates a network of live (sub-process) nodes with the given name and size. This method is
     * synchronized because we don't want to re-use any ports across different networks.
     *
     * @param name the name of the network
     * @param size the number of nodes in the network
     * @return the network
     */
    private static synchronized HederaNetwork liveNetwork(@NonNull final String name, final int size) {
        if (!nextPortsInitialized) {
            initializeNextPortsForNetwork(size);
        }
        final var network = new SubProcessNetwork(
                name,
                IntStream.range(0, size)
                        .mapToObj(nodeId -> new SubProcessNode(
                                classicMetadataFor(
                                        nodeId,
                                        name,
                                        SUBPROCESS_HOST,
                                        SHARED_NETWORK_NAME.equals(name) ? null : name,
                                        nextGrpcPort,
                                        nextNodeOperatorPort,
                                        nextGossipPort,
                                        nextGossipTlsPort,
                                        nextPrometheusPort),
                                GRPC_PINGER,
                                PROMETHEUS_CLIENT))
                        .toList());
        Runtime.getRuntime().addShutdownHook(new Thread(network::terminate));
        return network;
    }

    private void refreshNodeConfigTxt() {
        log.info("Refreshing config.txt for network '{}' - \n{}", name(), configTxt);
        nodes.forEach(node -> {
            final var configTxtLoc = node.getExternalPath(ADDRESS_BOOK);
            try {
                Files.writeString(configTxtLoc, configTxt);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private String memoOfNode(final long id) {
        return Arrays.stream(configTxt.split("\n"))
                .filter(line -> line.startsWith("address, " + id))
                .map(line -> line.substring(line.lastIndexOf(",") + 2))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No metadata found for node " + id));
    }

    private String consensusDabConfigTxt() {
        return consensusDabConfigTxt(ignore -> true);
    }

    private String consensusDabConfigTxt(@NonNull final Predicate<HederaNode> filter) {
        final Set<String> configTxts = nodes.stream()
                .filter(filter)
                .map(node -> rethrowIO(() -> Files.readString(
                        node.getExternalPath(UPGRADE_ARTIFACTS_DIR).resolve(CONFIG_TXT))))
                .collect(toSet());
        if (configTxts.size() != 1) {
            throw new IllegalStateException("DAB generated inconsistent config.txt files in network");
        }
        return configTxts.iterator().next();
    }

    private void reinitializePorts() {
        final var effectiveSize = (int) (maxNodeId + 1);
        final var firstGrpcPort = nodes().getFirst().getGrpcPort();
        final var totalPortsUsed = effectiveSize * PORTS_PER_NODE;
        final var newFirstGrpcPort = firstGrpcPort + totalPortsUsed;
        initializeNextPortsForNetwork(effectiveSize, newFirstGrpcPort);
    }

    private ServiceEndpoint endpointFor(final int port) {
        return ServiceEndpoint.newBuilder()
                .setIpAddressV4(SUBPROCESS_ENDPOINT)
                .setPort(port)
                .build();
    }

    private static void initializeNextPortsForNetwork(final int size) {
        initializeNextPortsForNetwork(size, randomPortAfter(FIRST_CANDIDATE_PORT, size * PORTS_PER_NODE));
    }

    /**
     * Initializes the next ports for the network with the given size and first gRPC port.
     *
     * @param size the number of nodes in the network
     * @param firstGrpcPort the first gRPC port
     */
    public static void initializeNextPortsForNetwork(final int size, final int firstGrpcPort) {
        // Suppose firstGrpcPort is 10000 with 4 nodes in the network, then the port assignments are,
        //   - grpcPort = 10000, 10002, 10004, 10006
        //   - nodeOperatorPort = 10008, 10009, 10010, 10011
        //   - gossipPort = 10012, 10014, 10016, 10018
        //   - gossipTlsPort = 10013, 10015, 10017, 10019
        //   - prometheusPort = 10020, 10021, 10022, 10023
        nextGrpcPort = firstGrpcPort;
        nextNodeOperatorPort = nextGrpcPort + 2 * size;
        nextGossipPort = nextNodeOperatorPort + size;
        nextGossipTlsPort = nextGossipPort + 1;
        nextPrometheusPort = nextGossipPort + 2 * size;
        nextPortsInitialized = true;
    }

    private static int randomPortAfter(final int firstAvailable, final int numRequired) {
        return RANDOM.nextInt(firstAvailable, LAST_CANDIDATE_PORT + 1 - numRequired);
    }

    /**
     * Returns the given <i>config.txt</i> with the ports reassigned for the given node id.
     *
     * @param nodeId the node id
     * @param firstNewPort the new internal port
     * @param secondNewPort the new external port
     * @param configTxt the <i>config.txt</i> to update
     * @return the updated <i>config.txt</i>
     */
    private static String withReassignedPorts(
            @NonNull final String configTxt, final long nodeId, final int firstNewPort, final int secondNewPort) {
        return Arrays.stream(configTxt.split("\n"))
                .map(line -> {
                    if (line.contains("address, " + nodeId)) {
                        final var parts = line.split(", ");
                        parts[6] = "" + firstNewPort;
                        parts[8] = "" + secondNewPort;
                        return String.join(", ", parts);
                    } else {
                        return line;
                    }
                })
                .collect(Collectors.joining("\n"));
    }
}
