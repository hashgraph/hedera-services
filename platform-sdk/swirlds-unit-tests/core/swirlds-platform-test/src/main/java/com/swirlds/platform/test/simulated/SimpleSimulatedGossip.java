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

import com.swirlds.common.time.Time;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A simple gossip simulation where events are distributed with a delay
 */
public class SimpleSimulatedGossip {
    private final int numNodes;
    private final NetworkLatency latency;
    private final Time time;
    /** Map from node id to that node's message handler */
    private final Map<Integer, GossipMessageHandler> nodes;

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
    public SimpleSimulatedGossip(final int numNodes, final NetworkLatency latency, final Time time) {
        this.numNodes = numNodes;
        this.latency = latency;
        this.time = time;

        nodes = new HashMap<>(numNodes);
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
    public void setNode(final GossipMessageHandler node) {
        nodes.put(node.getNodeId().getIdAsInt(), node);
    }

    public void gossipPayloads(final List<GossipMessage> messages) {
        messages.forEach(this::gossipPayload);
    }

    /**
     * Gossip an event to other nodes with the provided latency.
     * This method will not actually do any gossiping, it will only enqueue the events to send them later with
     * {@link #distribute()}
     *
     * @param message
     * 		the message to gossip
     */
    public void gossipPayload(final GossipMessage message) {
        if (message.recipientId() == null) {
            sendToAllPeers(message);
        } else {
            sendToPeer(message);
        }
    }

    private void sendToPeer(final GossipMessage message) {
        final int recipient = (int) message.recipientId().longValue();
        final Duration delay = latency.getLatency(message.senderId(), recipient);
        queues.get(recipient).add(new Payload(message, time.now().plus(delay)));
    }

    private void sendToAllPeers(final GossipMessage message) {
        for (int i = 0; i < numNodes; i++) {
            if (nodes.get(i).getNodeId().getId() != message.senderId()) {
                final Duration delay = latency.getLatency(message.senderId(), i);
                queues.get(i).add(new Payload(message, time.now().plus(delay)));
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
                    nodes.get(i).handleMessage(payload.gossipMessage().message(), payload.gossipMessage.senderId());
                    iterator.remove();
                }
            }
        }
    }

    public void printQueues() {
        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < queues.size(); i++) {
            final Deque<Payload> queue = queues.get(i);
            sb.append(String.format("Gossip Queue for %s (%s messages)%n", i, queue.size()));
            for (final Payload payload : queue) {
                sb.append("\t").append(payload).append("\n");
            }
            sb.append("\n");
        }

        System.out.println(sb);
    }

    public record Payload(GossipMessage gossipMessage, Instant arrivalTime) {

        @Override
        public String toString() {
            return gossipMessage.toString();
        }
    }
}
