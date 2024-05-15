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

package com.hedera.services.bdd.junit.hedera;

import static com.hedera.services.bdd.junit.hedera.live.WorkingDirUtils.workingDirFor;
import static com.hedera.services.bdd.suites.TargetNetworkType.HAPI_TEST_NETWORK;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.services.bdd.junit.hedera.live.GrpcPinger;
import com.hedera.services.bdd.junit.hedera.live.PrometheusClient;
import com.hedera.services.bdd.junit.hedera.live.SubProcessNode;
import com.hedera.services.bdd.suites.TargetNetworkType;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * A network of Hedera nodes. For now, assumed to be accessed via
 * a live gRPC connection. In the future, we will abstract the
 * submission and querying operations to allow for an embedded
 * "network".
 */
public class HederaNetwork {
    private static final int FIRST_GRPC_PORT = 50211;
    private static final int FIRST_GOSSIP_PORT = 60000;
    private static final int FIRST_GOSSIP_TLS_PORT = 60001;
    private static final int FIRST_PROMETHEUS_PORT = 10000;
    private static final long FIRST_NODE_ACCOUNT_NUM = 3;
    private static final String[] NODE_NAMES = new String[] {"Alice", "Bob", "Carol", "Dave"};

    public static final AtomicReference<HederaNetwork> SHARED_NETWORK = new AtomicReference<>();

    private final String configTxt;
    private final String networkName;
    private final List<HederaNode> nodes;

    @Nullable
    private CompletableFuture<Void> ready;

    public HederaNetwork(@NonNull final String networkName, @NonNull final List<HederaNode> nodes) {
        this.nodes = requireNonNull(nodes);
        this.networkName = requireNonNull(networkName);
        this.configTxt = configTxtFor(networkName, nodes);
    }

    /**
     * Creates a network of live (sub-process) nodes with the given name and size.
     *
     * @param name the name of the network
     * @param size the number of nodes in the network
     * @return the network
     */
    public static HederaNetwork newLiveNetwork(@NonNull final String name, final int size) {
        final var grpcPinger = new GrpcPinger();
        final var prometheusClient = new PrometheusClient();
        return new HederaNetwork(
                name,
                IntStream.range(0, size)
                        .<HederaNode>mapToObj(
                                nodeId -> new SubProcessNode(metadataFor(nodeId), grpcPinger, prometheusClient))
                        .toList());
    }

    /**
     * Returns the network type; for now this is always
     * {@link TargetNetworkType#HAPI_TEST_NETWORK}.
     *
     * @return the network type
     */
    public TargetNetworkType type() {
        return HAPI_TEST_NETWORK;
    }

    /**
     * Returns the nodes of the network.
     *
     * @return the nodes of the network
     */
    public List<HederaNode> nodes() {
        return nodes;
    }

    /**
     * Returns the name of the network.
     *
     * @return the name of the network
     */
    public String name() {
        return networkName;
    }

    /**
     * Starts all nodes in the network and waits for them to reach the
     * {@link PlatformStatus#ACTIVE} status, or times out.
     *
     * @param timeout the maximum time to wait for all nodes to start
     */
    public void startWithin(@NonNull final Duration timeout) {
        final var latch = new CountDownLatch(1);
        CompletableFuture.allOf(nodes.stream()
                        .map(node -> {
                            node.start(configTxt);
                            return node.waitForStatus(ACTIVE);
                        })
                        .toArray(CompletableFuture[]::new))
                .orTimeout(timeout.toMillis(), MILLISECONDS)
                .thenRun(latch::countDown);
        ready = CompletableFuture.runAsync(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * Forcibly stops all nodes in the network.
     */
    public void terminate() {
        nodes.forEach(HederaNode::terminate);
    }

    /**
     * Waits for all nodes in the network to be ready.
     */
    public void waitForReady() {
        requireNonNull(ready).join();
    }

    private static String configTxtFor(@NonNull final String networkName, @NonNull final List<HederaNode> nodes) {
        final var sb = new StringBuilder();
        sb.append("swirld, ")
                .append(networkName)
                .append("\n")
                .append("\n# This next line is, hopefully, ignored.\n")
                .append("app, HederaNode.jar\n\n#The following nodes make up this network\n");
        for (final var node : nodes) {
            sb.append("address, ")
                    .append(node.getNodeId())
                    .append(", ")
                    .append(node.getName().charAt(0))
                    .append(", ")
                    .append(node.getName())
                    .append(", 1, 127.0.0.1, ")
                    .append(FIRST_GOSSIP_PORT + (node.getNodeId() * 2))
                    .append(", 127.0.0.1, ")
                    .append(FIRST_GOSSIP_TLS_PORT + (node.getNodeId() * 2))
                    .append(", ")
                    .append("0.0.")
                    .append(node.getAccountId().accountNumOrThrow())
                    .append("\n");
        }
        sb.append("\nnextNodeId, ").append(nodes.size()).append("\n");
        return sb.toString();
    }

    private static NodeMetadata metadataFor(final int nodeId) {
        return new NodeMetadata(
                nodeId,
                NODE_NAMES[nodeId],
                AccountID.newBuilder()
                        .accountNum(FIRST_NODE_ACCOUNT_NUM + nodeId)
                        .build(),
                FIRST_GRPC_PORT + nodeId * 2,
                FIRST_PROMETHEUS_PORT + nodeId,
                workingDirFor(nodeId));
    }
}
