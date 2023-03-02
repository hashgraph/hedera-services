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

package com.swirlds.platform.test.chatter.simulator;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

/**
 * Simulates a network.
 */
public class SimulatedNetwork {

    /**
     * If true then print extra stuff to the console.
     */
    private final boolean debugEnabled;

    /**
     * The total number of nodes.
     */
    private final int nodeCount;

    /**
     * Messages that need to be sent but have not yet been put "on the wire". These
     * messages will be sent in a later time step.
     */
    private final Map<SourceDestinationPair, Deque<SimulatedMessage>> messagesToBeSent;

    /**
     * Messages that are somewhere on the network that will eventually be delivered.
     */
    private final Map<SourceDestinationPair, Deque<SimulatedMessage>> messagesInTransit;

    /**
     * Messages that have been transmitted but are awaiting requisite bandwidth on the receiver.
     */
    private final Map<SourceDestinationPair, Deque<SimulatedMessage>> transmittedMessages;

    /**
     * Messages that have been fully received and can be utilized at any time by the receiver.
     */
    private final Map<Long /* receiver ID */, Deque<SimulatedMessage>> messagesReceived;

    /**
     * The duration of a single time step.
     */
    private final Duration timeStep;

    /**
     * Current simulation time.
     */
    private Instant now = Instant.ofEpochSecond(0);

    /**
     * The outgoing bandwidth of each node in bytes/timestep.
     */
    private final Map<Long, Long> outgoingBandwidth = new HashMap<>();

    /**
     * The incoming bandwidth of each node in bytes/timestep.
     */
    private final Map<Long, Long> incomingBandwidth = new HashMap<>();

    /**
     * The latency between each node pair.
     */
    private final Map<SourceDestinationPair, Duration> pairwiseLatency = new HashMap<>();

    /**
     * For each node, the outgoing latency that has been used for the current time step.
     */
    private final Map<Long, Long> outgoingBandwidthUsed = new HashMap<>();

    /**
     * Maximum number of messages in incoming buffer, more than this number will cause connection to throttle.
     */
    private final int incomingMessageQueueSize;

    /**
     * The probability that a message sent from a particular node to another will be dropped.
     */
    private final Map<SourceDestinationPair, Double> dropProbabilityMap = new HashMap<>();

    /**
     * Connection status between nodes. True = connected, false = disconnected.
     */
    private final Map<SourceDestinationPair, Boolean> connectionStatus = new HashMap<>();

    /**
     * All network statistics are encapsulated in this object.
     */
    private final SimulatedNetworkStatistics statistics;

    /**
     * Create a new simulated network.
     *
     * @param builder
     * 		contains configuration information for the network.
     */
    public SimulatedNetwork(final GossipSimulationBuilder builder) {
        debugEnabled = builder.isDebugEnabled();
        nodeCount = builder.getNodeCount();

        messagesToBeSent = setupNodePairMap(nodeCount);
        messagesInTransit = setupNodePairMap(nodeCount);
        transmittedMessages = setupNodePairMap(nodeCount);
        messagesReceived = setupNodeMap(nodeCount);

        this.timeStep = builder.getTimeStep();

        for (long source = 0; source < nodeCount; source++) {
            outgoingBandwidth.put(source, (long)
                    (builder.getOutgoingBandwidth(source) * timeStep.toNanos() * NANOSECONDS_TO_SECONDS));
            incomingBandwidth.put(source, (long)
                    (builder.getIncomingBandwidth(source) * timeStep.toNanos() * NANOSECONDS_TO_SECONDS));
            for (long destination = 0; destination < nodeCount; destination++) {
                if (source != destination) {
                    final SourceDestinationPair pair = new SourceDestinationPair(source, destination);
                    pairwiseLatency.put(pair, builder.getLatency(source, destination));
                    dropProbabilityMap.put(pair, builder.getDropProbability(source, destination));
                    connectionStatus.put(pair, builder.getConnectionStatus(source, destination));
                }
            }
        }

        statistics = new SimulatedNetworkStatistics(nodeCount, timeStep, incomingBandwidth, outgoingBandwidth);

        incomingMessageQueueSize = builder.getIncomingMessageQueueSize();

        resetBandwidthUsage();
    }

    /**
     * Build a map with a queue for each node.
     */
    private static Map<Long, Deque<SimulatedMessage>> setupNodeMap(final int nodeCount) {
        final Map<Long, Deque<SimulatedMessage>> map = new HashMap<>();

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            map.put(nodeId, new LinkedList<>());
        }

