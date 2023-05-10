/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.test.simulated.SimpleSimulatedGossip;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class Network<T extends SimulatedChatterEvent> {
    private final Map<NodeId, Node<T>> nodes = new HashMap<>();
    private final SimpleSimulatedGossip gossip;
    private NetworkConfig currentConfiguration;

    public Network(final SimpleSimulatedGossip gossip) {
        this.gossip = gossip;
    }

    public void addNode(final Node<T> node) {
        if (nodes.containsKey(node.id())) {
            throw new IllegalArgumentException("Node with id " + node.id() + " already exists");
        }
        nodes.put(node.id(), node);
        gossip.setNode(node.chatterInstance());
    }

    public Set<NodeId> nodeIds() {
        return nodes.keySet();
    }

    public SimpleSimulatedGossip gossip() {
        return gossip;
    }

    public void forEachChatterInstance(final Consumer<ChatterInstance<T>> consumer) {
        nodes.values().forEach(n -> consumer.accept(n.chatterInstance()));
    }

    public ChatterInstance<T> chatterInstance(final NodeId id) {
        return nodes.get(id).chatterInstance();
    }

    public void enableChatter() {
        nodes.values().forEach(n -> n.chatterInstance().enableChatter());
    }

    public void printNetworkState() {
        nodes.values().forEach(n -> n.chatterInstance().printResults());
        gossip.printQueues();
    }

    public void applyConfig(final NetworkConfig nextConfig) {
        for (final Map.Entry<NodeId, NodeConfig> entry :
                nextConfig.nodeConfigs().entrySet()) {
            final NodeId nodeId = entry.getKey();
            final NodeConfig nodeConfig = entry.getValue();

            chatterInstance(nodeId).applyNodeConfig(nodeConfig);

            // FUTURE WORK: apply other updates
        }
        currentConfiguration = nextConfig;
    }

    public NetworkConfig getConfiguration() {
        return currentConfiguration;
    }
}
