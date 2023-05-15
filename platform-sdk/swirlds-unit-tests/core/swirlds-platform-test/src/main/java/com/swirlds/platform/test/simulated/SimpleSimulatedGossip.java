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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.time.Time;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A simple gossip simulation where events are distributed with a delay
 */
public class SimpleSimulatedGossip {
    private final int numNodes;
    private final Time time;
    /** Map from node id to that node's message handler */
    private final Map<Integer, GossipMessageHandler> nodes;
    private final List<Deque<Payload>> inTransit;
    private final List<Deque<GossipMessage>> delivered;
    private final List<Deque<GossipMessage>> sentBy;
    private final NetworkLatency latency;

    /**
     * Creates an instance to simulate gossip
     *
     * @param numNodes the number of nodes in the network
     * @param latency  the latencies between nodes
     * @param time     the current time
     */
    public SimpleSimulatedGossip(final int numNodes, final NetworkLatency latency, final Time time) {
        this.numNodes = numNodes;
        this.latency = latency;
        this.time = time;

        nodes = new HashMap<>(numNodes);
        inTransit = new ArrayList<>(numNodes);
        delivered = new ArrayList<>(numNodes);
        sentBy = new ArrayList<>(numNodes);

        for (int i = 0; i < numNodes; i++) {
            inTransit.add(new LinkedList<>());
            delivered.add(new LinkedList<>());
            sentBy.add(new LinkedList<>());
        }
    }

    /**
     * Set the instance of the node to send events to
     *
     * @param node the node to send events to
     */
    public void setNode(final GossipMessageHandler node) {
        nodes.put(node.getNodeId().getIdAsInt(), node);
    }

    /**
     * Gossips messages to the message recipients. Messages will be delivered once the applicable latency has elapsed.
     *
     * @param messages the messages to gossip
     */
    public void gossipPayloads(final List<GossipMessage> messages) {
        messages.forEach(this::gossipPayload);
    }

    /**
     * Get all the messages (not just events) delivered to a node via gossip
     *
     * @param nodeId the id of the node
     * @return all messages delivered to the node
     */
    public Deque<GossipMessage> getDeliveredTo(final NodeId nodeId) {
        return delivered.get(nodeId.getIdAsInt());
    }

    /**
     * Returns all the messages of a certain type delivered to a node.
     *
     * @param nodeId the node id of the receiver
     * @param clazz  the class of messages to return
     * @param <T>    the message type
     * @return the list of sent messages
     */
    @SuppressWarnings("unchecked")
    public <T extends SelfSerializable> Deque<T> getDeliveredTo(final NodeId nodeId, final Class<T> clazz) {
        return getDeliveredTo(nodeId).stream()
                .filter(msg -> clazz.isAssignableFrom(msg.message().getClass()))
                .map(msg -> (T) msg.message())
                .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the messages (not just events) sent by a node via gossip
     *
     * @param nodeId the id of the node
     * @return all messages sent by the node
     */
    public Deque<GossipMessage> getSentBy(final NodeId nodeId) {
        return sentBy.get(nodeId.getIdAsInt());
    }

    /**
     * Returns all the messages of a certain type sent by a node.
     *
     * @param nodeId the node id of the sender
     * @param clazz  the class of messages to return
     * @param <T>    the message type
     * @return the list of sent messages
     */
    @SuppressWarnings("unchecked")
    public <T extends SelfSerializable> Deque<T> getSentBy(final NodeId nodeId, final Class<T> clazz) {
        return getSentBy(nodeId).stream()
                .filter(msg -> clazz.isAssignableFrom(msg.message().getClass()))
                .map(msg -> (T) msg.message())
                .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Gossip an event to other nodes with the provided latency. This method will not actually do any gossiping, it will
     * only enqueue the events to send them later with {@link #distribute()}
     *
     * @param message the message to gossip
     */
    public void gossipPayload(final GossipMessage message) {
        sentBy.get((int) message.senderId()).add(message);
        if (message.recipientId() == null) {
            sendToAllPeers(message);
        } else {
            sendToPeer(message);
        }
    }

    private void sendToPeer(final GossipMessage message) {
        final int recipient = (int) message.recipientId().longValue();
        final Duration delay = latency.getLatency(message.senderId(), recipient);
        inTransit.get(recipient).add(new Payload(message, time.now().plus(delay)));
    }

    private void sendToAllPeers(final GossipMessage message) {
        for (int i = 0; i < numNodes; i++) {
            if (nodes.get(i).getNodeId().id() != message.senderId()) {
                final Duration delay = latency.getLatency(message.senderId(), i);
                inTransit.get(i).add(new Payload(message, time.now().plus(delay)));
            }
        }
    }

    /**
     * Distribute any previously gossipped events to other nodes if they are ready for arrival
     */
    public void distribute() {
        for (int i = 0; i < inTransit.size(); i++) {
            final Deque<Payload> queue = inTransit.get(i);
            for (final Iterator<Payload> iterator = queue.iterator(); iterator.hasNext(); ) {
                final Payload payload = iterator.next();
                if (!time.now().isBefore(payload.arrivalTime())) {
                    nodes.get(i)
                            .handleMessageFromWire(payload.gossipMessage().message(), payload.gossipMessage.senderId());
                    delivered.get(i).add(payload.gossipMessage);
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Applies a network configuration to this gossip simulation.
     *
     * @param networkConfig the configuration to apply
     */
    public void applyConfig(final NetworkConfig networkConfig) {
        for (final Map.Entry<NodeId, NodeConfig> entry :
                networkConfig.nodeConfigs().entrySet()) {
            final NodeId nodeId = entry.getKey();
            final NodeConfig nodeConfig = entry.getValue();
            if (!nodeConfig.customLatency().isZero()) {
                latency.setLatency(nodeId.id(), nodeConfig.customLatency());
            }
        }
    }

    /**
     * Prints all the queues of messages to std out.
     */
    public void printQueues() {
        final StringBuilder sb = new StringBuilder();

        printInTransit(sb);
        printDelivered(sb);
        //        printSentBy(sb);

        System.out.println(sb);
    }

    private void printSentBy(final StringBuilder sb) {
        for (int i = 0; i < sentBy.size(); i++) {
            final Deque<GossipMessage> queue = sentBy.get(i);
            sb.append(String.format("Messages sent by %s (%s messages)%n", i, queue.size()));
            for (final GossipMessage msg : queue) {
                sb.append("\t").append(msg).append("\n");
            }
            sb.append("\n");
        }
    }

    private void printDelivered(final StringBuilder sb) {
        for (int i = 0; i < delivered.size(); i++) {
            final Deque<GossipMessage> queue = delivered.get(i);
            sb.append(String.format("Messages delivered to %s (%s messages)%n", i, queue.size()));
            for (final GossipMessage msg : queue) {
                sb.append("\t").append(msg).append("\n");
            }
            sb.append("\n");
        }
    }

    private void printInTransit(final StringBuilder sb) {
        for (int i = 0; i < inTransit.size(); i++) {
            final Deque<Payload> queue = inTransit.get(i);
            sb.append(String.format("Messages in transit to %s (%s messages)%n", i, queue.size()));
            for (final Payload payload : queue) {
                sb.append("\t").append(payload).append("\n");
            }
            sb.append("\n");
        }
    }

    /**
     * A payload to gossip at a set time.
     *
     * @param gossipMessage the message to gossip
     * @param arrivalTime   the time to send the message
     */
    public record Payload(GossipMessage gossipMessage, Instant arrivalTime) {

        @Override
        public String toString() {
            return gossipMessage.toString();
        }
    }
}