        return map;
    }

    /**
     * Build a map with a queue for each pair of nodes (excluding self pairs).
     */
    private static Map<SourceDestinationPair, Deque<SimulatedMessage>> setupNodePairMap(final int nodeCount) {
        final Map<SourceDestinationPair, Deque<SimulatedMessage>> map = new HashMap<>();
        for (long source = 0; source < nodeCount; source++) {
            for (long destination = 0; destination < nodeCount; destination++) {
                if (source == destination) {
                    continue;
                }
                map.put(new SourceDestinationPair(source, destination), new LinkedList<>());
            }
        }
        return map;
    }

    /**
     * Check if there is outgoing capacity that is available for a particular node in this time step.
     *
     * @param nodeId
     * 		the node that wants to send a message
     * @return true if there is still available bandwidth in this time step
     */
    public boolean isOutgoingCapacityAvailable(final long nodeId) {
        final long bandwidth = outgoingBandwidth.get(nodeId);
        final long utilizedBandwidth = outgoingBandwidthUsed.get(nodeId);
        final long remainingBandwidth = bandwidth - utilizedBandwidth;

        return remainingBandwidth > 0;
    }

    /**
     * Check if a destination is connected to a source.
     *
     * @param source
     * 		the sender ID
     * @param destination
     * 		the receiver ID
     * @return true if there is a connection that allows messages to flow from the source to the destination
     */
    public boolean isDestinationConnected(final long source, final long destination) {
        return connectionStatus.get(new SourceDestinationPair(source, destination));
    }

    /**
     * Check if a destination is currently available for messages to be sent to in the current time step.
     *
     * @param source
     * 		the source of the message
     * @param destination
     * 		the destination of the message
     * @return true if the source can send to the destination
     */
    public boolean isDestinationAvailable(final long source, final long destination) {
        final SourceDestinationPair pair = new SourceDestinationPair(source, destination);

        if (!connectionStatus.get(pair)) {
            return false;
        }

        final Queue<SimulatedMessage> queue = transmittedMessages.get(pair);
        final int queueSize = queue == null ? 0 : queue.size();
        return queueSize < incomingMessageQueueSize;
    }

    /**
     * Send a message.
     *
     * @param message
     * 		the message to be sent
     */
    public void send(final SimulatedMessage message) {
        if (debugEnabled) {
            System.out.println(now.toEpochMilli() + ": " + message.getSource() + " -> " + message.getDestination() + " "
                    + message.getPayload().getClass().getSimpleName());
        }

        statistics.captureSendStatistics(message);

        final long source = message.getSource();
        final long destination = message.getDestination();

        final long bandwidth = outgoingBandwidth.get(source);
        final long utilizedBandwidth = outgoingBandwidthUsed.get(source);
        final long remainingBandwidth = bandwidth - utilizedBandwidth;

        if (remainingBandwidth >= message.getBytesToSend()) {
            // The entire message can be sent this round
            message.setTransmissionTime(now);
            outgoingBandwidthUsed.put(source, utilizedBandwidth + message.getBytesToSend());
            message.setBytesToSend(0); // all bytes have been sent

            messagesInTransit
                    .get(new SourceDestinationPair(source, destination))
                    .add(message);
        } else {
            // only part of the message can be sent this round

            message.setBytesToSend(message.getBytesToSend() - remainingBandwidth);
            outgoingBandwidthUsed.put(source, bandwidth);

            // This message will be sent in a future time step
            messagesToBeSent.get(new SourceDestinationPair(source, destination)).add(message);
        }
    }

    /**
     * Check if there are any messages available for a given node.
     *
     * @param nodeId
     * 		the destination node
     * @return true if there are available messages
     */
    public boolean hasMessages(final long nodeId) {
        return !messagesReceived.get(nodeId).isEmpty();
    }

    /**
     * Get the next available message for a node
     *
     * @param nodeId
     * 		the destination node
     * @return the next message
     * @throws java.util.NoSuchElementException
     * 		if there is no available message
     */
    public SimulatedMessage nextMessage(final long nodeId) {
        return messagesReceived.get(nodeId).remove();
    }

    /**
     * Simulate one time step.
     *
     * @param random
     * 		a source of randomness
     * @param now
     * 		the current time, system should simulate up until "now"
     */
    public void simulateOneStep(final Random random, final Instant now) {
        this.now = now;
        sendEnqueuedMessages();
        simulateTraversal(random);
        simulateDelivery();
        resetBandwidthUsage();

        statistics.simulateOneStep();
    }

    /**
     * Reset bandwidth usage in preparation for the next step.
     */
    private void resetBandwidthUsage() {
        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            outgoingBandwidthUsed.put(nodeId, 0L);
        }
    }

    /**
     * Simulate the traversal of the message over the network.
     *
     * @param random
     * 		a source of randomness
     */
    private void simulateTraversal(final Random random) {
        for (long source = 0; source < nodeCount; source++) {
            for (long destination = 0; destination < nodeCount; destination++) {
                if (source == destination) {
                    continue;
                }

                final SourceDestinationPair pair = new SourceDestinationPair(source, destination);

                final Queue<SimulatedMessage> messageQueue = messagesInTransit.get(pair);
                final Duration latency = pairwiseLatency.get(pair);

                while (!messageQueue.isEmpty()) {

                    final SimulatedMessage message = messageQueue.peek();

                    final Duration elapsedTime = Duration.between(message.getTransmissionTime(), now);

                    if (elapsedTime.compareTo(latency) >= 0) {
                        // message has traversed the wire
                        messageQueue.remove();

                        if (random.nextDouble() < dropProbabilityMap.get(pair)) {
                            // whoopsie, hope that message wasn't important
                            continue;
                        }

                        transmittedMessages.get(pair).add(message);
                    } else {
                        // all remaining messages will be delivered in a later round
                        break;
                    }
                }
            }
        }
    }

    /**
     * Simulate the receiver of a message reading it out of a socket.
     */
    private void simulateDelivery() {
        for (long source = 0; source < nodeCount; source++) {
            for (long destination = 0; destination < nodeCount; destination++) {
                if (source == destination) {
                    continue;
                }

                final SourceDestinationPair pair = new SourceDestinationPair(source, destination);

                final Queue<SimulatedMessage> messageQueue = transmittedMessages.get(pair);

                final long incomingTimestepBandwidth = incomingBandwidth.get(destination);
                long utilizedBandwidth = 0;

                while (!messageQueue.isEmpty()) {

                    final SimulatedMessage message = messageQueue.peek();

                    final long remainingBandwidth = incomingTimestepBandwidth - utilizedBandwidth;

                    if (remainingBandwidth >= message.getBytesToReceive()) {
                        // message will be received in this time step
                        statistics.captureReceiveStatistics(message);
                        utilizedBandwidth += message.getBytesToSend();
                        messagesReceived.get(destination).add(message);
                        messageQueue.remove();
                    } else {
                        // message will be received in a later time step
                        message.setBytesToReceive(message.getBytesToReceive() - remainingBandwidth);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Called when one time step has finished and the next is starting. Attempt to send any messages in the queue
     * that were not able to be sent in the previous step.
     */
    private void sendEnqueuedMessages() {
        for (long source = 0; source < nodeCount; source++) {
            for (long destination = 0; destination < nodeCount; destination++) {

                final SourceDestinationPair pair = new SourceDestinationPair(source, destination);
                final Queue<SimulatedMessage> messageQueue = messagesToBeSent.get(pair);
                if (messageQueue == null) {
                    continue;
                }

                final long bandwidth = outgoingBandwidth.get(source);

                while (!messageQueue.isEmpty()) {

                    final SimulatedMessage message = messageQueue.peek();

                    final long utilizedBandwidth = outgoingBandwidthUsed.get(source);
                    final long remainingBandwidth = bandwidth - utilizedBandwidth;

                    if (remainingBandwidth >= message.getBytesToSend()) {
                        // The entire message can be sent this round
                        message.setTransmissionTime(now);
                        outgoingBandwidthUsed.put(source, utilizedBandwidth + message.getBytesToSend());
                        message.setBytesToSend(0); // all bytes have been sent

                        messagesInTransit
                                .get(new SourceDestinationPair(source, message.getDestination()))
                                .add(message);

                        messageQueue.remove();

                    } else {
                        // only part of the message can be sent this round
                        message.setBytesToSend(message.getBytesToSend() - remainingBandwidth);
                        outgoingBandwidthUsed.put(source, bandwidth);

                        break;
                    }
                }
            }
        }
    }

    /**
     * Wraps things up, prints statistics to the console.
     */
    public void close() {
        statistics.printStatistics();
    }
}
