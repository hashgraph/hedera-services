/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.network.framework;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.test.simulated.SimpleSimulatedGossip;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A network of simulated nodes that gossip using chatter.
 *
 * @param <T> the type of chatter event gossiped by this network
 */
public class Network<T extends SimulatedChatterEvent> {
    /** A map of nodes in the network keys by node id */
    private final Map<NodeId, Node<T>> nodes = new HashMap<>();
    /** The gossip simulator to use for this network */
    private final SimpleSimulatedGossip gossip;
    /** The current configuration of the network */
    private NetworkConfig currentConfiguration;

    /**
     * Create a new network with the supplied gossip model.
     *
     * @param gossip the gossip model
     */
    public Network(final SimpleSimulatedGossip gossip) {
        this.gossip = Objects.requireNonNull(gossip);
    }

    /**
     * Adds a node to the network.
     *
     * @param node the node to add
     */
    public void addNode(final Node<T> node) {
        Objects.requireNonNull(node);
        if (nodes.containsKey(node.id())) {
            throw new IllegalArgumentException("Node with id " + node.id() + " already exists");
        }
        nodes.put(node.id(), node);
        gossip.setNode(node.chatterInstance());
    }

    /**
     * Returns a set of node ids in the network
     *
     * @return the node ids
     */
    public Set<NodeId> nodeIds() {
        return nodes.keySet();
    }

    /**
     * Returns the gossip model used by this network.
     *
     * @return the gossip model
     */
    public SimpleSimulatedGossip gossip() {
        return gossip;
    }

    /**
     * Passes the {@link ChatterInstance} from each node in this network to the provided consumer.
     *
     * @param consumer the consumer to invoke
     */
    public void forEachChatterInstance(final Consumer<ChatterInstance<T>> consumer) {
        nodes.values().forEach(n -> consumer.accept(n.chatterInstance()));
    }

    /**
     * Returns the chatter instance for the provided node
     *
     * @param id the id of the node whose chatter instance should be returned
     * @return the chatter instance
     */
    public ChatterInstance<T> chatterInstance(final NodeId id) {
        return nodes.get(id).chatterInstance();
    }

    /**
     * Enables chatter on all nodes.
     */
    public void enableChatter() {
        nodes.values().forEach(n -> n.chatterInstance().enableChatter());
    }

    /**
     * Prints data about the state of the network to std out
     */
    public void printNetworkState() {
        nodes.values().forEach(n -> n.chatterInstance().printResults());
        gossip.printQueues();
    }

    /**
     * Applies a new network configuration to the network.
     *
     * @param nextConfig the configuration to apply
     */
    public void applyNetworkConfig(final NetworkConfig nextConfig) {
        Objects.requireNonNull(nextConfig);
        for (final Map.Entry<NodeId, NodeConfig> entry :
                nextConfig.nodeConfigs().entrySet()) {
            final NodeId nodeId = entry.getKey();
            final NodeConfig nodeConfig = entry.getValue();
            chatterInstance(nodeId).applyNodeConfig(nodeConfig);
        }
        gossip.applyConfig(nextConfig);
        currentConfiguration = nextConfig;
    }

    /**
     * Returns the current network configuration.
     *
     * @return the current network configuration.
     */
    public NetworkConfig getConfiguration() {
        return currentConfiguration;
    }
}
