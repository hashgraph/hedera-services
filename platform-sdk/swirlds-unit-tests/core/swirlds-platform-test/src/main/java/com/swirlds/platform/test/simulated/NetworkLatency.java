/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.simulated;

import com.swirlds.common.system.NodeId;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates random latencies between nodes in the provided address book
 */
public class NetworkLatency {
    private final Map<Long, Latency> delays = new HashMap<>();

    private NetworkLatency(final HashMap<Long, Latency> delays) {
        this.delays.putAll(delays);
    }

    /**
     * Creates a {@link NetworkLatency} model where the latencies between all peers are the same.
     *
     * @param nodeIds the nodeIds of all nodes in the network
     * @param latency the latency between each pair of peers
     * @return the {@link NetworkLatency} object
     */
    public static NetworkLatency uniformLatency(final Set<NodeId> nodeIds, final Duration latency) {
        HashMap<Long, Latency> delays = new HashMap<>();
        for (final NodeId nodeId : nodeIds) {
            delays.put(nodeId.id(), new Latency(latency));
        }
        return new NetworkLatency(delays);
    }

    /**
     * Creates a hub-and-spoke {@link NetworkLatency} model with randomized latencies. Each node is assigned a randomly
     * generated delay. The delay between any two peers for a one-way trip is the sum nodes' delays. Some spokes
     * may be long, others may be short, but all messages sent to a from a particular peer include that peer's set
     * delay. This differs from real life in that it does not allow two nodes to have low latency with each other while
     * also having high latency with other nodes.
     *
     * @param nodeIds  the nodeIds of all nodes in the network
     * @param maxDelay the maximum one-way delay between 2 peers
     * @param random   source of randomness
     * @return the {@link NetworkLatency} object
     */
    public static NetworkLatency randomLatency(
            final Set<NodeId> nodeIds, final Duration maxDelay, final Random random) {
        HashMap<Long, Latency> delays = new HashMap<>();
        for (final NodeId nodeId : nodeIds) {
            final Latency latency = new Latency(
                    Duration.ofMillis(random.nextInt((int) maxDelay.dividedBy(2).toMillis())));
            delays.put(nodeId.id(), latency);
        }
        return new NetworkLatency(delays);
    }

    /**
     * Overrides the latency for a particular node
     *
     * @param nodeId  the node whose latency we are setting
     * @param latency the latency to set
     */
    public void setLatency(final long nodeId, final Latency latency) {
        delays.put(nodeId, latency);
    }

    /**
     * @param from the ID of the first node
     * @param to   the ID of the second node
     * @return the delay between the two nodes
     */
    public Duration getLatency(final long from, final long to) {
        return delays.get(from).delay().plus(delays.get(to).delay());
    }
}
