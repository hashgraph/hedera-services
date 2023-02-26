/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.time.Time;
import com.swirlds.platform.event.GossipEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple gossip simulation where events are distributed with a delay
 */
public class SimpleSimulatedGossip {
    private final int numNodes;
    private final Latency latency;
    private final Time time;
    private final SimulatedEventCreationNode[] nodes;
    private final List<Deque<Payload>> queues;

    /**
     * Creates an instance to simulate gossip
     *
     * @param numNodes
     * 		the number of nodes in the network
     * @param latency
     * 		the latencies between nodes
     * @param time
     * 		the current time
     */
    public SimpleSimulatedGossip(final int numNodes, final Latency latency, final Time time) {
        this.numNodes = numNodes;
        this.latency = latency;
        this.time = time;

        nodes = new SimulatedEventCreationNode[numNodes];
        queues = new ArrayList<>(numNodes);

        for (int i = 0; i < numNodes; i++) {
            queues.add(new LinkedList<>());
        }
    }

    /**
     * Set the instance of the node to send events to
     *
     * @param node
     * 		the node to send events to
     */
    public void setNode(final SimulatedEventCreationNode node) {
        nodes[node.getNodeId().getIdAsInt()] = node;
    }

    /**
     * Gossip an event to other nodes with the provided latency.
     * This method will not actually do any gossiping, it will only enqueue the events to send them later with
     * {@link #distribute()}
     *
     * @param event
     * 		the event to gossip
     */
    public void gossipEvent(final GossipEvent event) {
        for (int i = 0; i < numNodes; i++) {
            if (nodes[i].getNodeId().getId() != event.getHashedData().getCreatorId()) {
                final Duration delay = latency.getLatency(event.getHashedData().getCreatorId(), i);
                queues.get(i).add(new Payload(event, time.now().plus(delay)));
            }
        }
    }

    /**
     * Distribute any previously gossipped events to other nodes if they are ready for arrival
     */
    public void distribute() {
        for (int i = 0; i < queues.size(); i++) {
            final Deque<Payload> queue = queues.get(i);
            for (final Iterator<Payload> iterator = queue.iterator(); iterator.hasNext(); ) {
                final Payload payload = iterator.next();
                if (!time.now().isBefore(payload.arrivalTime())) {
                    nodes[i].addEvent(payload.event());
                    iterator.remove();
                }
            }
        }
    }

    private record Payload(GossipEvent event, Instant arrivalTime) {}
}
